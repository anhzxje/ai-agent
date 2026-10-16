package prj.anhzxje.aiagent.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import prj.anhzxje.aiagent.entity.User;
import prj.anhzxje.aiagent.enums.Role;

import java.util.Optional;

/**
 * Kho lưu trữ Người dùng (UserRepository):
 * Quản lý các truy vấn thông tin tài khoản người dùng và quản trị viên.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    /** Tìm người dùng theo tên đăng nhập */
    Optional<User> findByUsername(String username);

    /** Tìm người dùng theo địa chỉ email */
    Optional<User> findByEmail(String email);

    /** Kiểm tra trùng lặp tên đăng nhập khi đăng ký tài khoản mới */
    boolean existsByUsername(String username);

    /** Kiểm tra trùng lặp email khi đăng ký tài khoản mới */
    boolean existsByEmail(String email);

    /** Lấy toàn bộ danh sách người dùng sắp xếp theo ngày tạo mới nhất (cho Admin) */
    java.util.List<User> findAllByOrderByCreatedAtDesc();

    /** Đếm số lượng người dùng theo vai trò phân quyền */
    long countByRole(Role role);
}
