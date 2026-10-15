package prj.anhzxje.aiagent.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import prj.anhzxje.aiagent.dto.auth.AuthResponse;
import prj.anhzxje.aiagent.dto.auth.LoginRequest;
import prj.anhzxje.aiagent.dto.auth.RegisterRequest;
import prj.anhzxje.aiagent.entity.User;
import prj.anhzxje.aiagent.enums.Role;
import prj.anhzxje.aiagent.repository.UserRepository;
import prj.anhzxje.aiagent.security.CustomUserDetails;
import prj.anhzxje.aiagent.security.JwtTokenProvider;
import prj.anhzxje.aiagent.service.AuditLogService;

import java.util.Map;

/**
 * Controller xử lý Xác thực và Phân quyền người dùng (Authentication & Authorization):
 * - Đăng ký tài khoản mới (/api/auth/register) -> Mã hóa mật khẩu BCrypt, sinh JWT Token.
 * - Đăng nhập (/api/auth/login) -> Kiểm tra tài khoản có bị khóa không, xác thực qua AuthenticationManager, cấp JWT.
 * - Đăng xuất (/api/auth/logout) -> Ghi vết kiểm toán (Audit Log) phiên làm việc.
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuditLogService auditLogService;

    public AuthController(AuthenticationManager authenticationManager,
                          UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          JwtTokenProvider jwtTokenProvider) {
        this(authenticationManager, userRepository, passwordEncoder, jwtTokenProvider, null);
    }

    @Autowired
    public AuthController(AuthenticationManager authenticationManager,
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
     * Endpoint đăng ký tài khoản thành viên mới.
     * Quy trình xử lý:
     * - Bước 1: Kiểm tra tính duy nhất của Username trong cơ sở dữ liệu (tránh trùng lặp).
     * - Bước 2: Kiểm tra tính duy nhất của Email đăng ký.
     * - Bước 3: Mã hóa mật khẩu an toàn bằng thuật toán BCrypt (không bao giờ lưu mật khẩu thô).
     * - Bước 4: Tạo thực thể User với vai trò mặc định ROLE_USER và trạng thái mở khóa (isLocked = false).
     * - Bước 5: Lưu thông tin người dùng vào CSDL qua userRepository.
     * - Bước 6: Khởi tạo ngay JWT Bearer Token chứa username & vai trò để người dùng đăng nhập tự động.
     * - Bước 7: Ghi nhật ký kiểm toán (Audit Log) phục vụ truy vết bảo mật.
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Đăng ký tài khoản mới: {}", request.getUsername());

        // Bước 1: Kiểm tra username đã tồn tại chưa
        if (userRepository.existsByUsername(request.getUsername())) {
            return ResponseEntity.badRequest().body(
                    AuthResponse.builder()
                            .message("Username đã tồn tại")
                            .build());
        }

        // Bước 2: Kiểm tra email đã được đăng ký tài khoản khác chưa
        if (userRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity.badRequest().body(
                    AuthResponse.builder()
                            .message("Email đã được sử dụng")
                            .build());
        }

        // Bước 3 & 4: Khởi tạo thực thể User và băm mật khẩu bằng BCryptPasswordEncoder
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .isLocked(false)
                .build();

        // Bước 5: Lưu thực thể User xuống Database
        userRepository.save(user);

        // Bước 6: Tạo chữ ký điện tử JWT Token với thời hạn cấu hình
        String token = jwtTokenProvider.generateToken(user.getUsername(), user.getRole().name());

        // Bước 7: Ghi nhận sự kiện vào bảng Audit Logs nếu service khả dụng
        if (auditLogService != null) {
            auditLogService.log(user.getUsername(), "REGISTER", "Đăng ký tài khoản mới thành công");
        }
        log.info("Đăng ký thành công cho user: {}", user.getUsername());

        return ResponseEntity.status(HttpStatus.CREATED).body(
                AuthResponse.builder()
                        .token(token)
                        .username(user.getUsername())
                        .role(user.getRole().name())
                        .message("Đăng ký thành công")
                        .build());
    }

    /**
     * Endpoint xác thực thông tin đăng nhập và trả về JWT Bearer Token.
     * Quy trình xử lý:
     * - Bước 1: Kiểm tra sớm (pre-check) trạng thái khóa tài khoản (isLocked) từ CSDL. Nếu bị khóa -> 403 Forbidden.
     * - Bước 2: Chuyển thông tin tới Spring Security AuthenticationManager để xác thực username & password.
     * - Bước 3: Sau khi mật khẩu khớp, lấy thông tin UserDetails của người dùng đang đăng nhập.
     * - Bước 4: Tạo JWT Token có chứa Username, Role và Claims liên quan.
     * - Bước 5: Ghi nhật ký kiểm toán (Audit Log) về phiên đăng nhập thành công hoặc thất bại.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Đăng nhập: {}", request.getUsername());

        // Bước 1: Kiểm tra sớm trạng thái khóa tài khoản (isLocked) trước khi xử lý mã hóa
        var userOpt = userRepository.findByUsername(request.getUsername());
        if (userOpt.isPresent() && Boolean.TRUE.equals(userOpt.get().getIsLocked())) {
            if (auditLogService != null) {
                auditLogService.log(request.getUsername(), "LOGIN_FAILED", "Đăng nhập thất bại: Tài khoản đang bị khóa");
            }
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    AuthResponse.builder()
                            .message("Tài khoản của bạn đã bị khóa bởi Quản trị viên")
                            .build());
        }

        try {
            // Bước 2: Ủy thác xác thực cho AuthenticationManager của Spring Security
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()));

            // Bước 3: Trích xuất thông tin người dùng từ kết quả xác thực
            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            User user = userDetails.getUser();

            // Bước 4: Cấp phát chuỗi JWT Token mã hóa theo HMAC-SHA256
            String token = jwtTokenProvider.generateToken(user.getUsername(), user.getRole().name());

            // Bước 5: Ghi nhật ký Audit Log sự kiện đăng nhập thành công
            if (auditLogService != null) {
                auditLogService.log(user.getUsername(), "LOGIN", "Đăng nhập thành công vào hệ thống");
            }
            log.info("Đăng nhập thành công cho user: {}", user.getUsername());

            return ResponseEntity.ok(
                    AuthResponse.builder()
                            .token(token)
                            .username(user.getUsername())
                            .role(user.getRole().name())
                            .message("Đăng nhập thành công")
                            .build());
        } catch (LockedException e) {
            // Xử lý trường hợp tài khoản bị khóa phát sinh từ hệ thống Spring Security
            if (auditLogService != null) {
                auditLogService.log(request.getUsername(), "LOGIN_FAILED", "Tài khoản bị khóa");
            }
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    AuthResponse.builder()
                            .message("Tài khoản của bạn đã bị khóa bởi Quản trị viên")
                            .build());
        }
    }

    /**
     * Endpoint đăng xuất phiên làm việc.
     * Quy trình xử lý:
     * - Trích xuất danh tính người dùng hiện tại từ Security Context (@AuthenticationPrincipal).
     * - Ghi nhận sự kiện LOGOUT vào hệ thống Audit Log để phục vụ giám sát và bảo mật.
     * - Client chịu trách nhiệm xóa token khỏi LocalStorage/SessionStorage.
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(@AuthenticationPrincipal CustomUserDetails userDetails) {
        String username = userDetails != null ? userDetails.getUsername() : "ANONYMOUS";
        if (auditLogService != null) {
            auditLogService.log(username, "LOGOUT", "Người dùng đăng xuất phiên làm việc");
        }
        return ResponseEntity.ok(Map.of("message", "Đăng xuất thành công"));
    }
}
