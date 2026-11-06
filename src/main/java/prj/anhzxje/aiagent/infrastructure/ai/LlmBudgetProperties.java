package prj.anhzxje.aiagent.infrastructure.ai;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Character limits are deterministic bounds, not a provider token estimate. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "agent.llm-budget")
public class LlmBudgetProperties {
    private int maxPromptChars = 24000;
    private int maxSkillChars = 8000;
    private int maxSkills = 3;
    private int maxToolCalls = 24;
    private int maxModelCalls = 25;
    private int maxContextChars = 96000;
}
