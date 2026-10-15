package prj.anhzxje.aiagent.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import prj.anhzxje.aiagent.entity.ChatMessage;

import java.util.List;

/**
 * Kho lưu trữ Tin nhắn AI Chat (ChatMessageRepository):
 * Quản lý lịch sử tin nhắn trong các phiên hội thoại.
 */
@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    /** Lấy toàn bộ các tin nhắn trong một cuộc hội thoại theo thứ tự thời gian tăng dần */
    List<ChatMessage> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    /** Xóa toàn bộ tin nhắn thuộc một cuộc hội thoại khi phiên bị xóa */
    void deleteByConversationId(Long conversationId);
}
