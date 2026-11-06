"""Regression cases for forbidden dependencies and cycle detection."""
import importlib.util
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

spec = importlib.util.spec_from_file_location('architecture', Path(__file__).parents[3] / 'scripts/check-architecture.py')
architecture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(architecture)


class ArchitectureTest(unittest.TestCase):
    def test_unified_chat_may_consume_only_exported_task_and_skill_contracts(self):
        self.assertIn('tasks', architecture.ALLOWED['chat'])
        self.assertIn('skills', architecture.ALLOWED['chat'])
        self.assertTrue({'AgentTaskService', 'AgentTaskRequest', 'AgentTaskResponse'} <= architecture.EXPORTS['tasks'])
        self.assertTrue(architecture.exported('prj.anhzxje.aiagent.skills.SkillSelection'))

    def test_reports_transitive_cycle_and_leaves_acyclic_modules_out(self):
        self.assertEqual([['a', 'b', 'c']], architecture.strongly_connected(
            {'a', 'b', 'c', 'leaf'}, [('a', 'b'), ('b', 'c'), ('c', 'a'), ('c', 'leaf')]))

    def test_forbidden_import_is_rejected_but_comment_and_literal_are_not_dependencies(self):
        with TemporaryDirectory() as folder:
            previous = architecture.JAVA
            architecture.JAVA = Path(folder)
            try:
                target = Path(folder, 'review/Review.java')
                target.parent.mkdir()
                target.write_text('package prj.anhzxje.aiagent.review; public class Review {}')
                source = Path(folder, 'shared/Example.java')
                source.parent.mkdir()
                source.write_text('package prj.anhzxje.aiagent.shared;\n'
                                  '// prj.anhzxje.aiagent.review.Review\n'
                                  'class Example { String text = "prj.anhzxje.aiagent.review.Review"; }')
                self.assertEqual([], architecture.dependency_inventory()['edges'])
                source.write_text('package prj.anhzxje.aiagent.shared;\n'
                                  'import prj.anhzxje.aiagent.review.*;\nclass Example { Review review; }')
                with self.assertRaisesRegex(AssertionError, 'forbidden shared -> review'):
                    architecture.dependency_inventory()
            finally:
                architecture.JAVA = previous

    def test_inline_type_reference_cannot_bypass_feature_boundary(self):
        with TemporaryDirectory() as folder:
            previous = architecture.JAVA
            architecture.JAVA = Path(folder)
            try:
                for package, source in {
                    'review/Review.java': 'package prj.anhzxje.aiagent.review; class Review {}',
                    'shared/Example.java': 'package prj.anhzxje.aiagent.shared; class Example { prj.anhzxje.aiagent.review.Review field; }',
                }.items():
                    path = Path(folder, package)
                    path.parent.mkdir(exist_ok=True)
                    path.write_text(source)
                with self.assertRaisesRegex(AssertionError, 'forbidden shared -> review'):
                    architecture.dependency_inventory()
            finally:
                architecture.JAVA = previous


if __name__ == '__main__':
    unittest.main()
