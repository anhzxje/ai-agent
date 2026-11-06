package prj.anhzxje.aiagent.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import prj.anhzxje.aiagent.infrastructure.ai.SystemConfigService;
import prj.anhzxje.aiagent.infrastructure.persistence.entity.*;
import prj.anhzxje.aiagent.infrastructure.persistence.repository.*;
import prj.anhzxje.aiagent.infrastructure.tools.ToolManagerService;
import prj.anhzxje.aiagent.shared.enums.JobStatus;
import prj.anhzxje.aiagent.shared.exception.ResourceNotFoundException;
import prj.anhzxje.aiagent.tasks.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChatReviewServiceTest {
    ChatConversationRepository conversations = mock(ChatConversationRepository.class);
    ChatReviewLinkRepository links = mock(ChatReviewLinkRepository.class);
    AgentTaskRepository tasks = mock(AgentTaskRepository.class);
    AgentTaskService taskService = mock(AgentTaskService.class);
    SystemConfigService config = mock(SystemConfigService.class);
    ToolManagerService tools = mock(ToolManagerService.class);
    ChatReviewService service = new ChatReviewService(conversations, links, tasks, taskService, config, tools, new ObjectMapper());
    User user = User.builder().id(1L).build();
    Project project = Project.builder().id(2L).user(user).build();
    ChatConversation conversation = ChatConversation.builder().id(3L).user(user).project(project).model("allowed").build();
    ChatReviewRequest request = new ChatReviewRequest();

    @BeforeEach void setup() {
        request.setMessage("/review security auth"); request.setRequestId("request-1");
    }
    @Test void submitCreatesOneOwnedTaskAndCarriesSelection() {
        when(conversations.lockOwned(3L,1L)).thenReturn(Optional.of(conversation));
        when(config.getAllowedModels()).thenReturn(List.of("allowed"));
        when(taskService.createTask(any())).thenReturn(AgentTaskResponse.builder().id(4L).build());
        when(tasks.getReferenceById(4L)).thenReturn(AgentTask.builder().id(4L).build());
        request.setSelectedTools(List.of());
        request.setSkills(List.of(new prj.anhzxje.aiagent.skills.SkillSelection("security-review", "1.0.0")));
        assertEquals(4L, service.submit(3L, request, user).getId());
        var capture = ArgumentCaptor.forClass(AgentTaskRequest.class);
        verify(taskService).createTask(capture.capture());
        assertEquals(2L, capture.getValue().getProjectId());
        assertTrue(capture.getValue().getInput().contains("security"));
        assertEquals(request.getSkills(), capture.getValue().getSkills());
        assertEquals("allowed", capture.getValue().getModel());
        verify(links).save(any(ChatReviewLink.class));
    }
    @Test void duplicateKeyReturnsExistingTaskWithoutAnotherCreation() {
        when(conversations.lockOwned(3L,1L)).thenReturn(Optional.of(conversation));
        when(links.findByConversationIdAndRequestId(3L,"request-1")).thenReturn(Optional.of(ChatReviewLink.builder().task(AgentTask.builder().id(4L).build()).requestFingerprint(service.fingerprint(request)).build()));
        when(taskService.getTask(4L)).thenReturn(AgentTaskResponse.builder().id(4L).build());
        assertEquals(4L, service.submit(3L, request, user).getId());
        verify(taskService, never()).createTask(any());
    }
    @Test void reusingKeyForDifferentPayloadIsRejected() {
        when(conversations.lockOwned(3L,1L)).thenReturn(Optional.of(conversation));
        when(config.getAllowedModels()).thenReturn(List.of("allowed"));
        when(taskService.createTask(any())).thenReturn(AgentTaskResponse.builder().id(4L).build());
        when(tasks.getReferenceById(4L)).thenReturn(AgentTask.builder().id(4L).build());
        service.submit(3L, request, user);
        var capture = ArgumentCaptor.forClass(ChatReviewLink.class);
        verify(links).save(capture.capture());
        when(links.findByConversationIdAndRequestId(3L,"request-1")).thenReturn(Optional.of(capture.getValue()));
        request.setMessage("/review performance");
        assertThrows(IllegalArgumentException.class, () -> service.submit(3L, request, user));
        verify(taskService, times(1)).createTask(any());
    }
    @Test void crossUserConversationCannotSubmitOrReadReport() {
        assertThrows(ResourceNotFoundException.class, () -> service.submit(3L, request, user));
        assertThrows(ResourceNotFoundException.class, () -> service.reportContext(3L, 4L, user));
        verifyNoInteractions(taskService);
    }
    @Test void followUpUsesLinkedCompletedReportAndExcludesOtherFields() {
        when(conversations.findByIdAndUserId(3L,1L)).thenReturn(Optional.of(conversation));
        AgentTask task = AgentTask.builder().id(4L).user(user).project(project).status(JobStatus.COMPLETED)
                .output("{\"summary\":\"reviewed\",\"issues\":[],\"toolTranscript\":\"secret transcript\"}").build();
        when(links.findByConversationIdAndTaskId(3L,4L)).thenReturn(Optional.of(ChatReviewLink.builder().task(task).build()));
        String context = service.reportContext(3L,4L,user);
        assertTrue(context.contains("reviewed"));
        assertFalse(context.contains("secret transcript"));
        task.setStatus(JobStatus.ERROR);
        assertThrows(IllegalStateException.class, () -> service.reportContext(3L,4L,user));
    }
}
