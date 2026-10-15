package prj.anhzxje.aiagent.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import prj.anhzxje.aiagent.dto.project.ProjectRequest;
import prj.anhzxje.aiagent.dto.project.ProjectResponse;
import prj.anhzxje.aiagent.service.ProjectService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

/**
 * Kiểm thử Đơn vị cho ProjectController (ProjectControllerTest):
 * Kiểm tra các endpoint tạo mới, truy xuất danh sách dự án của tôi, xem chi tiết và xóa dự án.
 */
@ExtendWith(MockitoExtension.class)
class ProjectControllerTest {

    @Mock
    private ProjectService projectService;

    @InjectMocks
    private ProjectController projectController;

    private ProjectResponse sampleResponse;
    private ProjectRequest sampleRequest;

    @BeforeEach
    void setUp() {
        sampleResponse = ProjectResponse.builder()
                .id(1L)
                .name("Project 1")
                .path("/workspace/p1")
                .build();

        sampleRequest = new ProjectRequest();
        sampleRequest.setName("Project 1");
        sampleRequest.setPath("/workspace/p1");
    }

    @Test
    void testGetMyProjects() {
        when(projectService.getMyProjects()).thenReturn(List.of(sampleResponse));

        ResponseEntity<List<ProjectResponse>> response = projectController.getMyProjects();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void testGetProject() {
        when(projectService.getProject(1L)).thenReturn(sampleResponse);

        ResponseEntity<ProjectResponse> response = projectController.getProject(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1L, response.getBody().getId());
    }

    @Test
    void testCreateProject() {
        when(projectService.createProject(sampleRequest)).thenReturn(sampleResponse);

        ResponseEntity<ProjectResponse> response = projectController.createProject(sampleRequest);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(1L, response.getBody().getId());
    }

    @Test
    void testUpdateProject() {
        when(projectService.updateProject(1L, sampleRequest)).thenReturn(sampleResponse);

        ResponseEntity<ProjectResponse> response = projectController.updateProject(1L, sampleRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void testDeleteProject() {
        doNothing().when(projectService).deleteProject(1L);

        ResponseEntity<Void> response = projectController.deleteProject(1L);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(projectService).deleteProject(1L);
    }
}
