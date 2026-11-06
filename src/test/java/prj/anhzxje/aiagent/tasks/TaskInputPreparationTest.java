package prj.anhzxje.aiagent.tasks;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import prj.anhzxje.aiagent.infrastructure.ai.LlmBudgetProperties;
import prj.anhzxje.aiagent.tasks.AgentTaskRequest;
import prj.anhzxje.aiagent.infrastructure.persistence.entity.Project;
import prj.anhzxje.aiagent.infrastructure.ai.LlmBudgetService;
import prj.anhzxje.aiagent.skills.SkillRegistry;
import prj.anhzxje.aiagent.skills.SkillResolver;
import prj.anhzxje.aiagent.skills.SkillSelection;
import prj.anhzxje.aiagent.skills.SkillTaskSupport;
import prj.anhzxje.aiagent.skills.SkillValidationService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TaskInputPreparationTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final TaskInputPreparation preparation = new TaskInputPreparation(mapper);
    private final Project project = Project.builder().name("App").language("Java").build();

    @Test void mergesExplicitOverridesAndPreservesLegacyFields() throws Exception {
        var request = new AgentTaskRequest();
        request.setInput("{\"focus\":\"security\",\"tools\":[\"glob\"],\"model\":\"old\",\"prompt\":\"old\"}");
        request.setSelectedTools(List.of());
        request.setModel("  chosen  ");
        request.setPrompt("  inspect  ");
        var input = mapper.readTree(preparation.prepare(request, project, null));
        assertEquals("security", input.path("focus").asText());
        assertEquals("glob", input.at("/tools/0").asText());
        assertTrue(input.path("selectedTools").isEmpty());
        assertEquals("chosen", input.path("model").asText());
        assertEquals("inspect", input.path("prompt").asText());
    }

    @Test void blankOverridesPreserveInputAndEmptyInputBecomesObject() throws Exception {
        var request = new AgentTaskRequest();
        assertEquals("{}", preparation.prepare(request, project, null));
        request.setInput("{\"model\":\"old\",\"prompt\":\"original\"}");
        request.setModel(" "); request.setPrompt(" ");
        assertEquals(mapper.readTree(request.getInput()), mapper.readTree(preparation.prepare(request, project, null)));
    }

    @Test void rejectsMalformedOrNonObjectInputAndUnconfiguredSkills() {
        var request = new AgentTaskRequest();
        for (String input : List.of("[1]", "null", "1", "{bad")) {
            request.setInput(input);
            assertThrows(IllegalArgumentException.class, () -> preparation.prepare(request, project, null));
        }
        request.setInput("{\"skills\":[]}");
        assertEquals("Skill Engine chưa được cấu hình", assertThrows(IllegalArgumentException.class,
                () -> preparation.prepare(request, project, null)).getMessage());
    }

    @Test void pinsTrustedSkillsAndReplacesForgedProjectMetadata() throws Exception {
        var validator = new SkillValidationService();
        var support = new SkillTaskSupport(mapper, new SkillResolver(
                new SkillRegistry(mapper, new DefaultResourceLoader(), validator), validator,
                new LlmBudgetService(new LlmBudgetProperties())));
        var request = new AgentTaskRequest();
        request.setInput("{\"_skillProject\":{\"name\":\"forged\"}}");
        request.setSkills(List.of(new SkillSelection("security-review", null)));
        var input = mapper.readTree(preparation.prepare(request, project, support));
        assertEquals("1.0.0", input.at("/skills/0/version").asText());
        assertEquals("App", input.at("/_skillProject/name").asText());
        assertEquals("Java", input.at("/_skillProject/language").asText());
        request.setSkills(List.of());
        input = mapper.readTree(preparation.prepare(request, project, support));
        assertTrue(input.path("skills").isEmpty());
        assertFalse(input.has("_skillProject"));
    }
}
