package prj.anhzxje.aiagent.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import prj.anhzxje.aiagent.entity.ChatConversation;

import java.util.List;
import java.util.Optional;

/**
 * Kho lưu trữ Cuộc hội thoại AI (ChatConversationRepository):
 * Quản lý các phiên trao đổi giữa người dùng và tác tử AI.
 */
@Repository
public interface ChatConversationRepository extends JpaRepository<ChatConversation, Long> {
    /** Lấy danh sách cuộc hội thoại của một người dùng cụ thể, sắp xếp theo lần cập nhật gần nhất */
    List<ChatConversation> findByUserIdOrderByUpdatedAtDesc(Long userId);

    /** Tìm cuộc hội thoại theo id và ràng buộc đúng chủ sở hữu (userId) */
    Optional<ChatConversation> findByIdAndUserId(Long id, Long userId);
}
