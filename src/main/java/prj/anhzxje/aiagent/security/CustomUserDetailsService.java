package prj.anhzxje.aiagent.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import prj.anhzxje.aiagent.entity.User;
import prj.anhzxje.aiagent.repository.UserRepository;

/**
 * Service nạp thông tin người dùng từ CSDL MySQL cho Spring Security.
 * Chuyển đổi entity User thành CustomUserDetails để phục vụ việc xác thực mật khẩu và phân quyền.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Tìm kiếm người dùng theo username.
     * Ném ra UsernameNotFoundException nếu tài khoản không tồn tại trong hệ thống.
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy user: " + username));
        return new CustomUserDetails(user);
    }
}
