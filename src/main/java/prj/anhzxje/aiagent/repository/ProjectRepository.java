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

    @EntityGraph(attributePaths = {"user"})
    Optional<Project> findById(Long id);

    @EntityGraph(attributePaths = {"user"})
    List<Project> findByUserId(Long userId);

    @EntityGraph(attributePaths = {"user"})
    List<Project> findByUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserId(Long userId);
}

