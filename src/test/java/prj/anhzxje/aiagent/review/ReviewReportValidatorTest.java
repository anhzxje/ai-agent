package prj.anhzxje.aiagent.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ReviewReportValidatorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ReviewReportValidator validator = new ReviewReportValidator();

    ObjectNode report() throws Exception {
        return (ObjectNode) mapper.readTree("""
            {"schemaVersion":2,"summary":"Phạm vi giới hạn","issues":[{
              "classification":"CONFIRMED_BUG","severity":"HIGH","category":"SECURITY",
              "filePath":"AccountRepository.java","lineStart":6,"lineEnd":6,
              "evidence":"executeQuery(sql)","explanation":"Input đi trực tiếp vào truy vấn.",
              "suggestion":"Dùng tham số hóa."}],"suggestions":[],"limitations":[]}
            """);
    }
    @Test void normalizesAliasesWithoutLosingEvidence() throws Exception {
        var output = validator.validate(report());
        assertEquals("AccountRepository.java", output.at("/issues/0/file").asText());
        assertEquals("Input đi trực tiếp vào truy vấn.", output.at("/issues/0/description").asText());
        assertEquals("Dùng tham số hóa.", output.at("/issues/0/recommendation").asText());
    }
    @Test void rejectsMissingEvidenceUnsafePathsBadRangesEnumsAndObjectSuggestions() throws Exception {
        for (String field : List.of("evidence","explanation","suggestion","classification","severity","category")) {
            var report=report(); ((ObjectNode)report.at("/issues/0")).remove(field);
            assertThrows(IllegalStateException.class, () -> validator.validate(report), field);
        }
        for (String path : List.of("../secret.java","/etc/config","C:/project/File.java","a/../b.java")) {
            var report=report(); ((ObjectNode)report.at("/issues/0")).put("filePath",path);
            assertThrows(IllegalStateException.class, () -> validator.validate(report));
        }
        var reversed=report(); ((ObjectNode)reversed.at("/issues/0")).put("lineEnd",5);
        assertThrows(IllegalStateException.class, () -> validator.validate(reversed));
        var severity=report(); ((ObjectNode)severity.at("/issues/0")).put("severity","SEVERE");
        assertThrows(IllegalStateException.class, () -> validator.validate(severity));
        var suggestions=report(); suggestions.withArray("suggestions").addObject().put("text","ambiguous");
        assertThrows(IllegalStateException.class, () -> validator.validate(suggestions));
    }
    @Test void acceptsEmptyFindingsAndHistoricalReportContract() throws Exception {
        assertTrue(validator.validate(mapper.readTree("{\"summary\":\"OK\",\"issues\":[],\"suggestions\":[]}")).path("issues").isEmpty());
        var clean=report(); clean.withArray("issues").removeAll(); clean.withArray("limitations").add("Chưa đọc hết repository");
        assertTrue(validator.validate(clean).path("issues").isEmpty());
        assertEquals(1,clean.path("limitations").size());
    }
}
