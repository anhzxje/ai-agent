package prj.anhzxje.aiagent.skills;

import org.springframework.stereotype.Service;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class SkillValidationService {
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9-]{0,63}");
    private static final Pattern VERSION = Pattern.compile("[0-9]+\\.[0-9]+\\.[0-9]+");
    // Defense in depth for admin-controlled resources, not a general prompt-injection detector.
    private static final Pattern POLICY_OVERRIDE = Pattern.compile(
        "(?is)ignore\\s+(?:all\\s+)?previous\\s+instructions|override\\s+(?:the\\s+)?system|"
        + "<\\/?(?:system|developer)>|\\[/?INST\\]|<\\|[^>]*\\|>|"
        + "run\\s+shell\\s+commands|reveal\\s+(?:the\\s+)?system\\s+prompt");
    public void validateDefinition(SkillDefinition skill) {
        if (skill == null || skill.id() == null || !ID.matcher(skill.id()).matches()
                || skill.version() == null || !VERSION.matcher(skill.version()).matches()
                || skill.description() == null || skill.description().isBlank() || skill.description().length() > 1000
                || skill.languages().isEmpty() || skill.languages().size() > 30
                || skill.content() == null || skill.content().isBlank() || skill.content().length() > 16000) {
            throw new IllegalArgumentException("Metadata/nội dung skill không hợp lệ");
        }
        if (skill.languages().stream().anyMatch(s -> s.isBlank() || s.length() > 40)
                || skill.conflictsWith().stream().anyMatch(s -> !ID.matcher(s).matches())
                || skill.conflictsWith().contains(skill.id())) {
            throw new IllegalArgumentException("Ngôn ngữ hoặc conflict của skill không hợp lệ");
        }
        if (POLICY_OVERRIDE.matcher(skill.content() + "\n" + skill.description()).find()) {
            throw new IllegalArgumentException("Skill không được ghi đè system policy: " + skill.id());
        }
    }
    public void validateSelection(List<SkillDefinition> skills, String language) {
        Map<String, String> versions = new HashMap<>();
        for (var skill : skills) {
            validateDefinition(skill);
            String old = versions.putIfAbsent(skill.id(), skill.version());
            if (old != null && !old.equals(skill.version())) throw new IllegalArgumentException("Xung đột version: " + skill.id());
            if (language != null && !language.isBlank() && !Set.of("unknown", "đa ngôn ngữ", "mixed").contains(language.toLowerCase(Locale.ROOT))
                    && !skill.languages().contains("*") && skill.languages().stream().noneMatch(s -> s.equalsIgnoreCase(language.trim()))) {
                throw new IllegalArgumentException("Skill không áp dụng cho ngôn ngữ dự án: " + skill.id());
            }
        }
        for (var skill : skills) for (String conflict : skill.conflictsWith()) {
            if (versions.containsKey(conflict)) throw new IllegalArgumentException("Skill xung đột: " + skill.id() + " / " + conflict);
        }
    }
}
