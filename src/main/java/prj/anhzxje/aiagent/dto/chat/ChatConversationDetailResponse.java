package prj.anhzxje.aiagent.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Đối tượng DTO phản hồi Chi tiết Cuộc hội thoại (Chat Conversation Detail Response):
 * Trả về thông tin phiên chat kèm toàn bộ danh sách tin nhắn giữa user và AI.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatConversationDetailResponse {
    private Long id;
    private String title;
    private String model;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<ChatMessageResponse> messages;
}
