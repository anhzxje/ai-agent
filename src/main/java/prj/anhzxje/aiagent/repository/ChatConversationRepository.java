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

    /** Lấy danh sách cuộc hội thoại của một người dùng cụ thể */
    List<ChatConversation> findByUserId(Long userId);

    /** Tìm cuộc hội thoại theo id và ràng buộc đúng chủ sở hữu (userId) */
    Optional<ChatConversation> findByIdAndUserId(Long id, Long userId);

    /** Xóa toàn bộ cuộc hội thoại của một người dùng cụ thể */
    void deleteByUserId(Long userId);

    /** Lấy danh sách cuộc hội thoại thuộc về một dự án cụ thể */
    List<ChatConversation> findByProjectId(Long projectId);

    /** Lấy danh sách cuộc hội thoại của user thuộc 1 dự án cụ thể, sắp xếp theo lần cập nhật gần nhất */
    List<ChatConversation> findByUserIdAndProjectIdOrderByUpdatedAtDesc(Long userId, Long projectId);

    /** Xóa toàn bộ cuộc hội thoại liên quan đến một dự án cụ thể */
    void deleteByProjectId(Long projectId);
}
