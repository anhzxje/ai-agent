package prj.anhzxje.aiagent.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Đối tượng DTO phản hồi Tin nhắn Chat (Chat Message Response):
 * Trả về nội dung tin nhắn, người gửi (USER hoặc AGENT) và thời điểm gửi.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageResponse {
    private Long id;
    private Long conversationId;
    private String sender;
    private String content;
    private LocalDateTime createdAt;
}
