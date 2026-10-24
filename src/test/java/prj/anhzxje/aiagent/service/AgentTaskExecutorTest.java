package prj.anhzxje.aiagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import prj.anhzxje.aiagent.agent.CodeReviewAgent;
import prj.anhzxje.aiagent.enums.JobStatus;
import prj.anhzxje.aiagent.enums.TaskType;
import prj.anhzxje.aiagent.repository.AgentTaskRepository;

import java.nio.file.Path;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentTaskExecutorTest {
    private static final String REPORT = "{\"summary\":\"OK\",\"issues\":[],\"suggestions\":[]}";
    @Mock CodeReviewAgent codeReviewAgent;
    @Mock AgentTaskRepository agentTaskRepository;
    @Spy ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks AgentTaskExecutor executor;
    @TempDir Path root;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(executor, "allowedPaths", List.of(root.toString()));
        lenient().when(agentTaskRepository.claimTask(eq(1L), anyString())).thenReturn(1);
    }

    @Test
    void completesValidReport() {
        when(codeReviewAgent.reviewPath(anyString(), eq("security"))).thenReturn(REPORT);
        executor.executeTaskAsync(1L, root.toString(), "{\"focus\":\"security\"}", TaskType.CODE_REVIEW);
        verify(agentTaskRepository).finishTask(eq(1L), anyString(), eq(JobStatus.COMPLETED), eq(REPORT), isNull(), any());
    }

    @Test
    void cancelledDeletedOrAlreadyClaimedTasksNeverCallAi() {
        when(agentTaskRepository.claimTask(eq(1L), anyString())).thenReturn(0);
        executor.executeTaskAsync(1L, root.toString(), null, TaskType.CODE_REVIEW);
        verifyNoInteractions(codeReviewAgent);
        verify(agentTaskRepository, never()).finishTask(any(), any(), any(), any(), any(), any());
    }

    @Test
    void emptyAllowlistDeniesAccess() {
        ReflectionTestUtils.setField(executor, "allowedPaths", List.of());
        executor.executeTaskAsync(1L, root.toString(), null, TaskType.CODE_REVIEW);
        verifyNoInteractions(codeReviewAgent);
        verifyFailure();
    }

    @Test
    void rejectsTraversalOutsideProject() {
        executor.executeTaskAsync(1L, root.resolve("..").toString(), null, TaskType.CODE_REVIEW);
        verifyNoInteractions(codeReviewAgent);
        verifyFailure();
    }

    @Test
    void rejectsUnsupportedTaskInsteadOfReviewFallback() {
        executor.executeTaskAsync(1L, root.toString(), null, TaskType.CODE_FIX);
        verifyNoInteractions(codeReviewAgent);
        verifyFailure();
    }

    @Test
    void forwardsUserPrompt() {
        when(codeReviewAgent.reviewPath(anyString(), eq("security\nCheck authentication"))).thenReturn(REPORT);
        executor.executeTaskAsync(1L, root.toString(), "{\"focus\":\"security\",\"prompt\":\"Check authentication\"}", TaskType.CODE_REVIEW);
        verify(codeReviewAgent).reviewPath(anyString(), eq("security\nCheck authentication"));
    }

    @Test
    void preservesExplicitEmptyToolSelection() {
        when(codeReviewAgent.reviewPath(anyString(), isNull(), eq(List.of()), isNull())).thenReturn(REPORT);
        executor.executeTaskAsync(1L, root.toString(), "{\"selectedTools\":[]}", TaskType.CODE_REVIEW);
        verify(codeReviewAgent).reviewPath(anyString(), isNull(), eq(List.of()), isNull());
    }

    @Test
    void invalidJsonInputNeverCallsAi() {
        executor.executeTaskAsync(1L, root.toString(), "{broken", TaskType.CODE_REVIEW);
        verifyNoInteractions(codeReviewAgent);
        verifyFailure();
    }

    @Test
    void malformedReportDoesNotComplete() {
        when(codeReviewAgent.reviewPath(anyString(), any())).thenReturn("{\"summary\":\"missing arrays\"}");
        executor.executeTaskAsync(1L, root.toString(), null, TaskType.CODE_REVIEW);
        verifyFailure();
    }

    @Test
    void truncatesProviderErrorsToDatabaseColumnLength() {
        when(codeReviewAgent.reviewPath(anyString(), any())).thenThrow(new RuntimeException("x".repeat(1500)));
        executor.executeTaskAsync(1L, root.toString(), null, TaskType.CODE_REVIEW);
        verify(agentTaskRepository).finishTask(eq(1L), anyString(), eq(JobStatus.ERROR), isNull(), eq("x".repeat(1000)), any());
    }

    private void verifyFailure() {
        verify(agentTaskRepository).finishTask(eq(1L), anyString(), eq(JobStatus.ERROR), isNull(), anyString(), any());
    }
}
