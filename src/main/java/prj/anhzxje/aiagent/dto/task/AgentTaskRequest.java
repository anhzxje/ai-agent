package prj.anhzxje.aiagent.dto.task;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import prj.anhzxje.aiagent.enums.TaskType;


/**
 * Đối tượng DTO yêu cầu khởi tạo Tác vụ AI (Agent Task Request):
 * Chứa các tham số đầu vào do người dùng gửi từ giao diện web hoặc ứng dụng desktop:
 * - projectId: ID của dự án mã nguồn trong CSDL
 * - type: Loại tác vụ (CODE_REVIEW, CODE_FIX, TEST_GENERATION)
 * - input: Tham số JSON chi tiết
 * - selectedTools: Danh sách công cụ cho phép Agent gọi (list_directory, glob, file_system, grep)
 * - model: Tên model AI chỉ định (ví dụ: gemini-3.5-flash-lite)
 * - prompt: Lời nhắc bổ sung tùy chỉnh
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AgentTaskRequest {

    @NotNull(message = "Project ID không được để trống")
    private Long projectId;

    @NotNull(message = "Task type không được để trống")
    private TaskType type;

    /**
     * Input dạng JSON string hoặc JSON Object — cấu trúc tùy vào TaskType.
     * Hỗ trợ nhận cả chuỗi String lẫn Object JSON trực tiếp từ frontend/desktop client.
     */
    private String input;

    /**
     * Danh sách tools user chỉ định cho phép Agent sử dụng (ví dụ: ["list_directory", "grep"]).
     */
    private java.util.List<String> selectedTools;

    /**
     * Model Gemini chỉ định (nếu muốn override).
     */
    private String model;

    /**
     * Prompt / Yêu cầu văn bản trực tiếp từ người dùng.
     */
    private String prompt;

    private static final ObjectMapper INPUT_MAPPER = new ObjectMapper();

    /** Shared annotations work with both Jackson 2 and Boot 4's Jackson 3 HTTP converter. */
    @JsonSetter("input")
    public void setInput(Object value) {
        if (value == null || value instanceof String) {
            this.input = (String) value;
        } else if (value instanceof java.util.Map<?, ?>) {
            try {
                this.input = INPUT_MAPPER.writeValueAsString(value);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Input không thể chuyển thành JSON", e);
            }
        } else {
            throw new IllegalArgumentException("Input phải là đối tượng hoặc chuỗi JSON");
        }
    }
}
