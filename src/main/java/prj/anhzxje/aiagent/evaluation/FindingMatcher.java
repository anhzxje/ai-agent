package prj.anhzxje.aiagent.evaluation;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static prj.anhzxje.aiagent.evaluation.EvaluationTypes.*;

/** Conservative identity rubric, never an embedding/fuzzy-similarity judge. */
public final class FindingMatcher {
    public MatchResult match(List<JsonNode> predictions, List<Defect> truth, List<Adjudication> decisions,
                             String fixture, Group group, String generationHash, List<Boolean> evidence) {
        Map<Integer, Adjudication> manual = adjudications(decisions, fixture, group, generationHash, predictions.size());
        List<Match> matches = new ArrayList<>();
        List<Integer> falsePositives = new ArrayList<>();
        List<Integer> duplicates = new ArrayList<>();
        List<Candidate> candidates = new ArrayList<>();
        Set<String> used = new HashSet<>();

        // Human decisions take precedence, independent of prediction order.
        applyManualMatches(manual, truth, used, matches);
        for (int index = 0; index < predictions.size(); index++) {
            Adjudication decision = manual.get(index);
            if (decision != null) {
                if (decision.decision().equals("NO_MATCH")) falsePositives.add(index);
                continue;
            }

            JsonNode prediction = predictions.get(index);
            boolean hasEvidence = Boolean.TRUE.equals(evidence.get(index));
            List<Defect> exact = truth.stream()
                    .filter(defect -> defect.classification() == Label.CONFIRMED_BUG)
                    .filter(defect -> exactIdentity(prediction, defect))
                    .toList();
            if (hasEvidence && exact.size() == 1) {
                String defectId = exact.getFirst().defectId();
                if (used.add(defectId)) {
                    matches.add(new Match(index, defectId, "AUTOMATIC_EXACT"));
                } else {
                    duplicates.add(index);
                    falsePositives.add(index);
                }
            } else {
                falsePositives.add(index);
                List<String> possible = truth.stream()
                        .filter(defect -> defect.classification() == Label.CONFIRMED_BUG)
                        .filter(defect -> location(prediction, defect, 3) && sameCategory(prediction, defect))
                        .map(Defect::defectId)
                        .toList();
                if (!explicitNegative(prediction, truth, hasEvidence)) {
                    candidates.add(new Candidate(index, possible, possible.isEmpty()
                            ? "UNMATCHED_IDENTITY: possibly unannotated or out of scope; human adjudication required"
                            : "RULE_BASED_CANDIDATE: root cause/evidence requires adjudication; no TP credit"));
                }
            }
        }

        List<String> falseNegatives = truth.stream()
                .filter(defect -> defect.classification() == Label.CONFIRMED_BUG && !used.contains(defect.defectId()))
                .map(Defect::defectId)
                .toList();
        return new MatchResult(List.copyOf(matches), List.copyOf(falsePositives), falseNegatives,
                List.copyOf(duplicates), List.copyOf(candidates));
    }

    private static Map<Integer, Adjudication> adjudications(List<Adjudication> decisions, String fixture,
                                                           Group group, String generationHash, int predictionCount) {
        Map<Integer, Adjudication> manual = new HashMap<>();
        for (Adjudication decision : decisions) {
            if (!decision.fixtureId().equals(fixture) || decision.group() != group) continue;
            if (!decision.generationHash().equals(generationHash)
                    || decision.predictionIndex() < 0 || decision.predictionIndex() >= predictionCount
                    || decision.rationale() == null || decision.rationale().isBlank()
                    || !Set.of("MATCH", "NO_MATCH").contains(decision.decision())
                    || manual.putIfAbsent(decision.predictionIndex(), decision) != null) {
                throw new IllegalArgumentException("Invalid/stale adjudication");
            }
        }
        return manual;
    }

    private static void applyManualMatches(Map<Integer, Adjudication> manual, List<Defect> truth,
                                          Set<String> used, List<Match> matches) {
        for (var entry : new TreeMap<>(manual).entrySet()) {
            if (!entry.getValue().decision().equals("MATCH")) continue;
            Defect defect = truth.stream()
                    .filter(candidate -> candidate.defectId().equals(entry.getValue().defectId())
                            && candidate.classification() == Label.CONFIRMED_BUG)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Adjudication must reference a confirmed defect"));
            if (!used.add(defect.defectId())) throw new IllegalArgumentException("Manual matches must be one-to-one");
            matches.add(new Match(entry.getKey(), defect.defectId(), "MANUAL"));
        }
    }

    private static boolean exactIdentity(JsonNode prediction, Defect defect) {
        return location(prediction, defect, 0) && sameCategory(prediction, defect)
                && sameCause(prediction, defect)
                && defect.locations().stream().anyMatch(location ->
                        normalized(location.evidence()).equals(normalized(prediction.path("evidence").asText())));
    }

    private static boolean explicitNegative(JsonNode prediction, List<Defect> truth, boolean hasEvidence) {
        return truth.stream()
                .filter(defect -> defect.classification() == Label.NON_BUG
                        || defect.classification() == Label.SUBJECTIVE_IMPROVEMENT)
                .anyMatch(defect -> location(prediction, defect, 0) && sameCategory(prediction, defect)
                        && sameCause(prediction, defect) && hasEvidence);
    }

    private static boolean sameCategory(JsonNode prediction, Defect defect) {
        return defect.category().equals(prediction.path("category").asText());
    }

    private static boolean sameCause(JsonNode prediction, Defect defect) {
        return normalized(defect.rootCause()).equals(normalized(explanation(prediction)));
    }

    static String path(JsonNode prediction) {
        return prediction.path("filePath").asText(prediction.path("file").asText()).replace('\\', '/');
    }

    static String explanation(JsonNode prediction) {
        return prediction.path("explanation").asText(prediction.path("description").asText());
    }

    static boolean location(JsonNode prediction, Defect defect, int proximity) {
        int start = prediction.path("lineStart").asInt(prediction.path("line").asInt(-1));
        int end = prediction.path("lineEnd").asInt(start);
        return start > 0 && end >= start && defect.locations().stream().anyMatch(location ->
                location.filePath().equals(path(prediction))
                        && (long) start <= location.lineEnd() + (long) proximity
                        && (long) end >= location.lineStart() - (long) proximity);
    }

    private static String normalized(String value) {
        return value == null ? "" : value.strip().replaceAll("\\s+", " ");
    }
}
