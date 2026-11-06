package prj.anhzxje.aiagent.skills;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class SkillRegistry {
    private static final int MAX_RESOURCE_BYTES = 64 * 1024;
    private final List<SkillDefinition> definitions;
    public record CatalogEntry(String id, String version, String description, List<String> languages,
                               List<String> conflictsWith, String resource) {}
    public SkillRegistry(ObjectMapper mapper, ResourceLoader loader, SkillValidationService validation) {
        try {
            var entries = mapper.readValue(read(loader, "catalog.json"), CatalogEntry[].class);
            if (entries == null || entries.length == 0 || entries.length > 100) throw new IllegalArgumentException("Catalog skill không hợp lệ");
            List<SkillDefinition> loaded = new ArrayList<>();
            Set<String> keys = new HashSet<>();
            for (var entry : entries) {
                var definition = new SkillDefinition(entry.id(), entry.version(), entry.description(), entry.languages(),
                        entry.conflictsWith(), read(loader, entry.resource()));
                validation.validateDefinition(definition);
                if (!keys.add(definition.id() + "@" + definition.version())) throw new IllegalArgumentException("Skill trùng ID/version");
                loaded.add(definition);
            }
            for (var definition : loaded) for (String conflict : definition.conflictsWith()) {
                if (loaded.stream().noneMatch(s -> s.id().equals(conflict))) throw new IllegalArgumentException("Conflict tham chiếu skill không tồn tại");
            }
            definitions = List.copyOf(loaded);
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("Không thể nạp catalog skill nội bộ", e);
        }
    }
    private static String read(ResourceLoader loader, String file) throws IOException {
        if (file == null || !file.matches("[a-z0-9-]+\\.(?:md|json)")) throw new IllegalArgumentException("Resource skill không hợp lệ");
        try (var stream = loader.getResource("classpath:skills/" + file).getInputStream()) {
            byte[] bytes = stream.readNBytes(MAX_RESOURCE_BYTES + 1);
            if (bytes.length > MAX_RESOURCE_BYTES) throw new IllegalArgumentException("Resource skill quá lớn");
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
    public List<SkillDefinition> list() { return definitions; }
    public SkillDefinition require(SkillSelection selection) {
        if (selection == null || selection.id() == null) throw new IllegalArgumentException("Skill ID không hợp lệ");
        var candidates = definitions.stream().filter(s -> s.id().equals(selection.id())).toList();
        if (selection.version() == null) {
            if (candidates.size() == 1) return candidates.getFirst();
            throw new IllegalArgumentException("Skill không tồn tại hoặc cần chỉ định version: " + selection.id());
        }
        return candidates.stream().filter(s -> s.version().equals(selection.version())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Skill ID/version không tồn tại: " + selection.id()));
    }
}
