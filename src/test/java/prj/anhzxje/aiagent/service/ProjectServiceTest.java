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
import prj.anhzxje.aiagent.dto.project.ProjectRequest;
import prj.anhzxje.aiagent.dto.project.ProjectResponse;
import prj.anhzxje.aiagent.entity.Project;
import prj.anhzxje.aiagent.entity.User;
import prj.anhzxje.aiagent.enums.Role;
import prj.anhzxje.aiagent.exception.ResourceNotFoundException;
import prj.anhzxje.aiagent.repository.ProjectRepository;
import prj.anhzxje.aiagent.security.CustomUserDetails;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Kiểm thử Đơn vị cho Tầng Dịch vụ Dự án (ProjectServiceTest):
 * Kiểm tra:
 * 1. Đăng ký dự án mới gắn liền với tài khoản người dùng đăng nhập.
 * 2. Cập nhật thông tin dự án (tên, mô tả, đường dẫn, ngôn ngữ).
 * 3. Kiểm tra bảo mật cách ly: không cho phép người dùng đọc/sửa/xóa dự án của người khác.
 * 4. Xóa dự án thành công khi đúng chủ sở hữu.
 */
@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private prj.anhzxje.aiagent.repository.AgentTaskRepository agentTaskRepository;

    @InjectMocks
    private ProjectService projectService;

    private User currentUser;
    private User otherUser;
    private Project sampleProject;

    @BeforeEach
    void setUp() {
        currentUser = User.builder().id(1L).username("testuser").role(Role.USER).build();
        otherUser = User.builder().id(2L).username("otheruser").role(Role.USER).build();

        sampleProject = Project.builder()
                .id(10L)
                .name("Demo Project")
                .description("Demo Description")
                .path("/workspace/demo")
                .language("Java")
                .user(currentUser)
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
    void testGetMyProjects() {
        when(projectRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(sampleProject));

        List<ProjectResponse> result = projectService.getMyProjects();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Demo Project", result.get(0).getName());
        assertEquals("testuser", result.get(0).getOwnerUsername());
    }

    @Test
    void testGetProject_Success() {
        when(projectRepository.findById(10L)).thenReturn(Optional.of(sampleProject));

        ProjectResponse response = projectService.getProject(10L);

        assertNotNull(response);
        assertEquals(10L, response.getId());
        assertEquals("Demo Project", response.getName());
    }

    @Test
    void testGetProject_NotFound() {
        when(projectRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> projectService.getProject(99L));
    }

    @Test
    void testGetProject_IDOR_NotOwner() {
        Project otherProject = Project.builder()
                .id(20L)
                .name("Other Project")
                .user(otherUser)
                .build();

        when(projectRepository.findById(20L)).thenReturn(Optional.of(otherProject));

        assertThrows(ResourceNotFoundException.class, () -> projectService.getProject(20L));
    }

    @Test
    void testCreateProject() {
        ProjectRequest request = new ProjectRequest();
        request.setName("New Project");
        request.setDescription("New Desc");
        request.setPath("/workspace/new");
        request.setLanguage("Java");

        Project savedProject = Project.builder()
                .id(11L)
                .name(request.getName())
                .description(request.getDescription())
                .path(request.getPath())
                .language(request.getLanguage())
                .user(currentUser)
                .build();

        when(projectRepository.save(any(Project.class))).thenReturn(savedProject);

        ProjectResponse response = projectService.createProject(request);

        assertNotNull(response);
        assertEquals(11L, response.getId());
        assertEquals("New Project", response.getName());
    }

    @Test
    void testUpdateProject_Success() {
        ProjectRequest updateRequest = new ProjectRequest();
        updateRequest.setName("Updated Project");
        updateRequest.setDescription("Updated Desc");
        updateRequest.setPath("/workspace/updated");
        updateRequest.setLanguage("Kotlin");

        when(projectRepository.findById(10L)).thenReturn(Optional.of(sampleProject));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectResponse response = projectService.updateProject(10L, updateRequest);

        assertNotNull(response);
        assertEquals("Updated Project", response.getName());
        assertEquals("Updated Desc", response.getDescription());
        assertEquals("/workspace/updated", response.getPath());
    }

    @Test
    void testDeleteProject_Success() {
        when(projectRepository.findById(10L)).thenReturn(Optional.of(sampleProject));
        when(agentTaskRepository.findByProjectId(10L)).thenReturn(List.of());

        assertDoesNotThrow(() -> projectService.deleteProject(10L));
        verify(projectRepository).delete(sampleProject);
    }

    @Test
    void testDeleteProject_WithTasks_CascadeDeleteTasks() {
        prj.anhzxje.aiagent.entity.AgentTask task = prj.anhzxje.aiagent.entity.AgentTask.builder().id(100L).build();
        when(projectRepository.findById(10L)).thenReturn(Optional.of(sampleProject));
        when(agentTaskRepository.findByProjectId(10L)).thenReturn(List.of(task));

        assertDoesNotThrow(() -> projectService.deleteProject(10L));
        verify(agentTaskRepository).deleteAll(List.of(task));
        verify(projectRepository).delete(sampleProject);
    }
}
