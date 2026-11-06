package prj.anhzxje.aiagent.review;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Set;

/** Deterministic shape validation, never a substitute for semantic review accuracy. */
public final class ReviewReportValidator {
    public ObjectNode validate(JsonNode value) {
        require(value != null && value.isObject(), "report object");
        var report = (ObjectNode) value;
        text(report,"summary");
        require(report.path("issues").isArray(),"issues array");
        strings(report.path("suggestions"),"suggestions");
        boolean v2 = report.has("schemaVersion");
        if (v2) {
            require(report.path("schemaVersion").isIntegralNumber() && report.path("schemaVersion").asInt() == 2,"schemaVersion");
            strings(report.path("limitations"),"limitations");
        }
        for (var item : report.path("issues")) {
            require(item.isObject(),"issue object");
            var issue=(ObjectNode)item;
            enumeration(issue,"severity",Set.of("CRITICAL","HIGH","MEDIUM","LOW"));
            enumeration(issue,"category",Set.of("BUG","SECURITY","PERFORMANCE","CODE_QUALITY","ARCHITECTURE"));
            if (v2) {
                enumeration(issue,"classification",Set.of("CONFIRMED_BUG","POTENTIAL_RISK"));
                String path=text(issue,"filePath");
                require(!path.startsWith("/") && !path.contains("\\") && !path.contains(":")
                    && java.util.Arrays.stream(path.split("/",-1)).noneMatch(p -> p.isBlank() || p.equals("..") || p.equals(".")),"relative filePath");
                require(issue.path("lineStart").canConvertToInt() && issue.path("lineStart").isIntegralNumber()
                    && issue.path("lineEnd").canConvertToInt() && issue.path("lineEnd").isIntegralNumber(),"line range");
                int start=issue.path("lineStart").asInt(), end=issue.path("lineEnd").asInt();
                require(start>0 && end>=start && end-(long)start<400,"line range");
                require(text(issue,"evidence").length()<=2000,"bounded evidence");
                String explanation=text(issue,"explanation"), suggestion=text(issue,"suggestion");
                // Keep the original Phase 1 fields for existing API consumers.
                issue.put("file",path); issue.put("description",explanation); issue.put("recommendation",suggestion);
            } else {
                text(issue,"file"); text(issue,"description"); text(issue,"recommendation");
            }
        }
        return report;
    }
    private static void strings(JsonNode array,String field) {
        require(array.isArray(),field+" array");
        for(var value:array) require(value.isTextual() && !value.asText().isBlank(),field+" string");
    }
    private static String text(JsonNode object,String field) {
        var value=object.path(field);
        require(value.isTextual() && !value.asText().isBlank(),field+" string");
        return value.asText();
    }
    private static void enumeration(JsonNode object,String field,Set<String> values) {
        require(values.contains(text(object,field)),field+" enum");
    }
    private static void require(boolean condition,String field) {
        if(!condition) throw new IllegalStateException("Báo cáo AI không hợp lệ: "+field);
    }
}
