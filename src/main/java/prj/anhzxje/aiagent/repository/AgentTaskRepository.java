package prj.anhzxje.aiagent.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import prj.anhzxje.aiagent.entity.AgentTask;
import prj.anhzxje.aiagent.enums.TaskType;

import java.util.List;
import java.util.Optional;

/**
 * Kho lưu trữ Tác vụ AI (AgentTaskRepository):
 * Cung cấp các phương thức truy vấn JPA cho thực thể AgentTask.
 * Tận dụng @EntityGraph(attributePaths = {"project", "user"}) để nạp trước quan hệ,
 * ngăn ngừa lỗi kinh điển N+1 queries trong Hibernate ORM.
 */
@Repository
public interface AgentTaskRepository extends JpaRepository<AgentTask, Long> {

    @EntityGraph(attributePaths = {"project", "user"})
    Optional<AgentTask> findById(Long id);

    @EntityGraph(attributePaths = {"project", "user"})
    List<AgentTask> findByProjectId(Long projectId);

    @EntityGraph(attributePaths = {"project", "user"})
    List<AgentTask> findByUserId(Long userId);

    @EntityGraph(attributePaths = {"project", "user"})
    List<AgentTask> findByUserIdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = {"project", "user"})
    List<AgentTask> findByProjectIdAndType(Long projectId, TaskType type);

    @EntityGraph(attributePaths = {"project", "user"})
    List<AgentTask> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"project", "user"})
    List<AgentTask> findByStatusOrderByCreatedAtDesc(prj.anhzxje.aiagent.enums.JobStatus status);

    @EntityGraph(attributePaths = {"project", "user"})
    List<AgentTask> findByErrorMessageIsNotNullOrderByCreatedAtDesc();

    long countByUserId(Long userId);

    void deleteByUserId(Long userId);
}

