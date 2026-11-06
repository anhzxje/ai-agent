package prj.anhzxje.aiagent.chat;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Đối tượng DTO gửi tin nhắn Chat (Chat Message Request):
 * Gửi nội dung tin nhắn của người dùng kèm mã hội thoại (tùy chọn) và model AI muốn sử dụng.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageRequest {

    @NotBlank(message = "Nội dung tin nhắn không được để trống")
    private String message;

    private Long conversationId;
    private Long projectId;
    private String model;
    private Long reviewTaskId;
    private String requestId;
    private java.util.List<String> selectedTools;
    private java.util.List<prj.anhzxje.aiagent.skills.SkillSelection> skills;

    public ChatMessageRequest(String message, Long conversationId, Long projectId, String model) {
        this.message = message; this.conversationId = conversationId; this.projectId = projectId; this.model = model;
    }
}
