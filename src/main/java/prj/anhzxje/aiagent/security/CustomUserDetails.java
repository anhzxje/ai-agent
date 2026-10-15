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

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Thêm prefix "ROLE_" cho Spring Security
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true; // Tài khoản không có thời hạn hết hạn
    }

    /**
     * Kiểm tra tài khoản có bị khóa hay không (dựa trên trường isLocked trong MySQL).
     */
    @Override
    public boolean isAccountNonLocked() {
        return !Boolean.TRUE.equals(user.getIsLocked());
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true; // Thông tin đăng nhập không hết hạn
    }

    /**
     * Tài khoản được phép hoạt động khi không ở trạng thái bị khóa.
     */
    @Override
    public boolean isEnabled() {
        return !Boolean.TRUE.equals(user.getIsLocked());
    }
}
