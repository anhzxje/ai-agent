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

    /**
     * Xóa toàn bộ dự án thuộc quyền sở hữu của một người dùng cụ thể.
     *
     * @param userId mã định danh người dùng
     */
    void deleteByUserId(Long userId);
}

