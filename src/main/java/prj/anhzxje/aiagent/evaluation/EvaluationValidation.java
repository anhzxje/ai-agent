package prj.anhzxje.aiagent.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import prj.anhzxje.aiagent.review.ReviewReportValidator;
import prj.anhzxje.aiagent.infrastructure.ai.LlmBudgetService.ReadScope;
import java.nio.file.Files;
import java.util.*;
import static prj.anhzxje.aiagent.evaluation.EvaluationTypes.*;

final class EvaluationValidation {
    record Inspected(List<JsonNode> findings,List<Boolean> evidence,Validation outcome) {}
    static Inspected inspect(Generation generation,LoadedCase fixture,boolean requireV2) {
        List<JsonNode> findings=new ArrayList<>(); List<Boolean> evidence=new ArrayList<>(); List<String> reasons=new ArrayList<>();
        boolean schema=false;
        try {
            String raw=generation.rawOutput()==null ? "" : generation.rawOutput().strip();
            if(raw.startsWith("```")) raw=raw.replaceFirst("^```(?:json)?\\s*","").replaceFirst("\\s*```$","");
            JsonNode report=EvaluationFiles.JSON.readTree(raw);
            if(report!=null && report.path("issues").isArray()) report.path("issues").forEach(findings::add);
            new ReviewReportValidator().validate(report==null ? null : report.deepCopy());
            schema=!requireV2 || report.path("schemaVersion").asInt()==2;
        } catch(Exception invalid) { /* Never log raw output or parse exception source snippets. */ }
        if(!schema) reasons.add(generation.rawOutput()==null ? "NO_REPORT" : "SCHEMA_INVALID");
        for(var finding:findings) evidence.add(evidence(finding,fixture,generation.observations()));
        boolean evidenceValid=evidence.stream().allMatch(Boolean.TRUE::equals);
        if(!evidenceValid) reasons.add("EVIDENCE_INVALID");
        boolean incomplete=!"COMPLETED".equals(generation.status()) || generation.observations()==null || generation.observations().isEmpty();
        if(incomplete) reasons.add("INCOMPLETE");
        return new Inspected(List.copyOf(findings),List.copyOf(evidence),new Validation(schema,evidenceValid,incomplete,List.copyOf(reasons)));
    }
    static boolean evidence(JsonNode finding,LoadedCase fixture,List<ReadScope> observations) {
        try {
            String path=finding.path("filePath").asText(finding.path("file").asText());
            int start=finding.path("lineStart").asInt(-1),end=finding.path("lineEnd").asInt(-1);
            String quote=finding.path("evidence").asText();
            if(start<1 || end<start || end-(long)start>=400 || quote.isBlank() || quote.length()>2000 || observations==null) return false;
            var lines=Files.readAllLines(EvaluationFiles.inside(fixture.sourceRoot(),path));
            if(end>lines.size()) return false;
            for(int line=start;line<=end;line++) {
                final int checked=line;
                if(observations.stream().noneMatch(o->o!=null && path.equals(o.filePath()) && checked>=o.lineStart() && checked<=o.lineEnd())) return false;
            }
            return String.join("\n",lines.subList(start-1,end)).contains(quote.replace("\r\n","\n").strip());
        } catch(Exception invalid) {return false;}
    }
}
