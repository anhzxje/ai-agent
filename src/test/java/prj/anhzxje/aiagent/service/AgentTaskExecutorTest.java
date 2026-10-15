package prj.anhzxje.aiagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import prj.anhzxje.aiagent.agent.CodeReviewAgent;
import prj.anhzxje.aiagent.entity.AgentTask;
import prj.anhzxje.aiagent.enums.JobStatus;
import prj.anhzxje.aiagent.enums.TaskType;
import prj.anhzxje.aiagent.repository.AgentTaskRepository;

import java.io.File;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Kiểm thử Bộ Thực thi Tác vụ Ngầm (AgentTaskExecutorTest):
 * Kiểm tra:
 * 1. Cơ chế thực thi bất đồng bộ trên luồng riêng.
 * 2. Cập nhật trạng thái PENDING -> PROCESSING -> COMPLETED/ERROR.
 * 3. Kiểm tra tính năng Sandbox an toàn (chặn đường dẫn không nằm trong allowed-paths).
 */
@ExtendWith(MockitoExtension.class)
class AgentTaskExecutorTest {

    @Mock
    private CodeReviewAgent codeReviewAgent;

    @Mock
    private AgentTaskRepository agentTaskRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AgentTaskExecutor agentTaskExecutor;

    private AgentTask task;
    private String allowedDir;

    @BeforeEach
    void setUp() {
        allowedDir = new File(".").getAbsolutePath();
        ReflectionTestUtils.setField(agentTaskExecutor, "allowedPaths", List.of(allowedDir));

        task = AgentTask.builder()
                .id(1L)
                .status(JobStatus.PENDING)
                .build();

        lenient().when(agentTaskRepository.findById(1L)).thenReturn(Optional.of(task));
    }

    @Test
    void testExecuteTaskAsync_Success() {
        when(codeReviewAgent.reviewPath(anyString(), any())).thenReturn("{\"summary\":\"OK\"}");

        agentTaskExecutor.executeTaskAsync(1L, allowedDir, "{\"focus\":\"security\"}", TaskType.CODE_REVIEW);

        assertEquals(JobStatus.COMPLETED, task.getStatus());
        assertEquals("{\"summary\":\"OK\"}", task.getOutput());
    }

    @Test
    void testExecuteTaskAsync_PathTraversal_Fails() {
        String forbiddenPath = allowedDir + "/../../etc/passwd";

        agentTaskExecutor.executeTaskAsync(1L, forbiddenPath, null, TaskType.CODE_REVIEW);

        assertEquals(JobStatus.ERROR, task.getStatus());
    }

    @Test
    void testExecuteTaskAsync_EmptyPath_Fails() {
        agentTaskExecutor.executeTaskAsync(1L, "", null, TaskType.CODE_REVIEW);

        assertEquals(JobStatus.ERROR, task.getStatus());
    }

    @Test
    void testExecuteTaskAsync_CodeExplainAndTestGeneration() {
        when(codeReviewAgent.reviewPath(anyString(), any())).thenReturn("{\"summary\":\"OK\"}");

        agentTaskExecutor.executeTaskAsync(1L, allowedDir, null, TaskType.CODE_EXPLAIN);
        assertEquals(JobStatus.COMPLETED, task.getStatus());

        agentTaskExecutor.executeTaskAsync(1L, allowedDir, null, TaskType.TEST_GENERATION);
        assertEquals(JobStatus.COMPLETED, task.getStatus());
    }

    @Test
    void testExecuteTaskAsync_AllowedPathsEmpty_AllowsAll() {
        ReflectionTestUtils.setField(agentTaskExecutor, "allowedPaths", List.of());
        when(codeReviewAgent.reviewPath(anyString(), any())).thenReturn("{\"summary\":\"OK\"}");

        agentTaskExecutor.executeTaskAsync(1L, allowedDir, null, TaskType.CODE_REVIEW);

        assertEquals(JobStatus.COMPLETED, task.getStatus());
    }

    @Test
    void testExecuteTaskAsync_WithValidOverridePathInJson() {
        when(codeReviewAgent.reviewPath(anyString(), any())).thenReturn("{\"summary\":\"OK\"}");

        String json = "{\"projectPath\":\"" + allowedDir.replace("\\", "\\\\") + "\",\"focus\":\"performance\"}";
        agentTaskExecutor.executeTaskAsync(1L, allowedDir, json, TaskType.CODE_REVIEW);

        assertEquals(JobStatus.COMPLETED, task.getStatus());
    }

    @Test
    void testExecuteTaskAsync_WithInvalidOverridePathInJson() {
        when(codeReviewAgent.reviewPath(anyString(), any())).thenReturn("{\"summary\":\"OK\"}");

        String json = "{\"projectPath\":\"/forbidden/path/123\",\"focus\":\"performance\"}";
        agentTaskExecutor.executeTaskAsync(1L, allowedDir, json, TaskType.CODE_REVIEW);

        assertEquals(JobStatus.COMPLETED, task.getStatus());
    }
}
