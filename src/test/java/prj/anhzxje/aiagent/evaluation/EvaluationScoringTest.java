package prj.anhzxje.aiagent.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static prj.anhzxje.aiagent.evaluation.EvaluationTypes.*;

class EvaluationScoringTest {
    final ObjectMapper mapper = new ObjectMapper();
    Defect truth(String id, Label label) {
        return new Defect(id,"fixture","hash","sql-injection","SECURITY","HIGH",label,
            List.of(new Location("Store.java",6,6,"query(name)")),"Untrusted name changes SQL structure.",
            "HTTP input", "Read other accounts", "No binding", "Send quote", "Direct concatenation");
    }
    com.fasterxml.jackson.databind.JsonNode finding(String explanation) throws Exception {
        return mapper.readTree("""
          {"filePath":"Store.java","lineStart":6,"lineEnd":6,"category":"SECURITY","severity":"HIGH",
           "classification":"CONFIRMED_BUG","evidence":"query(name)","explanation":"%s"}
          """.formatted(explanation));
    }
    @Test void duplicatePredictionsNeverMultiplyTruePositives() throws Exception {
        var predictions=List.of(finding("Untrusted name changes SQL structure."),finding("Untrusted name changes SQL structure."));
        var truth=List.of(truth("D1",Label.CONFIRMED_BUG));
        var match=new FindingMatcher().match(predictions,truth,List.of(),"fixture",Group.R1,"hash",List.of(true,true));
        var score=new EvaluationScorer().score(predictions,truth,match,List.of(true,true));
        assertEquals(1,score.overall().tp()); assertEquals(1,score.overall().fp()); assertEquals(0,score.overall().fn());
        assertEquals(.5,score.overall().precision()); assertEquals(1.0,score.overall().recall());
        assertEquals(2.0/3,score.overall().f1()); assertEquals(1,score.duplicateFindings());
    }
    @Test void paraphraseRequiresAdjudicationRatherThanSimilarityCredit() throws Exception {
        var predictions=List.of(finding("The input may affect a query."));
        var truth=List.of(truth("D1",Label.CONFIRMED_BUG));
        var match=new FindingMatcher().match(predictions,truth,List.of(),"fixture",Group.R1,"hash",List.of(true));
        assertTrue(match.matches().isEmpty()); assertEquals(1,match.candidates().size());
        var score=new EvaluationScorer().score(predictions,truth,match,List.of(true));
        assertTrue(score.provisional()); assertEquals(1,score.overall().fn());
        var decision=new Adjudication("fixture",Group.R1,"hash",0,"D1","MATCH","Same root cause, manually checked.");
        var resolved=new FindingMatcher().match(predictions,truth,List.of(decision),"fixture",Group.R1,"hash",List.of(true));
        assertEquals(1,resolved.matches().size()); assertEquals("MANUAL",resolved.matches().getFirst().level());
        assertThrows(IllegalArgumentException.class,()->new FindingMatcher().match(predictions,truth,List.of(decision),"fixture",Group.R1,"changed",List.of(true)));
    }
    @Test void zeroDenominatorsAreUndefinedAndNegativeControlsAreNotDefects() throws Exception {
        var truth=List.of(truth("SAFE",Label.NON_BUG));
        var matcher=new FindingMatcher(); var scorer=new EvaluationScorer();
        var empty=scorer.score(List.of(),truth,matcher.match(List.of(),truth,List.of(),"fixture",Group.R0,"hash",List.of()),List.of());
        assertNull(empty.overall().precision()); assertNull(empty.overall().recall()); assertNull(empty.overall().f1());
        var predictions=List.of(finding("Untrusted name changes SQL structure."));
        var falseAlarm=scorer.score(predictions,truth,matcher.match(predictions,truth,List.of(),"fixture",Group.R0,"hash",List.of(true)),List.of(true));
        assertEquals(0,falseAlarm.overall().tp()); assertEquals(1,falseAlarm.overall().fp()); assertNull(falseAlarm.overall().recall());
    }
    @Test void errorsWithNoPredictionsStillContributeFalseNegatives() {
        var truth=List.of(truth("D1",Label.CONFIRMED_BUG));
        var match=new FindingMatcher().match(List.of(),truth,List.of(),"fixture",Group.R1,"hash",List.of());
        assertEquals(1,new EvaluationScorer().score(List.of(),truth,match,List.of()).overall().fn());
    }
    @Test void ambiguousIdentityNeverArbitrarilySelectsOneDefect() throws Exception {
        var truth=List.of(truth("D1",Label.CONFIRMED_BUG),truth("D2",Label.CONFIRMED_BUG));
        var match=new FindingMatcher().match(List.of(finding("Untrusted name changes SQL structure.")),truth,List.of(),"fixture",Group.R1,"hash",List.of(true));
        assertTrue(match.matches().isEmpty());assertEquals(2,match.candidates().getFirst().defectIds().size());
    }
    @Test void unknownFindingIsQueuedRatherThanDeclaredAConfirmedFalsePositive() throws Exception {
        var prediction=(com.fasterxml.jackson.databind.node.ObjectNode)finding("Another root cause not in the annotations.");
        prediction.put("filePath","Other.java");
        var match=new FindingMatcher().match(List.of(prediction),List.of(truth("D1",Label.CONFIRMED_BUG)),List.of(),"fixture",Group.R1,"hash",List.of(true));
        assertEquals(1,match.candidates().size());assertTrue(match.candidates().getFirst().defectIds().isEmpty());
    }

    @Test void manualMatchTakesPrecedenceOverAnEarlierExactPrediction() throws Exception {
        var predictions = List.of(finding("Untrusted name changes SQL structure."), finding("Paraphrased cause"));
        var decision = new Adjudication("fixture", Group.R1, "hash", 1, "D1", "MATCH", "Verified identity");
        var match = new FindingMatcher().match(predictions, List.of(truth("D1", Label.CONFIRMED_BUG)),
                List.of(decision), "fixture", Group.R1, "hash", List.of(true, true));
        assertEquals(List.of(new Match(1, "D1", "MANUAL")), match.matches());
        assertEquals(List.of(0), match.falsePositives());
        assertEquals(List.of(0), match.duplicates());
        assertTrue(match.falseNegatives().isEmpty());
    }

    @Test void explicitNegativeRequiresEvidenceBeforeSuppressingAdjudicationQueue() throws Exception {
        var predictions = List.of(finding("Untrusted name changes SQL structure."));
        var truth = List.of(truth("SAFE", Label.NON_BUG));
        var matcher = new FindingMatcher();
        var evidenced = matcher.match(predictions, truth, List.of(), "fixture", Group.R0, "hash", List.of(true));
        assertEquals(List.of(0), evidenced.falsePositives());
        assertTrue(evidenced.candidates().isEmpty());
        var unverified = matcher.match(predictions, truth, List.of(), "fixture", Group.R0, "hash", List.of(false));
        assertEquals(1, unverified.candidates().size());
    }

    @Test void duplicateAdjudicationsAndTwoManualClaimsForOneDefectAreRejected() throws Exception {
        var predictions = List.of(finding("First"), finding("Second"));
        var truth = List.of(truth("D1", Label.CONFIRMED_BUG));
        var first = new Adjudication("fixture", Group.R1, "hash", 0, "D1", "MATCH", "Checked");
        var second = new Adjudication("fixture", Group.R1, "hash", 1, "D1", "MATCH", "Checked");
        var matcher = new FindingMatcher();
        assertThrows(IllegalArgumentException.class, () -> matcher.match(predictions, truth, List.of(first, first),
                "fixture", Group.R1, "hash", List.of(true, true)));
        assertThrows(IllegalArgumentException.class, () -> matcher.match(predictions, truth, List.of(first, second),
                "fixture", Group.R1, "hash", List.of(true, true)));
    }

    @Test void whitespaceNormalizationKeepsLiteralMatchingButNeverIgnoresCase() throws Exception {
        var matcher = new FindingMatcher();
        var truth = List.of(truth("D1", Label.CONFIRMED_BUG));
        var spaced = finding("  Untrusted   name changes SQL structure. ");
        assertEquals(1, matcher.match(List.of(spaced), truth, List.of(), "fixture", Group.R1, "hash", List.of(true)).matches().size());
        var lower = finding("untrusted name changes SQL structure.");
        assertTrue(matcher.match(List.of(lower), truth, List.of(), "fixture", Group.R1, "hash", List.of(true)).matches().isEmpty());
    }
}
