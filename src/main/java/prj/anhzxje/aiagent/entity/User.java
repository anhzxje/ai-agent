package prj.anhzxje.aiagent.entity;

import jakarta.persistence.*;
import lombok.*;
import prj.anhzxje.aiagent.enums.Role;

import java.time.LocalDateTime;

/**
 * Thực thể Người dùng (User Account):
 * Lưu trữ thông tin tài khoản đăng nhập, email, mật khẩu mã hóa BCrypt,
 * vai trò phân quyền (USER / ADMIN) và trạng thái tài khoản (bị khóa hay đang hoạt động).
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Role role = Role.USER;

    @Column(name = "is_locked")
    @Builder.Default
    private Boolean isLocked = false;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
