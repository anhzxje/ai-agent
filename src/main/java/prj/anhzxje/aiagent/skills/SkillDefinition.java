package prj.anhzxje.aiagent.skills;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;

public record SkillDefinition(String id, String version, String description,
        List<String> languages, List<String> conflictsWith, @JsonIgnore String content) {
    public SkillDefinition {
        languages = languages == null ? List.of() : List.copyOf(languages);
        conflictsWith = conflictsWith == null ? List.of() : List.copyOf(conflictsWith);
    }
    public SkillSelection selection() { return new SkillSelection(id, version); }
}
