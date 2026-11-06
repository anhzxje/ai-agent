package prj.anhzxje.aiagent.tasks;

import prj.anhzxje.aiagent.infrastructure.ai.LlmBudgetService;
import prj.anhzxje.aiagent.tasks.AgentTaskExecutor;
import prj.anhzxje.aiagent.tasks.AgentTaskService;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import prj.anhzxje.aiagent.review.CodeReviewAgent;
import prj.anhzxje.aiagent.infrastructure.ai.LlmBudgetProperties;
import prj.anhzxje.aiagent.tasks.AgentTaskRequest;
import prj.anhzxje.aiagent.infrastructure.persistence.entity.AgentTask;
import prj.anhzxje.aiagent.infrastructure.persistence.entity.Project;
import prj.anhzxje.aiagent.infrastructure.persistence.entity.User;
import prj.anhzxje.aiagent.shared.enums.JobStatus;
import prj.anhzxje.aiagent.shared.enums.Role;
import prj.anhzxje.aiagent.shared.enums.TaskType;
import prj.anhzxje.aiagent.infrastructure.persistence.repository.AgentTaskRepository;
import prj.anhzxje.aiagent.infrastructure.persistence.repository.ProjectRepository;
import prj.anhzxje.aiagent.security.CustomUserDetails;
import prj.anhzxje.aiagent.skills.ReviewSkillContext;
import prj.anhzxje.aiagent.skills.SkillRegistry;
import prj.anhzxje.aiagent.skills.SkillResolver;
import prj.anhzxje.aiagent.skills.SkillSelection;
import prj.anhzxje.aiagent.skills.SkillTaskSupport;
import prj.anhzxje.aiagent.skills.SkillValidationService;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SkillTaskPipelineTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final SkillValidationService validator = new SkillValidationService();
    private final SkillTaskSupport support = new SkillTaskSupport(mapper,
        new SkillResolver(new SkillRegistry(mapper, new DefaultResourceLoader(), validator), validator,
            new LlmBudgetService(new LlmBudgetProperties())));
    @TempDir Path root;
    @AfterEach void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @Test void validatesBeforeSaveAndPinsVersionsInPersistedInput() throws Exception {
        var tasks = mock(AgentTaskRepository.class);
        var projects = mock(ProjectRepository.class);
        var worker = mock(AgentTaskExecutor.class);
        var service = new AgentTaskService(tasks, projects, worker);
        ReflectionTestUtils.setField(service, "skillTaskSupport", support);
        User user = User.builder().id(1L).username("user").role(Role.USER).build();
        var principal = new CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        when(projects.findById(1L)).thenReturn(Optional.of(Project.builder().id(1L).name("App").path(root.toString()).language("Java").user(user).build()));
        when(tasks.save(any())).thenAnswer(invocation -> { AgentTask task = invocation.getArgument(0); task.setId(1L); return task; });
        var request = new AgentTaskRequest(); request.setProjectId(1L); request.setType(TaskType.CODE_REVIEW);
        request.setSkills(List.of(new SkillSelection("security-review", null)));
        var response = service.createTask(request);
        assertEquals("1.0.0", mapper.readTree(response.getInput()).at("/skills/0/version").asText());
        request.setSkills(List.of(new SkillSelection("unknown", null)));
        assertThrows(IllegalArgumentException.class, () -> service.createTask(request));
        verify(tasks, times(1)).save(any());
    }
    @Test void workerPersistsServerSkillsRatherThanModelClaimedSkills() throws Exception {
        var tasks = mock(AgentTaskRepository.class);
        var agent = mock(CodeReviewAgent.class);
        when(tasks.claimTask(eq(1L), anyString())).thenReturn(1);
        when(agent.reviewPath(anyString(), eq("security"), isNull(), isNull(), any(ReviewSkillContext.class), any()))
            .thenReturn("{\"summary\":\"OK\",\"issues\":[],\"suggestions\":[],\"appliedSkills\":[{\"id\":\"forged\"}]}");
        var worker = new AgentTaskExecutor(agent, tasks, mapper);
        ReflectionTestUtils.setField(worker, "allowedPaths", List.of(root.toString()));
        ReflectionTestUtils.setField(worker, "skillTaskSupport", support);
        var input = support.prepareInput(mapper.createObjectNode().put("focus", "security"),
            List.of(new SkillSelection("security-review", null)), "Java", "App");
        worker.executeTaskAsync(1L, root.toString(), input.toString(), TaskType.CODE_REVIEW);
        var output = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(tasks).finishTask(eq(1L), anyString(), eq(JobStatus.COMPLETED), output.capture(), isNull(), any());
        var report = mapper.readTree(output.getValue());
        assertEquals("security-review", report.at("/appliedSkills/0/id").asText());
        assertEquals("1.0.0", report.at("/appliedSkills/0/version").asText());
        assertEquals(1, report.path("appliedSkills").size());
    }
    @Test void workerRejectsUnknownVersionWithoutCallingAgent() {
        var tasks = mock(AgentTaskRepository.class); var agent = mock(CodeReviewAgent.class);
        when(tasks.claimTask(eq(1L), anyString())).thenReturn(1);
        var worker = new AgentTaskExecutor(agent, tasks, mapper);
        ReflectionTestUtils.setField(worker, "allowedPaths", List.of(root.toString()));
        ReflectionTestUtils.setField(worker, "skillTaskSupport", support);
        worker.executeTaskAsync(1L, root.toString(), "{\"skills\":[{\"id\":\"security-review\",\"version\":\"99.0.0\"}]}", TaskType.CODE_REVIEW);
        verifyNoInteractions(agent);
        verify(tasks).finishTask(eq(1L), anyString(), eq(JobStatus.ERROR), isNull(), anyString(), any());
    }
}
