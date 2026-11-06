package prj.anhzxje.aiagent.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import prj.anhzxje.aiagent.infrastructure.ai.LlmBudgetService.ReadScope;
import prj.anhzxje.aiagent.skills.SkillSelection;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** File-only research contracts. No Spring components or production entities. */
public final class EvaluationTypes {
    private EvaluationTypes() {}
    public enum Mode { OFFLINE_REPLAY, MOCK_INTEGRATION, REAL_LLM }
    public enum Group { R0, R1, R2 }
    public enum Label { CONFIRMED_BUG, POTENTIAL_RISK, NON_BUG, SUBJECTIVE_IMPROVEMENT }
    public record Location(String filePath, int lineStart, int lineEnd, String evidence) {}
    public record Defect(String defectId, String fixtureId, String sourceRevision, String family,
                         String category, String severity, Label classification, List<Location> locations,
                         String rootCause, String triggerCondition, String expectedImpact, String counterevidence,
                         String verificationProcedure, String rationale) {}
    public record Fixture(String fixtureId, String path, String sourceHash, String groundTruth) {}
    public record Dataset(int schemaVersion, int groundTruthSchemaVersion, String version, String split, List<Fixture> fixtures) {}
    public record LoadedCase(Fixture fixture, Path sourceRoot, List<Defect> truth) {}
    public record Limits(int maxCases, int maxRequestsPerCase, int maxTotalRequests, int maxModelCalls,
                         int maxToolCalls, int maxTotalToolCalls, int maxPromptChars, int maxContextChars,
                         int maxSkillChars, int maxSkills, int concurrency, int retries, int maxOutputTokens) {}
    public record Config(int schemaVersion, String dataset, Mode mode, String provider, String model,
                         Map<String, Object> modelOptions, List<String> tools, List<SkillSelection> skills,
                         Limits limits, Map<String, String> replays, String adjudications, boolean storeRawFindings,
                         Pricing pricing) {}
    public record Pricing(String currency, double inputUsdPerMillionTokens, double outputUsdPerMillionTokens,
                          String source, String effectiveDate) {}
    public record Usage(Integer modelDispatches, Integer toolInvocations, Long peakContextChars,
                        Long accumulatedContextChars, Long inputTokens, Long outputTokens) {}
    public record Generation(String status, String rawOutput, List<ReadScope> observations, Usage usage,
                             Long generationLatencyMs, String errorCode, String provenance) {}
    public record Validation(boolean schemaValid, boolean evidenceValid, boolean incomplete, List<String> reasons) {}
    public record Match(int predictionIndex, String defectId, String level) {}
    public record Candidate(int predictionIndex, List<String> defectIds, String reason) {}
    public record MatchResult(List<Match> matches, List<Integer> falsePositives, List<String> falseNegatives,
                              List<Integer> duplicates, List<Candidate> candidates) {}
    public record Counts(int tp, int fp, int fn, Double precision, Double recall, Double f1) {}
    public record FamilyDetection(int detected, int total, Double rate) {}
    public record Scores(Counts overall, Map<String, Counts> byCategory, Map<String, FamilyDetection> byFamily,
                         int duplicateFindings, int pendingAdjudications, boolean provisional,
                         Double evidenceValidityRate, Double locationAccuracy, Double severityAgreement) {}
    public record Adjudication(String fixtureId, Group group, String generationHash, int predictionIndex,
                               String defectId, String decision, String rationale) {}
    public record CaseResult(String experimentId, String fixtureId, Group group, String effectiveModel,
                             List<SkillSelection> skillSelections, int promptSchemaVersion, String sourceHash,
                             String generationHash, String status, String errorCode, String provenance,
                             boolean generationReused, int rawFindingCount, int acceptedFindingCount, int rejectedFindingCount,
                             List<JsonNode> rawFindings, List<JsonNode> acceptedFindings,
                             List<JsonNode> rejectedFindings, Validation validation, MatchResult rawMatching,
                             MatchResult acceptedMatching, Scores rawScores, Scores acceptedScores,
                             Usage usage, Long generationLatencyMs, long evaluationLatencyMs) {}
}
