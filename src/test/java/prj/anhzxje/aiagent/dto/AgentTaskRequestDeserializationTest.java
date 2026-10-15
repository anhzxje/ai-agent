package prj.anhzxje.aiagent.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import prj.anhzxje.aiagent.dto.task.AgentTaskRequest;
import prj.anhzxje.aiagent.enums.TaskType;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm thử Giải mã Linh hoạt DTO (AgentTaskRequestDeserializationTest):
 * Kiểm tra FlexibleStringDeserializer có thể tiếp nhận input từ frontend ở cả hai dạng:
 * 1. JSON Object trực tiếp (`{"focus": "all", ...}`)
 * 2. Chuỗi JSON string escape (`"{\"focus\": \"all\"}"`)
 */
class AgentTaskRequestDeserializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testDeserializeInputAsJsonObject() throws Exception {
        String json = """
                {
                    "projectId": 1,
                    "type": "CODE_REVIEW",
                    "input": {
                        "projectPath": "D:/AI Agent/aiagent",
                        "focus": "security"
                    }
                }
                """;

        AgentTaskRequest req = objectMapper.readValue(json, AgentTaskRequest.class);

        assertNotNull(req);
        assertEquals(1L, req.getProjectId());
        assertEquals(TaskType.CODE_REVIEW, req.getType());
        assertNotNull(req.getInput());
        assertTrue(req.getInput().contains("projectPath"));
        assertTrue(req.getInput().contains("security"));
    }

    @Test
    void testDeserializeInputAsString() throws Exception {
        String json = """
                {
                    "projectId": 2,
                    "type": "CODE_REVIEW",
                    "input": "{\\"projectPath\\":\\"/some/path\\"}"
                }
                """;

        AgentTaskRequest req = objectMapper.readValue(json, AgentTaskRequest.class);

        assertNotNull(req);
        assertEquals(2L, req.getProjectId());
        assertEquals("{\"projectPath\":\"/some/path\"}", req.getInput());
    }

    @Test
    void testDeserializeInputAsNull() throws Exception {
        String json = """
                {
                    "projectId": 3,
                    "type": "TEST_GENERATION"
                }
                """;

        AgentTaskRequest req = objectMapper.readValue(json, AgentTaskRequest.class);

        assertNotNull(req);
        assertEquals(3L, req.getProjectId());
        assertNull(req.getInput());
    }
}
