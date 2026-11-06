package prj.anhzxje.aiagent.evaluation;

import java.nio.file.*;
import java.util.*;
import static prj.anhzxje.aiagent.evaluation.EvaluationDataset.require;

public final class ExperimentComparison {
    public void compare(Path results,List<String> ids,Path destination) throws Exception {
        require(ids.size()>=2 && new HashSet<>(ids).size()==ids.size(),"Select at least two distinct experiments");
        List<Object> data=new ArrayList<>();String reference=null;
        var csv=new StringBuilder("experiment,group,provenance,tp,fp,fn,precision,recall,f1,pending\n");
        for(String id:ids) {
            require(id.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,79}"),"Safe experiment ID required");
            Path dir=EvaluationFiles.inside(results,id);
            var manifest=EvaluationFiles.JSON.readTree(EvaluationFiles.read(dir.resolve("experiment.json")));
            var conditions=EvaluationFiles.JSON.createObjectNode();
            for(String field:List.of("fixtures","truthHash","frameworkHash","datasetHash","datasetVersion","groundTruthSchemaVersion","javaVersion","promptSchemaVersion","matchingProtocol")) conditions.set(field,manifest.path(field));
            var config=manifest.path("config");
            for(String field:List.of("mode","provider","model","modelOptions","tools","skills","limits")) conditions.set(field,config.path(field));
            String fingerprint=EvaluationFiles.hash(conditions);
            if(reference==null) reference=fingerprint; else require(reference.equals(fingerprint),"Experiments have unequal controlled conditions; do not pool");
            var result=EvaluationFiles.JSON.readTree(EvaluationFiles.read(dir.resolve("experiment-results.json")));
            data.add(Map.of("experimentId",id,"summaries",result.path("summaries")));
            for(var summary:result.path("summaries")) {
                var score=summary.path("accepted");
                csv.append(EvaluationExporter.row(id,summary.path("group").asText(),summary.path("provenance").asText(),score.path("tp").asInt(),score.path("fp").asInt(),score.path("fn").asInt(),
                    nullable(score.get("precision")),nullable(score.get("recall")),nullable(score.get("f1")),summary.path("pendingAdjudications").asInt()));
            }
        }
        Files.createDirectories(destination.getParent());Files.createDirectory(destination);
        EvaluationFiles.create(destination.resolve("comparison.json"),Map.of("conditionHash",reference,"experiments",data,"warning","No causal/model-quality conclusion without adjudicated actual LLM experiments"));
        Files.writeString(destination.resolve("comparison.csv"),csv);
        Files.writeString(destination.resolve("comparison.md"),"# Experiment comparison\n\nControlled condition hash: "+reference+".\n\nExperiments: "+String.join(", ",ids)+". See CSV/JSON for R0/R1/R2 tables. Replays and mocks do not measure Gemini accuracy. No significance claim; no pooled average across different conditions.\n");
    }
    private static Object nullable(com.fasterxml.jackson.databind.JsonNode node) {return node==null || node.isNull()?null:node.asText();}
}
