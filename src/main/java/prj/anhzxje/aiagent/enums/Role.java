package prj.anhzxje.aiagent.enums;

/**
 * Vai trò người dùng (User Role) trong hệ thống:
 * - USER: Người dùng thông thường (quản lý dự án của mình, chạy task AI, chat, đổi mật khẩu).
 * - ADMIN: Quản trị viên hệ thống (quản lý người dùng, khóa/mở khóa tài khoản, bật/tắt công cụ, đổi key, xem audit log).
 */
public enum Role {
    USER,
    ADMIN
}
