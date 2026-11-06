package prj.anhzxje.aiagent.evaluation;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;
import static prj.anhzxje.aiagent.evaluation.EvaluationTypes.*;

public final class EvaluationExporter {
    public record Summary(Group group, String provenance, int cases, Counts raw, Counts accepted,
                          Map<String,Counts> byCategory, Map<String,FamilyDetection> byFamily,
                          int rawFindings, int acceptedFindings, int rejectedFindings, int duplicates, int pendingAdjudications,
                          Double evidenceValidityRate, Double locationAccuracy, Double severityAgreement,
                          double schemaFailureRate, double evidenceFailureRate, double incompleteRate,
                          double invalidReportRate, double taskErrorRate, double notRunRate,
                          double budgetExhaustionRate, double quotaFailureRate,
                          Double meanGenerationLatencyMs, Double meanEvaluationLatencyMs, int usageObservedCases,
                          Long modelDispatches, Long toolInvocations, Long peakContextChars, Long inputTokens, Long outputTokens,
                          Double estimatedCostUsd) {}

    public List<Summary> summaries(List<CaseResult> cases) {
        return summaries(cases,null);
    }
    private List<Summary> summaries(List<CaseResult> cases,Pricing pricing) {
        List<Summary> summaries=new ArrayList<>();
        for(Group group:Group.values()) {
            var rows=cases.stream().filter(r->r.group()==group).toList(); if(rows.isEmpty()) continue;
            var raw=aggregate(rows,CaseResult::rawScores); var accepted=aggregate(rows,CaseResult::acceptedScores);
            Map<String,int[]> categories=new TreeMap<>(),families=new TreeMap<>();
            for(var row:rows) {
                row.acceptedScores().byCategory().forEach((key,value)-> {var a=categories.computeIfAbsent(key,k->new int[3]);a[0]+=value.tp();a[1]+=value.fp();a[2]+=value.fn();});
                row.acceptedScores().byFamily().forEach((key,value)-> {var a=families.computeIfAbsent(key,k->new int[2]);a[0]+=value.detected();a[1]+=value.total();});
            }
            Map<String,Counts> categoryScores=new TreeMap<>();categories.forEach((k,v)->categoryScores.put(k,EvaluationScorer.counts(v[0],v[1],v[2])));
            Map<String,FamilyDetection> familyScores=new TreeMap<>();families.forEach((k,v)->familyScores.put(k,new FamilyDetection(v[0],v[1],EvaluationScorer.ratio(v[0],v[1]))));
            int rawFindings=rows.stream().mapToInt(CaseResult::rawFindingCount).sum(),acceptedFindings=rows.stream().mapToInt(CaseResult::acceptedFindingCount).sum();
            long validEvidence=rows.stream().mapToLong(r->r.rawScores().evidenceValidityRate()==null?0:Math.round(r.rawScores().evidenceValidityRate()*r.rawFindingCount())).sum();
            long correctLocation=rows.stream().mapToLong(r->r.acceptedScores().locationAccuracy()==null?0:Math.round(r.acceptedScores().locationAccuracy()*r.acceptedScores().overall().tp())).sum();
            long correctSeverity=rows.stream().mapToLong(r->r.acceptedScores().severityAgreement()==null?0:Math.round(r.acceptedScores().severityAgreement()*r.acceptedScores().overall().tp())).sum();
            var known=rows.stream().filter(r->r.usage()!=null).toList();
            summaries.add(new Summary(group,String.join("+",rows.stream().map(CaseResult::provenance).distinct().sorted().toList()),rows.size(),raw,accepted,categoryScores,familyScores,
                rawFindings,acceptedFindings,rawFindings-acceptedFindings,rows.stream().mapToInt(r->r.rawScores().duplicateFindings()).sum(),rows.stream().mapToInt(r->r.rawScores().pendingAdjudications()).sum(),
                EvaluationScorer.ratio(validEvidence,rawFindings),EvaluationScorer.ratio(correctLocation,accepted.tp()),EvaluationScorer.ratio(correctSeverity,accepted.tp()),
                rate(rows,r->r.validation().reasons().contains("SCHEMA_INVALID")),rate(rows,r->!r.validation().evidenceValid()),rate(rows,r->r.validation().incomplete()),
                rate(rows,r->r.status().equals("INVALID_REPORT")),rate(rows,r->r.status().equals("ERROR")),rate(rows,r->r.status().startsWith("NOT_RUN_")),
                rate(rows,r->r.status().equals("BUDGET_EXHAUSTED")),rate(rows,r->r.status().equals("QUOTA_EXHAUSTED")),
                mean(rows.stream().map(CaseResult::generationLatencyMs).toList()),mean(rows.stream().map(r->Long.valueOf(r.evaluationLatencyMs())).toList()),known.size(),
                sumKnown(known,r->r.usage().modelDispatches()==null?null:r.usage().modelDispatches().longValue()),sumKnown(known,r->r.usage().toolInvocations()==null?null:r.usage().toolInvocations().longValue()),
                known.stream().map(r->r.usage().peakContextChars()).filter(Objects::nonNull).max(Long::compare).orElse(null),
                sumKnown(known,r->r.usage().inputTokens()),sumKnown(known,r->r.usage().outputTokens()),estimateCost(rows.stream().map(CaseResult::usage).toList(),pricing)));
        }
        return summaries;
    }
    public void export(Path dir,Config config,List<CaseResult> rows) throws IOException {
        var summary=summaries(rows,config.pricing());
        var result=new LinkedHashMap<String,Object>();result.put("mode",config.mode());result.put("config",config);result.put("summaries",summary);result.put("cases",rows);
        result.put("warning",config.mode()==Mode.REAL_LLM ? "Actual provider experiment; inspect failures and pending adjudications before conclusions" : "No actual LLM quality results; offline/synthetic/mock only");
        EvaluationFiles.JSON.writerWithDefaultPrettyPrinter().writeValue(dir.resolve("experiment-results.json").toFile(),result);
        EvaluationFiles.JSON.writerWithDefaultPrettyPrinter().writeValue(dir.resolve("per-case.json").toFile(),rows);
        Map<String,Object> queue=new LinkedHashMap<>();
        for(var r:rows) for(var candidate:r.rawMatching().candidates()) {
            Group generationGroup=r.group()==Group.R2?Group.R1:r.group();
            String key=r.fixtureId()+":"+generationGroup+":"+r.generationHash()+":"+candidate.predictionIndex();
            queue.putIfAbsent(key,Map.of("fixtureId",r.fixtureId(),"group",generationGroup,"generationHash",r.generationHash(),
                "predictionIndex",candidate.predictionIndex(),"candidateDefectIds",candidate.defectIds(),"reason",candidate.reason()));
        }
        EvaluationFiles.JSON.writerWithDefaultPrettyPrinter().writeValue(dir.resolve("adjudication-queue.json").toFile(),queue.values());
        var csv=new StringBuilder("group,provenance,cases,raw_tp,raw_fp,raw_fn,tp,fp,fn,precision,recall,f1,raw_findings,accepted_findings,rejected_findings,duplicates,pending_adjudications,schema_failure_rate,evidence_failure_rate,incomplete_rate,invalid_report_rate,task_error_rate,not_run_rate,budget_exhaustion_rate,quota_failure_rate,mean_generation_latency_ms,mean_evaluation_latency_ms,usage_observed_cases,model_dispatches,tool_invocations,peak_context_chars,input_tokens,output_tokens,estimated_cost_usd\n");
        for(var s:summary) csv.append(row(s.group(),s.provenance(),s.cases(),s.raw().tp(),s.raw().fp(),s.raw().fn(),s.accepted().tp(),s.accepted().fp(),s.accepted().fn(),s.accepted().precision(),s.accepted().recall(),s.accepted().f1(),s.rawFindings(),s.acceptedFindings(),s.rejectedFindings(),s.duplicates(),s.pendingAdjudications(),s.schemaFailureRate(),s.evidenceFailureRate(),s.incompleteRate(),s.invalidReportRate(),s.taskErrorRate(),s.notRunRate(),s.budgetExhaustionRate(),s.quotaFailureRate(),s.meanGenerationLatencyMs(),s.meanEvaluationLatencyMs(),s.usageObservedCases(),s.modelDispatches(),s.toolInvocations(),s.peakContextChars(),s.inputTokens(),s.outputTokens(),s.estimatedCostUsd()));
        Files.writeString(dir.resolve("summary.csv"),csv);
        var perCase=new StringBuilder("fixture,group,status,provenance,generation_hash,reused,tp,fp,fn,raw_findings,accepted_findings,rejected_findings,schema_valid,evidence_valid,incomplete,pending,generation_latency_ms,evaluation_latency_ms\n");
        for(var r:rows) perCase.append(row(r.fixtureId(),r.group(),r.status(),r.provenance(),r.generationHash(),r.generationReused(),r.acceptedScores().overall().tp(),r.acceptedScores().overall().fp(),r.acceptedScores().overall().fn(),r.rawFindingCount(),r.acceptedFindingCount(),r.rejectedFindingCount(),r.validation().schemaValid(),r.validation().evidenceValid(),r.validation().incomplete(),r.rawScores().pendingAdjudications(),r.generationLatencyMs(),r.evaluationLatencyMs()));
        Files.writeString(dir.resolve("per-case.csv"),perCase);
        var md=new StringBuilder("# Research evaluation\n\n").append(result.get("warning")).append(".\n\n")
            .append("Mode: ").append(config.mode()).append(". Model: ").append(config.model()).append(". Dataset: ").append(config.dataset()).append(".\n\n")
            .append("R0 is a controlled Phase-2 no-skill baseline, not a historical baseline or Chat /review. R2 reuses R1 generation; do not add their usage as separate physical calls.\n\n")
            .append("| Group | Cases | Raw TP/FP/FN | Accepted TP/FP/FN | Precision | Recall | F1 | Pending | Invalid schema rate |\n| --- | --- | --- | --- | --- | --- | --- | --- | --- |\n");
        for(var s:summary) md.append("| ").append(s.group()).append(" | ").append(s.cases()).append(" | ").append(triple(s.raw())).append(" | ").append(triple(s.accepted())).append(" | ").append(s.accepted().precision()).append(" | ").append(s.accepted().recall()).append(" | ").append(s.accepted().f1()).append(" | ").append(s.pendingAdjudications()).append(" | ").append(s.schemaFailureRate()).append(" |\n");
        md.append("\nUndefined ratios are null, not 100%. Pending adjudications receive no TP credit: these scores are provisional. No false positive rate is reported because there is no trusted TN unit.\n\n")
            .append("See JSON for per-category/family, evidence/location/severity metrics and failure rates; CSV for latency/usage. Missing provider usage/cost stays null. Characters are not tokens; model dispatches are not observed HTTP request counts.\n\n")
            .append("## Error analysis\n\n");
        for(var r:rows) md.append("- ").append(r.fixtureId()).append('/').append(r.group()).append(": ").append(r.status()).append("; FP prediction indices ").append(r.acceptedMatching().falsePositives()).append("; FN annotation IDs ").append(r.acceptedMatching().falseNegatives()).append("; pending ").append(r.rawScores().pendingAdjudications()).append(".\n");
        md.append("\n## Limitations\n\nFixtures are synthetic, small and project-split; family overlap remains. Literal identity matching has low automatic recall and requires human adjudication for paraphrases. Schema/evidence validation does not prove semantic correctness. No model-quality improvement is inferred from replay or mock data. No statistical significance or real-model conclusion is established by this framework alone.\n");
        Files.writeString(dir.resolve("research-report.md"),md);
    }
    static String row(Object... values) {return String.join(",",Arrays.stream(values).map(EvaluationExporter::cell).toList())+"\n";}
    static Double estimateCost(List<Usage> values,Pricing pricing) {
        if(pricing==null || values.isEmpty() || values.stream().anyMatch(u->u==null || u.inputTokens()==null || u.outputTokens()==null)) return null;
        return values.stream().mapToDouble(u->(u.inputTokens()*pricing.inputUsdPerMillionTokens()+u.outputTokens()*pricing.outputUsdPerMillionTokens())/1_000_000.0).sum();
    }
    static String cell(Object value) {
        String text=value==null?"":EvaluationFiles.redact(value.toString());
        if(text.stripLeading().matches("(?s)^[=+@-].*")) text="'"+text;
        return "\""+text.replace("\"","\"\"")+"\"";
    }
    private Counts aggregate(List<CaseResult> rows,Function<CaseResult,Scores> scores) {int tp=0,fp=0,fn=0;for(var r:rows){var c=scores.apply(r).overall();tp+=c.tp();fp+=c.fp();fn+=c.fn();}return EvaluationScorer.counts(tp,fp,fn);}
    private static double rate(List<CaseResult> rows,java.util.function.Predicate<CaseResult> predicate) {return rows.stream().filter(predicate).count()/(double)rows.size();}
    private static Double mean(List<Long> values) {return values.stream().filter(Objects::nonNull).mapToLong(Long::longValue).average().stream().boxed().findFirst().orElse(null);}
    private static Long sumKnown(List<CaseResult> rows,Function<CaseResult,Long> value) {var values=rows.stream().map(value).filter(Objects::nonNull).toList();return values.isEmpty()?null:values.stream().mapToLong(Long::longValue).sum();}
    private static String triple(Counts c) {return c.tp()+"/"+c.fp()+"/"+c.fn();}
}
