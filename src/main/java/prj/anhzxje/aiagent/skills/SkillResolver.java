package prj.anhzxje.aiagent.skills;

import org.springframework.stereotype.Service;
import prj.anhzxje.aiagent.infrastructure.ai.LlmBudgetService;
import java.util.*;

@Service
public class SkillResolver {
    private final SkillRegistry registry;
    private final SkillValidationService validation;
    private final LlmBudgetService budget;
    public SkillResolver(SkillRegistry registry, SkillValidationService validation, LlmBudgetService budget) {
        this.registry = registry; this.validation = validation; this.budget = budget;
    }
    public List<SkillDefinition> resolve(List<SkillSelection> requested, String language) {
        if (requested == null || requested.isEmpty()) return List.of(); // Explicit legacy default, never all skills.
        if (requested.size() > 30) throw new IllegalArgumentException("Quá nhiều lựa chọn skill");
        List<SkillDefinition> resolved = requested.stream().map(registry::require).toList();
        validation.validateSelection(resolved, language);
        var unique = new LinkedHashMap<String, SkillDefinition>();
        for (var skill : resolved) unique.putIfAbsent(skill.id(), skill);
        var result = List.copyOf(unique.values());
        budget.validateSkills(result.size(), result.stream().mapToLong(s -> s.content().length()).sum());
        return result;
    }
}
