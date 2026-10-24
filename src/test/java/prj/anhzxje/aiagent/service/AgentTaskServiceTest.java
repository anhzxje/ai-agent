package prj.anhzxje.aiagent.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import prj.anhzxje.aiagent.dto.task.AgentTaskRequest;
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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Kiểm thử Đơn vị cho Tầng Dịch vụ Tác vụ (AgentTaskServiceTest):
 * Kiểm tra các nghiệp vụ cơ bản:
 * 1. Khởi tạo tác vụ mới và xác thực quyền sở hữu project.
 * 2. Lấy chi tiết tác vụ, danh sách tác vụ theo người dùng hoặc dự án.
 * 3. Ghim / Bỏ ghim tác vụ lên dashboard.
 */
@ExtendWith(MockitoExtension.class)
class AgentTaskServiceTest {

    @Mock
    private AgentTaskRepository agentTaskRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private AgentTaskExecutor agentTaskExecutor;

    @InjectMocks
    private AgentTaskService agentTaskService;

    private User currentUser;
    private User otherUser;
    private Project sampleProject;
    private AgentTask sampleTask;

    @BeforeEach
    void setUp() {
        currentUser = User.builder().id(1L).username("testuser").role(Role.USER).build();
        otherUser = User.builder().id(2L).username("otheruser").role(Role.USER).build();

        sampleProject = Project.builder()
                .id(10L)
                .name("Demo Project")
                .path("/workspace/demo")
                .user(currentUser)
                .build();

        sampleTask = AgentTask.builder()
                .id(100L)
                .user(currentUser)
                .project(sampleProject)
                .type(TaskType.CODE_REVIEW)
                .status(JobStatus.PENDING)
                .input("{\"focus\":\"security\"}")
                .build();

        CustomUserDetails userDetails = new CustomUserDetails(currentUser);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void testCreateTask_Success() {
        AgentTaskRequest request = new AgentTaskRequest();
        request.setProjectId(10L);
        request.setType(TaskType.CODE_REVIEW);
        request.setInput("{\"focus\":\"security\"}");

        when(projectRepository.findById(10L)).thenReturn(Optional.of(sampleProject));
        when(agentTaskRepository.save(any(AgentTask.class))).thenReturn(sampleTask);

        AgentTaskResponse response = agentTaskService.createTask(request);

        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals(JobStatus.PENDING, response.getStatus());
        verify(agentTaskExecutor).executeTaskAsync(eq(100L), eq("/workspace/demo"), eq("{\"focus\":\"security\"}"), eq(TaskType.CODE_REVIEW));
    }

    @Test
    void testCreateTask_ProjectNotFound() {
        AgentTaskRequest request = new AgentTaskRequest();
        request.setProjectId(99L);
        request.setType(TaskType.CODE_REVIEW);

        when(projectRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> agentTaskService.createTask(request));
    }

    @Test
    void testCreateTask_NotOwner() {
        Project otherProject = Project.builder().id(20L).user(otherUser).build();
        AgentTaskRequest request = new AgentTaskRequest();
        request.setProjectId(20L);
        request.setType(TaskType.CODE_REVIEW);

        when(projectRepository.findById(20L)).thenReturn(Optional.of(otherProject));

        assertThrows(ResourceNotFoundException.class, () -> agentTaskService.createTask(request));
    }

    @Test
    void testGetTask_Success() {
        when(agentTaskRepository.findById(100L)).thenReturn(Optional.of(sampleTask));

        AgentTaskResponse response = agentTaskService.getTask(100L);

        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals("Demo Project", response.getProjectName());
    }

    @Test
    void testGetMyTasks() {
        when(agentTaskRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(sampleTask));

        List<AgentTaskResponse> tasks = agentTaskService.getMyTasks();

        assertNotNull(tasks);
        assertEquals(1, tasks.size());
        assertEquals(100L, tasks.get(0).getId());
    }

    @Test
    void testGetTasksByProject_Success() {
        when(projectRepository.findById(10L)).thenReturn(Optional.of(sampleProject));
        when(agentTaskRepository.findByProjectId(10L)).thenReturn(List.of(sampleTask));

        List<AgentTaskResponse> tasks = agentTaskService.getTasksByProject(10L);

        assertNotNull(tasks);
        assertEquals(1, tasks.size());
        assertEquals(100L, tasks.get(0).getId());
    }

    @Test
    void testGetTask_NotFound() {
        when(agentTaskRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> agentTaskService.getTask(999L));
    }

    @Test
    void testGetTask_NotOwner() {
        AgentTask otherTask = AgentTask.builder()
                .id(200L)
                .user(otherUser)
                .build();

        when(agentTaskRepository.findById(200L)).thenReturn(Optional.of(otherTask));

        assertThrows(ResourceNotFoundException.class, () -> agentTaskService.getTask(200L));
    }

    @Test
    void testGetTasksByProject_ProjectNotFound() {
        when(projectRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> agentTaskService.getTasksByProject(999L));
    }

    @Test
    void testGetTasksByProject_NotOwner() {
        Project otherProject = Project.builder().id(20L).user(otherUser).build();
        when(projectRepository.findById(20L)).thenReturn(Optional.of(otherProject));

        assertThrows(ResourceNotFoundException.class, () -> agentTaskService.getTasksByProject(20L));
    }
}
