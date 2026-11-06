package prj.anhzxje.aiagent.infrastructure.persistence.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import prj.anhzxje.aiagent.infrastructure.persistence.entity.ChatReviewLink;

public interface ChatReviewLinkRepository extends JpaRepository<ChatReviewLink, Long> {
    Optional<ChatReviewLink> findByConversationIdAndRequestId(Long conversationId, String requestId);
    Optional<ChatReviewLink> findByConversationIdAndTaskId(Long conversationId, Long taskId);
    List<ChatReviewLink> findByConversationIdOrderByIdAsc(Long conversationId);
}
