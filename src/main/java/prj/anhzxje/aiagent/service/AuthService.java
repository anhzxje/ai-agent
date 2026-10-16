package prj.anhzxje.aiagent.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import prj.anhzxje.aiagent.dto.auth.AuthResponse;
import prj.anhzxje.aiagent.dto.auth.LoginRequest;
import prj.anhzxje.aiagent.dto.auth.RegisterRequest;
import prj.anhzxje.aiagent.entity.User;
import prj.anhzxje.aiagent.enums.Role;
import prj.anhzxje.aiagent.repository.UserRepository;
import prj.anhzxje.aiagent.security.CustomUserDetails;
import prj.anhzxje.aiagent.security.JwtTokenProvider;

import java.util.Map;

/**
 * ============================================================================
 * AUTH SERVICE - DỊCH VỤ XÁC THỰC VÀ PHÂN QUYỀN HỆ THỐNG
 * ============================================================================
 * Chịu trách nhiệm toàn bộ logic nghiệp vụ:
 * 1. Đăng ký tài khoản thành viên mới: kiểm tra tính duy nhất, mã hóa mật khẩu, sinh JWT.
 * 2. Đăng nhập: kiểm tra trạng thái khóa tài khoản, xác thực thông tin, cấp phát JWT Bearer Token.
 * 3. Đăng xuất: ghi vết kiểm toán (Audit Log) phiên làm việc.
 * ============================================================================
 */
@Slf4j
@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuditLogService auditLogService;

    public AuthService(AuthenticationManager authenticationManager,
                       UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider) {
        this(authenticationManager, userRepository, passwordEncoder, jwtTokenProvider, null);
    }

    @Autowired
    public AuthService(AuthenticationManager authenticationManager,
                       UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       @Autowired(required = false) AuditLogService auditLogService) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.auditLogService = auditLogService;
    }

    /**
     * Nghiệp vụ đăng ký tài khoản thành viên mới.
     *
     * @param request dữ liệu đăng ký gồm username, email, raw password
     * @return AuthResponse chứa token JWT hoặc thông báo lỗi nếu username/email bị trùng
     */
    @Transactional
    public AuthResponse registerUser(RegisterRequest request) {
        log.info("Xử lý đăng ký tài khoản mới: {}", request.getUsername());

        // Kiểm tra username đã tồn tại chưa
        if (userRepository.existsByUsername(request.getUsername())) {
            return AuthResponse.builder()
                    .message("Username đã tồn tại")
                    .build();
        }

        // Kiểm tra email đã được đăng ký tài khoản khác chưa
        if (userRepository.existsByEmail(request.getEmail())) {
            return AuthResponse.builder()
                    .message("Email đã được sử dụng")
                    .build();
        }

        // Băm mật khẩu bằng BCryptPasswordEncoder và tạo thực thể User
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .isLocked(false)
                .build();

        userRepository.save(user);

        // Tạo chuỗi JWT Token
        String token = jwtTokenProvider.generateToken(user.getUsername(), user.getRole().name());

        if (auditLogService != null) {
            auditLogService.log(user.getUsername(), "REGISTER", "Đăng ký tài khoản mới thành công");
        }
        log.info("Đăng ký thành công cho user: {}", user.getUsername());

        return AuthResponse.builder()
                .token(token)
                .username(user.getUsername())
                .role(user.getRole().name())
                .message("Đăng ký thành công")
                .build();
    }

    /**
     * Nghiệp vụ xác thực thông tin đăng nhập và cấp phát JWT.
     *
     * @param request thông tin đăng nhập gồm username và password
     * @return AuthResponse chứa token JWT và thông tin tài khoản
     * @throws LockedException nếu tài khoản bị khóa
     */
    public AuthResponse loginUser(LoginRequest request) {
        log.info("Xử lý đăng nhập cho user: {}", request.getUsername());

        // Kiểm tra sớm trạng thái khóa tài khoản (isLocked)
        var userOpt = userRepository.findByUsername(request.getUsername());
        if (userOpt.isPresent() && Boolean.TRUE.equals(userOpt.get().getIsLocked())) {
            if (auditLogService != null) {
                auditLogService.log(request.getUsername(), "LOGIN_FAILED", "Đăng nhập thất bại: Tài khoản đang bị khóa");
            }
            return AuthResponse.builder()
                    .message("Tài khoản của bạn đã bị khóa bởi Quản trị viên")
                    .build();
        }

        try {
            // Xác thực thông tin qua Spring Security AuthenticationManager
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()));

            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            User user = userDetails.getUser();

            String token = jwtTokenProvider.generateToken(user.getUsername(), user.getRole().name());

            if (auditLogService != null) {
                auditLogService.log(user.getUsername(), "LOGIN", "Đăng nhập thành công vào hệ thống");
            }
            log.info("Đăng nhập thành công cho user: {}", user.getUsername());

            return AuthResponse.builder()
                    .token(token)
                    .username(user.getUsername())
                    .role(user.getRole().name())
                    .message("Đăng nhập thành công")
                    .build();
        } catch (LockedException e) {
            if (auditLogService != null) {
                auditLogService.log(request.getUsername(), "LOGIN_FAILED", "Tài khoản bị khóa");
            }
            throw e;
        }
    }

    /**
     * Ghi nhận sự kiện người dùng đăng xuất.
     *
     * @param userDetails thông tin xác thực của người dùng
     * @return thông điệp xác nhận đăng xuất
     */
    public Map<String, String> logoutUser(CustomUserDetails userDetails) {
        String username = userDetails != null ? userDetails.getUsername() : "ANONYMOUS";
        if (auditLogService != null) {
            auditLogService.log(username, "LOGOUT", "Người dùng đăng xuất phiên làm việc");
        }
        return Map.of("message", "Đăng xuất thành công");
    }
}
