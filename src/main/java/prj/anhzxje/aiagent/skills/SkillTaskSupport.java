package prj.anhzxje.aiagent.skills;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import java.util.*;

/** Canonicalizes and pins selections at submission; resolves trusted content again at execution. */
@Service
public class SkillTaskSupport {
    private final ObjectMapper mapper;
    private final SkillResolver resolver;
    public SkillTaskSupport(ObjectMapper mapper, SkillResolver resolver) { this.mapper = mapper; this.resolver = resolver; }

    public ObjectNode prepareInput(ObjectNode input, List<SkillSelection> requested, String language, String name) {
        input.remove("_skillProject"); // Never accept forged project metadata from the client.
        boolean selected = requested != null || input.has("skills");
        var resolved = resolver.resolve(requested == null ? selections(input.get("skills")) : requested, language);
        if (selected) input.set("skills", mapper.valueToTree(resolved.stream().map(SkillDefinition::selection).toList()));
        if (!resolved.isEmpty()) {
            if ((language != null && language.length() > 500) || (name != null && name.length() > 500))
                throw new IllegalArgumentException("Project context quá dài");
            var context = input.putObject("_skillProject");
            context.put("language", language); context.put("name", name);
        }
        return input;
    }
    public ReviewSkillContext readContext(JsonNode input) {
        if (input == null || !input.has("skills")) return ReviewSkillContext.legacy();
        String language = nullableText(input.path("_skillProject").get("language"));
        String name = nullableText(input.path("_skillProject").get("name"));
        return new ReviewSkillContext(resolver.resolve(selections(input.get("skills")), language), language, name);
    }
    private static String nullableText(JsonNode value) {
        if (value == null || value.isNull()) return null;
        if (!value.isTextual() || value.asText().length() > 500) throw new IllegalArgumentException("Project context không hợp lệ");
        return value.asText();
    }
    private List<SkillSelection> selections(JsonNode node) {
        if (node == null) return null;
        if (!node.isArray() || node.size() > 30) throw new IllegalArgumentException("skills phải là danh sách tối đa 30 lựa chọn");
        List<SkillSelection> result = new ArrayList<>();
        for (var entry : node) {
            if (!entry.isObject() || !entry.path("id").isTextual()
                    || (entry.has("version") && !entry.get("version").isNull() && !entry.get("version").isTextual()))
                throw new IllegalArgumentException("Lựa chọn skill không hợp lệ");
            entry.fieldNames().forEachRemaining(key -> {
                if (!Set.of("id", "version").contains(key)) throw new IllegalArgumentException("Không nhận nội dung/policy skill từ request");
            });
            result.add(new SkillSelection(entry.get("id").asText(), nullableText(entry.get("version"))));
        }
        return result;
    }
}
