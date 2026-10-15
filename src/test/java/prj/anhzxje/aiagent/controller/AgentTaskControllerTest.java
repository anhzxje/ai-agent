package prj.anhzxje.aiagent.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import prj.anhzxje.aiagent.dto.task.AgentTaskRequest;
import prj.anhzxje.aiagent.dto.task.AgentTaskResponse;
import prj.anhzxje.aiagent.enums.JobStatus;
import prj.anhzxje.aiagent.enums.TaskType;
import prj.anhzxje.aiagent.service.AgentTaskService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

/**
 * Kiểm thử Đơn vị cho AgentTaskController (AgentTaskControllerTest):
 * Kiểm tra các endpoint tạo task, lấy task theo ID, lấy danh sách task của tôi,
 * ghim task và lọc task theo dự án.
 */
@ExtendWith(MockitoExtension.class)
class AgentTaskControllerTest {

    @Mock
    private AgentTaskService agentTaskService;

    @InjectMocks
    private AgentTaskController agentTaskController;

    private AgentTaskResponse sampleResponse;
    private AgentTaskRequest sampleRequest;

    @BeforeEach
    void setUp() {
        sampleResponse = AgentTaskResponse.builder()
                .id(10L)
                .projectId(1L)
                .type(TaskType.CODE_REVIEW)
                .status(JobStatus.PENDING)
                .build();

        sampleRequest = new AgentTaskRequest();
        sampleRequest.setProjectId(1L);
        sampleRequest.setType(TaskType.CODE_REVIEW);
    }

    @Test
    void testCreateTask() {
        when(agentTaskService.createTask(sampleRequest)).thenReturn(sampleResponse);

        ResponseEntity<AgentTaskResponse> response = agentTaskController.createTask(sampleRequest);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(10L, response.getBody().getId());
    }

    @Test
    void testGetTask() {
        when(agentTaskService.getTask(10L)).thenReturn(sampleResponse);

        ResponseEntity<AgentTaskResponse> response = agentTaskController.getTask(10L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(10L, response.getBody().getId());
    }

    @Test
    void testGetMyTasks() {
        when(agentTaskService.getMyTasks()).thenReturn(List.of(sampleResponse));

        ResponseEntity<List<AgentTaskResponse>> response = agentTaskController.getMyTasks();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void testGetTasksByProject() {
        when(agentTaskService.getTasksByProject(1L)).thenReturn(List.of(sampleResponse));

        ResponseEntity<List<AgentTaskResponse>> response = agentTaskController.getTasksByProject(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
    }
}
