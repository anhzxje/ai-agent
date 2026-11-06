package prj.anhzxje.aiagent.skills;

import java.util.List;

/** Server-owned criteria; never populated with Markdown supplied by a request. */
public record ReviewSkillContext(List<SkillDefinition> skills, String language, String projectName) {
    public ReviewSkillContext { skills = List.copyOf(skills); }
    public static ReviewSkillContext legacy() { return new ReviewSkillContext(List.of(), null, null); }
}
