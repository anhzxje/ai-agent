package prj.anhzxje.aiagent.dto.chat;

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
}
