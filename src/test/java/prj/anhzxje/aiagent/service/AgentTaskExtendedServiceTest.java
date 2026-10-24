package prj.anhzxje.aiagent.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import prj.anhzxje.aiagent.dto.task.AgentTaskResponse;
import prj.anhzxje.aiagent.entity.AgentTask;
import prj.anhzxje.aiagent.entity.Project;
import prj.anhzxje.aiagent.entity.User;
import prj.anhzxje.aiagent.enums.JobStatus;
import prj.anhzxje.aiagent.enums.Role;
import prj.anhzxje.aiagent.enums.TaskType;
import prj.anhzxje.aiagent.exception.ResourceNotFoundException;
import prj.anhzxje.aiagent.repository.AgentTaskRepository;
import prj.anhzxje.aiagent.repository.ProjectRepository;
import prj.anhzxje.aiagent.security.CustomUserDetails;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Kiểm thử Mở rộng Tầng Dịch vụ Tác vụ (AgentTaskExtendedServiceTest):
 * Kiểm tra các tính năng nâng cao:
 * 1. Hủy tác vụ đang chạy (`cancelTask`).
 * 2. Thực thi lại tác vụ thất bại (`retryTask`).
 * 3. Xóa một tác vụ hoặc xóa toàn bộ lịch sử tác vụ của người dùng (`deleteTask`, `deleteAllMyTasks`).
 */
@ExtendWith(MockitoExtension.class)
class AgentTaskExtendedServiceTest {

    @Mock
    private AgentTaskRepository agentTaskRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private AgentTaskExecutor agentTaskExecutor;

    @Mock
    private AuditLogService auditLogService;

    private AgentTaskService agentTaskService;

    private User currentUser;
    private User otherUser;
    private Project sampleProject;
    private AgentTask myTask;
    private AgentTask otherTask;

    @BeforeEach
    void setUp() {
        currentUser = User.builder().id(1L).username("alice").role(Role.USER).build();
        otherUser = User.builder().id(2L).username("bob").role(Role.USER).build();

        sampleProject = Project.builder()
                .id(10L)
                .name("Alice Project")
                .path("/path/alice")
                .user(currentUser)
                .build();

        myTask = AgentTask.builder()
                .id(100L)
                .user(currentUser)
                .project(sampleProject)
                .type(TaskType.CODE_REVIEW)
                .status(JobStatus.PROCESSING)
                .createdAt(LocalDateTime.now())
                .build();

        otherTask = AgentTask.builder()
                .id(200L)
                .user(otherUser)
                .project(sampleProject)
                .type(TaskType.CODE_REVIEW)
                .status(JobStatus.PROCESSING)
                .createdAt(LocalDateTime.now())
                .build();

        CustomUserDetails userDetails = new CustomUserDetails(currentUser);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));

        agentTaskService = new AgentTaskService(
                agentTaskRepository, projectRepository, agentTaskExecutor, auditLogService, null);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void testCancelTask_Success() {
        when(agentTaskRepository.findById(100L)).thenReturn(Optional.of(myTask));
        when(agentTaskRepository.cancelActiveTask(eq(100L), any())).thenAnswer(i -> {
            myTask.setStatus(JobStatus.CANCELLED);
            return 1;
        });

        AgentTaskResponse res = agentTaskService.cancelTask(100L);

        assertNotNull(res);
        assertEquals(JobStatus.CANCELLED, res.getStatus());
        verify(auditLogService, times(1)).log(eq("alice"), eq("CANCEL_TASK"), any());
    }

    @Test
    void testCancelTask_CannotCancelCompletedTask() {
        myTask.setStatus(JobStatus.COMPLETED);
        when(agentTaskRepository.findById(100L)).thenReturn(Optional.of(myTask));

        assertThrows(IllegalStateException.class, () -> agentTaskService.cancelTask(100L));
        verify(agentTaskRepository, never()).save(any());
    }

    @Test
    void testUserCannotCancelOrViewOtherUserTask() {
        when(agentTaskRepository.findById(200L)).thenReturn(Optional.of(otherTask));

        // When Alice tries to access Bob's task, backend rejects with ResourceNotFoundException
        assertThrows(ResourceNotFoundException.class, () -> agentTaskService.cancelTask(200L));
        assertThrows(ResourceNotFoundException.class, () -> agentTaskService.getTask(200L));
        assertThrows(ResourceNotFoundException.class, () -> agentTaskService.deleteTask(200L));
    }

    @Test
    void testRetryTask_Success() {
        myTask.setStatus(JobStatus.ERROR);
        myTask.setErrorMessage("Some error");
        when(agentTaskRepository.findById(100L)).thenReturn(Optional.of(myTask));
        when(agentTaskRepository.resetForRetry(100L)).thenAnswer(i -> {
            myTask.setStatus(JobStatus.PENDING);
            myTask.setErrorMessage(null);
            return 1;
        });

        AgentTaskResponse res = agentTaskService.retryTask(100L);

        assertEquals(JobStatus.PENDING, res.getStatus());
        assertNull(res.getErrorMessage());
        verify(agentTaskExecutor, times(1)).executeTaskAsync(eq(100L), any(), any(), eq(TaskType.CODE_REVIEW));
        verify(auditLogService, times(1)).log(eq("alice"), eq("RETRY_TASK"), any());
    }
}
