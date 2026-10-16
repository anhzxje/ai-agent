package prj.anhzxje.aiagent.security;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import prj.anhzxje.aiagent.entity.User;

import java.util.Collection;
import java.util.List;

/**
 * Adapter giữa domain entity User và Spring Security UserDetails.
 * User entity giữ nguyên tính thuần túy (pure domain), không phụ thuộc Security.
 */
@Getter
@AllArgsConstructor
public class CustomUserDetails implements UserDetails {

    private final User user;

    /**
     * Trả về danh sách quyền hạn (Authorities) của người dùng.
     * Tự động thêm tiền tố "ROLE_" (ví dụ: "ROLE_USER", "ROLE_ADMIN") để tương thích với Spring Security.
     *
     * @return tập hợp danh sách GrantedAuthority
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    /**
     * Trả về mật khẩu đã được băm (hash BCrypt) của người dùng.
     *
     * @return chuỗi mật khẩu mã hóa
     */
    @Override
    public String getPassword() {
        return user.getPassword();
    }

    /**
     * Trả về tên đăng nhập (username) của người dùng.
     *
     * @return tên đăng nhập
     */
    @Override
    public String getUsername() {
        return user.getUsername();
    }

    /**
     * Kiểm tra tài khoản có bị hết hạn hay không.
     *
     * @return luôn trả về true (hệ thống không áp dụng hết hạn tài khoản)
     */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * Kiểm tra tài khoản có bị khóa hay không (dựa trên trường isLocked trong MySQL).
     *
     * @return true nếu tài khoản KHÔNG bị khóa, false nếu bị khóa
     */
    @Override
    public boolean isAccountNonLocked() {
        return !Boolean.TRUE.equals(user.getIsLocked());
    }

    /**
     * Kiểm tra thông tin đăng nhập (mật khẩu) có bị hết hạn hay không.
     *
     * @return luôn trả về true (không áp dụng hết hạn mật khẩu)
     */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * Kiểm tra tài khoản có đang được kích hoạt để sử dụng hệ thống hay không.
     *
     * @return true nếu tài khoản không bị khóa (isLocked = false), false nếu bị khóa
     */
    @Override
    public boolean isEnabled() {
        return !Boolean.TRUE.equals(user.getIsLocked());
    }
}
