package prj.anhzxje.aiagent.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Đối tượng DTO tóm tắt Cuộc hội thoại (Chat Conversation Response):
 * Trả về danh sách tóm tắt (id, tiêu đề, model, ngày cập nhật) cho thanh sidebar lịch sử chat.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatConversationResponse {
    private Long id;
    private String title;
    private String model;
    private Long projectId;
    private String projectName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
