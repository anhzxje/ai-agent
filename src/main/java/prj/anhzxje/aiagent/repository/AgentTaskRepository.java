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

    /**
     * Tìm kiếm tác vụ AI theo ID và nạp trước thực thể Project và User để tránh N+1 query.
     *
     * @param id mã định danh tác vụ
     * @return Optional chứa AgentTask nếu tìm thấy
     */
    @EntityGraph(attributePaths = {"project", "user"})
    Optional<AgentTask> findById(Long id);

    /**
     * Lấy danh sách các tác vụ thuộc về một dự án cụ thể.
     *
     * @param projectId mã định danh dự án
     * @return danh sách AgentTask thuộc dự án
     */
    @EntityGraph(attributePaths = {"project", "user"})
    List<AgentTask> findByProjectId(Long projectId);

    /**
     * Lấy danh sách các tác vụ thuộc về một người dùng cụ thể.
     *
     * @param userId mã định danh người dùng
     * @return danh sách AgentTask thuộc người dùng
     */
    @EntityGraph(attributePaths = {"project", "user"})
    List<AgentTask> findByUserId(Long userId);

    /**
     * Lấy danh sách các tác vụ của một người dùng, sắp xếp giảm dần theo thời gian tạo.
     *
     * @param userId mã định danh người dùng
     * @return danh sách AgentTask sắp xếp mới nhất lên đầu
     */
    @EntityGraph(attributePaths = {"project", "user"})
    List<AgentTask> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * Tìm danh sách các tác vụ theo dự án và loại tác vụ (CODE_REVIEW, CODE_FIX, TEST_GENERATION).
     *
     * @param projectId mã định danh dự án
     * @param type      loại tác vụ cần lọc
     * @return danh sách AgentTask phù hợp
     */
    @EntityGraph(attributePaths = {"project", "user"})
    List<AgentTask> findByProjectIdAndType(Long projectId, TaskType type);

    /**
     * Lấy danh sách toàn bộ tác vụ trong hệ thống, sắp xếp theo thời gian tạo mới nhất (cho Admin).
     *
     * @return danh sách toàn bộ AgentTask
     */
    @EntityGraph(attributePaths = {"project", "user"})
    List<AgentTask> findAllByOrderByCreatedAtDesc();

    /**
     * Lọc danh sách tác vụ toàn hệ thống theo trạng thái vòng đời (PENDING, PROCESSING, COMPLETED, ERROR, CANCELLED).
     *
     * @param status trạng thái công việc cần lọc
     * @return danh sách AgentTask tương ứng
     */
    @EntityGraph(attributePaths = {"project", "user"})
    List<AgentTask> findByStatusOrderByCreatedAtDesc(prj.anhzxje.aiagent.enums.JobStatus status);

    /**
     * Lấy danh sách các tác vụ gặp sự cố lỗi (errorMessage != null) để phục vụ Quản trị viên giám sát.
     *
     * @return danh sách AgentTask bị lỗi
     */
    @EntityGraph(attributePaths = {"project", "user"})
    List<AgentTask> findByErrorMessageIsNotNullOrderByCreatedAtDesc();

    /**
     * Đếm tổng số lượng tác vụ đã tạo của một người dùng.
     *
     * @param userId mã định danh người dùng
     * @return tổng số tác vụ
     */
    long countByUserId(Long userId);

    /** Đếm số lượng tác vụ theo trạng thái */
    long countByStatus(prj.anhzxje.aiagent.enums.JobStatus status);

    /** Đếm số lượng tác vụ theo danh sách các trạng thái */
    long countByStatusIn(java.util.Collection<prj.anhzxje.aiagent.enums.JobStatus> statuses);

    /** Đếm số lượng tác vụ theo loại tác vụ */
    long countByType(TaskType type);

    /**
     * Xóa toàn bộ các tác vụ của một người dùng (hỗ trợ xóa cascade tài khoản).
     *
     * @param userId mã định danh người dùng cần xóa tác vụ
     */
    void deleteByUserId(Long userId);

    /**
     * Xóa toàn bộ tác vụ thuộc về một dự án cụ thể.
     *
     * @param projectId mã định danh dự án cần xóa tác vụ
     */
    void deleteByProjectId(Long projectId);
}

