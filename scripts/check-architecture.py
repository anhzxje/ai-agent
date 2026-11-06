#!/usr/bin/env python3
"""Enforce feature ownership and an acyclic source dependency graph (stdlib only)."""
import argparse
import json
import re
import subprocess
from collections import Counter
from pathlib import Path
from xml.etree import ElementTree

ROOT = Path(__file__).resolve().parent.parent
JAVA = ROOT / 'src/main/java/prj/anhzxje/aiagent'
STATIC = ROOT / 'src/main/resources/static'
PREFIX = 'prj.anhzxje.aiagent.'
# Public module dependencies. Persistence is shared because JPA aggregates reference
# each other; repositories/entities may never depend back on business services.
ALLOWED = {
    'application': set(),
    'shared': set(),
    'infrastructure.persistence': {'shared'},
    'infrastructure.configuration': set(),
    'infrastructure.filesystem': set(),
    'infrastructure.audit': {'infrastructure.persistence'},
    'infrastructure.ai': {'infrastructure.persistence', 'infrastructure.audit', 'infrastructure.configuration', 'shared'},
    'infrastructure.tools': {'infrastructure.ai', 'infrastructure.persistence', 'infrastructure.audit', 'infrastructure.configuration', 'infrastructure.filesystem', 'shared'},
    'infrastructure.moderation': {'infrastructure.audit'},
    'security': {'infrastructure.persistence', 'shared'},
    'auth': {'security', 'infrastructure.persistence', 'infrastructure.audit', 'shared'},
    'users': {'infrastructure.ai', 'security', 'infrastructure.persistence', 'infrastructure.audit', 'shared'},
    'skills': {'infrastructure.ai'},
    'projects': {'security', 'infrastructure.persistence', 'infrastructure.configuration', 'infrastructure.filesystem', 'shared'},
    'review': {'skills', 'infrastructure.ai', 'infrastructure.tools', 'shared'},
    'chat': {'infrastructure.moderation', 'security', 'infrastructure.persistence', 'infrastructure.ai', 'infrastructure.tools', 'infrastructure.audit', 'tasks', 'skills', 'shared'},
    'tasks': {'infrastructure.moderation', 'infrastructure.audit', 'security', 'infrastructure.persistence', 'infrastructure.configuration', 'infrastructure.filesystem', 'review', 'skills', 'shared'},
    'admin': {'users', 'chat', 'projects', 'tasks', 'security', 'infrastructure.persistence', 'infrastructure.ai', 'infrastructure.tools', 'infrastructure.audit', 'shared'},
    'evaluation': {'review', 'skills', 'infrastructure.ai', 'infrastructure.tools'},
    'bootstrap': {'security', 'infrastructure.persistence', 'infrastructure.ai', 'infrastructure.configuration', 'infrastructure.audit', 'shared'},
}


# Cross-feature entry points. Classes not listed stay internal to their feature.
EXPORTS = {
    'auth': set(), 'admin': set(),
    'users': {'UserService', 'UserSummaryResponse'},
    'chat': {'ChatResponses', 'ChatConversationResponse', 'ChatMessageResponse'},
    'projects': {'ProjectResponses', 'ProjectResponse'},
    'tasks': {'TaskResponses', 'AgentTaskResponse', 'AgentTaskRequest', 'AgentTaskService'},
    'review': {'CodeReviewAgent', 'ReviewReportValidator', 'ReviewBudgetAdvisor'},
}


def exported(qualified):
    relative = qualified.removeprefix(PREFIX)
    owner = module(relative)
    return owner not in EXPORTS or relative.split('.')[1].split('$')[0] in EXPORTS[owner]


def module(package):
    parts = package.split('.')
    if len(parts) == 1:
        return 'application'
    return '.'.join(parts[:2]) if parts[0] == 'infrastructure' else parts[0]


def strongly_connected(nodes, edges):
    adjacency = {n: set() for n in nodes}
    for a, b in edges:
        adjacency[a].add(b)
    index, low, stack, active, groups = {}, {}, [], set(), []

    def visit(node):
        index[node] = low[node] = len(index)
        stack.append(node)
        active.add(node)
        for target in sorted(adjacency[node]):
            if target not in index:
                visit(target)
                low[node] = min(low[node], low[target])
            elif target in active:
                low[node] = min(low[node], index[target])
        if low[node] == index[node]:
            group = []
            while True:
                target = stack.pop()
                active.remove(target)
                group.append(target)
                if target == node:
                    break
            if len(group) > 1:
                groups.append(sorted(group))

    for node in sorted(nodes):
        if node not in index:
            visit(node)
    return sorted(groups)


def dependency_inventory(enforce=True):
    sources = {}
    for file in sorted(JAVA.rglob('*.java')):
        source = file.read_text()
        package = re.search(r'^[\ufeff\s]*package ([\w.]+);', source, re.M)[1]
        sources[package + '.' + file.stem] = (file, source)
    edges = Counter()
    nodes = Counter()
    violations = []
    for qualified, (file, source) in sources.items():
        expected = Path(*qualified.removeprefix(PREFIX).split('.')).with_suffix('.java')
        assert file.relative_to(JAVA) == expected, f'Package/path mismatch: {file}'
        owner = module(qualified.removeprefix(PREFIX))
        nodes[owner] += 1
        # Ignore comments and literals; count imports and inline qualified references.
        code = re.sub(r'/\*.*?\*/|//[^\n]*|"(?:\\.|[^"\\])*"', '', source, flags=re.S)
        refs = set(re.findall(re.escape(PREFIX) + r'[\w.]+', code))
        for package in re.findall(r'import\s+' + re.escape(PREFIX) + r'([\w.]+)\.\*;', code):
            for qualified_type in sources:
                if qualified_type.rsplit('.', 1)[0] == PREFIX + package and re.search(r'\b' + qualified_type.split('.')[-1] + r'\b', code):
                    refs.add(qualified_type)
        dependencies = set()
        for ref in refs:
            name = ref
            candidates = [q for q in sources if name == q or name.startswith(q + '.')]
            if candidates:
                target = max(candidates, key=len)
                dependency = module(target.removeprefix(PREFIX))
                dependencies.add(dependency)
                if dependency != owner and not exported(target):
                    violations.append(f'{file.relative_to(JAVA)}: internal feature access {target}')
        for dependency in dependencies - {owner}:
            edges[owner, dependency] += 1
            if owner not in ALLOWED or dependency not in ALLOWED[owner]:
                violations.append(f'{file.relative_to(JAVA)}: forbidden {owner} -> {dependency}')
        if owner not in {'infrastructure.ai', 'evaluation'} and re.search(r'import\s+(?:com\.google\.genai|org\.springframework\.ai\.google\.genai)\.', code):
            violations.append(f'{file.relative_to(JAVA)}: provider SDK outside AI infrastructure')
        if file.name.endswith('Controller.java') and re.search(r'\b\w+Repository\b', code):
            violations.append(f'{file.relative_to(JAVA)}: HTTP controller accesses persistence directly')
        if owner not in ALLOWED:
            violations.append(f'{file.relative_to(JAVA)}: unowned module {owner}')
    cycles = strongly_connected(nodes, edges)
    graph = {
        'method': 'Resolved internal imports and qualified type references, ignoring comments/literals; source-file edge counts, not runtime coupling metrics.',
        'javaFiles': len(sources), 'nodes': sorted(nodes), 'filesByModule': dict(sorted(nodes.items())),
        'edges': [{'from': a, 'to': b, 'sourceFiles': count} for (a,b),count in sorted(edges.items())],
        'stronglyConnectedComponents': cycles,
        'featureExports': {k: sorted(v) for k,v in EXPORTS.items()},
        'allowedDependencies': {k: sorted(v) for k,v in ALLOWED.items()},
    }
    if enforce:
        assert not violations, '\n'.join(violations)
        assert not cycles, f'Production dependency cycles: {cycles}'
    return graph



def bytecode_inventory(directory):
    """Independently validate compiled dependencies, including nested classes."""
    result = subprocess.run(['jdeps', '-verbose:class', '-filter:none', str(directory)],
                            check=True, text=True, capture_output=True)
    edges = Counter()
    nodes = set()
    pattern = re.compile(r'^\s+(prj\.anhzxje\.aiagent\.[\w.$]+)\s+->\s+(prj\.anhzxje\.aiagent\.[\w.$]+)\s+', re.M)
    for source, target in pattern.findall(result.stdout):
        owner, dependency = module(source.removeprefix(PREFIX)), module(target.removeprefix(PREFIX))
        nodes.update((owner, dependency))
        if owner != dependency:
            assert exported(target), f'Bytecode accesses feature internal: {source} -> {target}'
            assert dependency in ALLOWED.get(owner, set()), f'Bytecode forbidden {source} -> {target}'
            edges[owner, dependency] += 1
    assert nodes, 'No internal compiled dependencies found; compile first'
    cycles = strongly_connected(nodes, edges)
    assert not cycles, f'Bytecode dependency cycles: {cycles}'
    return {'method': 'JDK jdeps -verbose:class -filter:none; internal compiled class references only. External dependencies are intentionally not resolved.',
            'nodes': sorted(nodes), 'edges': [{'from': a, 'to': b, 'classReferences': n} for (a,b),n in sorted(edges.items())],
            'stronglyConnectedComponents': cycles}


def check_frontend():
    html = (STATIC / 'index.html').read_text()
    for resource in re.findall(r'(?:src|href)="((?:js|css|assets)/[^"?#]+)"', html):
        assert (STATIC / resource).is_file(), f'Missing local asset: {resource}'
    for relative in ['js/modules/workspaceSession.js', 'js/core/taskPolling.js']:
        source = (STATIC / relative).read_text()
        assert not re.search(r'\b(document|AppState|apiClient)\b', source), f'{relative}: shell/DOM dependency'
        assert html.index(f'src="{relative}"') < html.index('src="js/app.js"')
    assert html.index('href="css/style.css"') < html.index('href="css/features/assistant.css"') < html.index('href="css/workspace.css"')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--write', action='store_true')
    parser.add_argument('--bytecode', type=Path, help='Also validate compiled classes using jdeps')
    parser.add_argument('--inventory', help='Write inventory without enforcing boundaries (baseline only)')
    args = parser.parse_args()
    ElementTree.parse(ROOT / 'pom.xml')
    graph = dependency_inventory(enforce=not args.inventory)
    if args.inventory:
        (ROOT / args.inventory).write_text(json.dumps(graph, indent=2) + '\n')
    else:
        check_frontend()
        if args.bytecode:
            compiled = bytecode_inventory(args.bytecode)
            if args.write:
                (ROOT / 'docs/architecture-bytecode.json').write_text(json.dumps(compiled, indent=2) + '\n')
            print(f"Bytecode: {len(compiled['nodes'])} modules; {len(compiled['edges'])} edges; no cycles")
        if args.write:
            (ROOT / 'docs/architecture-dependencies.json').write_text(json.dumps(graph, indent=2) + '\n')
    print(f"{graph['javaFiles']} Java files; {len(graph['nodes'])} modules; {len(graph['edges'])} edges; cycles: {graph['stronglyConnectedComponents']}")


if __name__ == '__main__':
    main()
