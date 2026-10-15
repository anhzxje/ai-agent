package prj.anhzxje.aiagent.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import prj.anhzxje.aiagent.entity.AuditLog;

import java.util.List;

/**
 * Kho lưu trữ Nhật ký Kiểm toán (AuditLogRepository):
 * Quản lý các truy vấn lịch sử hoạt động bảo mật hệ thống.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    /** Lấy toàn bộ nhật ký sắp xếp theo thời gian mới nhất */
    List<AuditLog> findAllByOrderByCreatedAtDesc();

    /** Lấy 100 sự kiện kiểm toán gần đây nhất cho giao diện Quản trị viên */
    List<AuditLog> findTop100ByOrderByCreatedAtDesc();
}
