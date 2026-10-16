package prj.anhzxje.aiagent.dto.task;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import prj.anhzxje.aiagent.enums.TaskType;

import java.io.IOException;

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
    @JsonDeserialize(using = FlexibleStringDeserializer.class)
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

    /**
     * Bộ giải tuần tự hóa tùy biến (Custom JSON Deserializer):
     * Linh hoạt xử lý trường `input` khi client gửi lên dưới dạng chuỗi String thuần túy
     * HOẶC một đối tượng JSON Object lồng nhau, giúp tránh lỗi parse từ frontend/desktop.
     */
    public static class FlexibleStringDeserializer extends JsonDeserializer<String> {

        /**
         * Chuyển đổi dữ liệu JSON đầu vào thành chuỗi String.
         *
         * @param p    bộ parser JSON của Jackson
         * @param ctxt ngữ cảnh giải tuần tự hóa
         * @return chuỗi String biểu diễn JSON hoặc null nếu rỗng
         * @throws IOException nếu xảy ra lỗi đọc luồng JSON
         */
        @Override
        public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            if (node == null || node.isNull()) {
                return null;
            }
            if (node.isTextual()) {
                return node.asText();
            }
            return node.toString();
        }
    }
}
