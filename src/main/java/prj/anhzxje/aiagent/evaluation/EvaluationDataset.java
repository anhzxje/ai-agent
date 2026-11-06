package prj.anhzxje.aiagent.evaluation;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static prj.anhzxje.aiagent.evaluation.EvaluationTypes.*;

public final class EvaluationDataset {
    private final Path root;
    public EvaluationDataset(Path root) {this.root=root;}
    public List<LoadedCase> load(String manifest) throws IOException {
        var dataset=EvaluationFiles.JSON.readValue(EvaluationFiles.read(EvaluationFiles.inside(root,manifest)),Dataset.class);
        require(dataset.schemaVersion()==1 && dataset.groundTruthSchemaVersion()==1 && dataset.version()!=null && !dataset.version().isBlank()
            && Set.of("development","test").contains(dataset.split()) && dataset.fixtures()!=null && !dataset.fixtures().isEmpty(),"Dataset schema");
        Set<String> fixtures=new HashSet<>(),defects=new HashSet<>(); List<LoadedCase> loaded=new ArrayList<>();
        for(var f:dataset.fixtures()) {
            require(f.fixtureId()!=null && f.fixtureId().matches("[a-z0-9-]+") && fixtures.add(f.fixtureId()),"Duplicate/invalid fixture ID");
            Path source=EvaluationFiles.inside(root,f.path());
            require(source.startsWith(root.toRealPath().resolve("fixtures")) && Files.isDirectory(source),"Fixture root");
            require(EvaluationFiles.treeHash(source).equals(f.sourceHash()),"Fixture revision mismatch");
            Path truthFile=EvaluationFiles.inside(root,f.groundTruth());
            require(!truthFile.startsWith(source),"Ground truth leakage");
            var truth=List.of(EvaluationFiles.JSON.readValue(EvaluationFiles.read(truthFile),Defect[].class));
            require(!truth.isEmpty(),"Missing ground truth/negative controls");
            for(var d:truth) {
                require(d.defectId()!=null && defects.add(d.defectId()) && f.fixtureId().equals(d.fixtureId())
                    && f.sourceHash().equals(d.sourceRevision()),"Duplicate defect ID or wrong source revision");
                require(d.classification()!=null && d.locations()!=null && !d.locations().isEmpty()
                    && Set.of("SECURITY","BUG","PERFORMANCE","CODE_QUALITY","ARCHITECTURE").contains(d.category())
                    && Set.of("CRITICAL","HIGH","MEDIUM","LOW").contains(d.severity()),"Defect schema");
                for(String s:Arrays.asList(d.family(),d.rootCause(),d.triggerCondition(),d.expectedImpact(),d.counterevidence(),d.verificationProcedure(),d.rationale()))
                    require(s!=null && !s.isBlank(),"Missing annotation rationale/verification");
                for(var l:d.locations()) {
                    var lines=Files.readAllLines(EvaluationFiles.inside(source,l.filePath()));
                    require(l.lineStart()>0 && l.lineEnd()>=l.lineStart() && l.lineEnd()<=lines.size()
                        && l.evidence()!=null && !l.evidence().isBlank(),"Annotation location");
                    require(String.join("\n",lines.subList(l.lineStart()-1,l.lineEnd())).contains(l.evidence()),"Annotation evidence mismatch");
                }
                try(var paths=Files.walk(source)) {
                    for(Path p:paths.filter(Files::isRegularFile).toList())
                        require(!Files.readString(p).contains(d.defectId()),"Answer label leaked into source");
                }
            }
            loaded.add(new LoadedCase(f,source,truth));
        }
        return List.copyOf(loaded);
    }
    public void checkSplits(String development,String test) throws IOException {
        var dev=load(development); var heldOut=load(test); Set<String> ids=new HashSet<>(),javaHashes=new HashSet<>(),defectIds=new HashSet<>();
        for(var c:dev) {ids.add(c.fixture().fixtureId()); c.truth().forEach(t->defectIds.add(t.defectId())); javaHashes.addAll(javaHashes(c.sourceRoot()));}
        for(var c:heldOut) {
            require(!ids.contains(c.fixture().fixtureId()),"Project split leakage");
            for(var d:c.truth()) require(!defectIds.contains(d.defectId()),"Defect ID split leakage");
            for(String hash:javaHashes(c.sourceRoot())) require(!javaHashes.contains(hash),"Identical Java source across splits");
        }
    }
    private Set<String> javaHashes(Path source) throws IOException {
        Set<String> values=new HashSet<>();
        try(var paths=Files.walk(source)) {for(Path p:paths.filter(p->p.toString().endsWith(".java")).toList()) values.add(EvaluationFiles.sha(EvaluationFiles.read(p)));}
        return values;
    }
    static void require(boolean condition,String message) {if(!condition) throw new IllegalArgumentException(message);}
}
