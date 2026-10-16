package prj.anhzxje.aiagent.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import prj.anhzxje.aiagent.entity.Project;

import java.util.List;
import java.util.Optional;

/**
 * Kho lưu trữ Dự án Mã nguồn (ProjectRepository):
 * Quản lý các dự án được đăng ký bởi từng người dùng.
 * Áp dụng @EntityGraph(attributePaths = {"user"}) để giải quyết triệt để N+1 query.
 */
@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    /**
     * Tìm dự án theo ID và nạp trước thông tin chủ sở hữu (User) để tránh N+1 query.
     *
     * @param id mã định danh dự án
     * @return Optional chứa Project nếu tìm thấy
     */
    @EntityGraph(attributePaths = {"user"})
    Optional<Project> findById(Long id);

    /**
     * Lấy danh sách toàn bộ dự án thuộc về một người dùng cụ thể.
     *
     * @param userId mã định danh người dùng
     * @return danh sách Project thuộc người dùng
     */
    @EntityGraph(attributePaths = {"user"})
    List<Project> findByUserId(Long userId);

    /**
     * Lấy danh sách dự án của một người dùng, sắp xếp giảm dần theo thời gian tạo (mới nhất lên đầu).
     *
     * @param userId mã định danh người dùng
     * @return danh sách Project sắp xếp theo ngày tạo
     */
    @EntityGraph(attributePaths = {"user"})
    List<Project> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * Đếm tổng số lượng dự án thuộc quyền sở hữu của một người dùng cụ thể.
     *
     * @param userId mã định danh người dùng
     * @return tổng số lượng dự án
     */
    long countByUserId(Long userId);

    /** Đếm tổng số lượng dự án gom nhóm theo user_id để giải quyết N+1 query */
    @org.springframework.data.jpa.repository.Query("SELECT p.user.id, COUNT(p) FROM Project p GROUP BY p.user.id")
    List<Object[]> countProjectsGroupedByUser();

    /**
     * Xóa toàn bộ dự án thuộc quyền sở hữu của một người dùng cụ thể.
     *
     * @param userId mã định danh người dùng
     */
    void deleteByUserId(Long userId);

    /** Kiểm tra xem user đã có dự án nào cùng tên chưa (không phân biệt hoa thường) */
    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);

    /** Kiểm tra xem user đã có dự án nào cùng đường dẫn chưa */
    boolean existsByUserIdAndPath(Long userId, String path);

    /** Kiểm tra xem user có dự án nào khác cùng tên (loại trừ chính nó khi update) */
    boolean existsByUserIdAndNameIgnoreCaseAndIdNot(Long userId, String name, Long id);

    /** Kiểm tra xem user có dự án nào khác cùng đường dẫn (loại trừ chính nó khi update) */
    boolean existsByUserIdAndPathAndIdNot(Long userId, String path, Long id);
}

