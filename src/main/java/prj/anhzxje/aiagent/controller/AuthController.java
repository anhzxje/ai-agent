    package prj.anhzxje.aiagent.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import prj.anhzxje.aiagent.dto.auth.AuthResponse;
import prj.anhzxje.aiagent.dto.auth.LoginRequest;
import prj.anhzxje.aiagent.dto.auth.RegisterRequest;
import prj.anhzxje.aiagent.repository.UserRepository;
import prj.anhzxje.aiagent.security.CustomUserDetails;
import prj.anhzxje.aiagent.security.JwtTokenProvider;
import prj.anhzxje.aiagent.service.AuditLogService;
import prj.anhzxje.aiagent.service.AuthService;

import java.util.Map;

/**
 * Controller xử lý Xác thực và Phân quyền người dùng (Authentication & Authorization):
 * - Đăng ký tài khoản mới (/api/auth/register) -> Mã hóa mật khẩu BCrypt, sinh JWT Token.
 * - Đăng nhập (/api/auth/login) -> Kiểm tra tài khoản có bị khóa không, xác thực qua AuthenticationManager, cấp JWT.
 * - Đăng xuất (/api/auth/logout) -> Ghi vết kiểm toán (Audit Log) phiên làm việc.
 *
 * Nghiệp vụ cốt lõi được ủy thác cho AuthService theo nguyên lý Single Responsibility.
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    @Autowired
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Constructor tương thích ngược cho Mockito Unit Tests và khởi tạo thủ công.
     */
    public AuthController(AuthenticationManager authenticationManager,
                          UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          JwtTokenProvider jwtTokenProvider) {
        this(new AuthService(authenticationManager, userRepository, passwordEncoder, jwtTokenProvider, null));
    }

    public AuthController(AuthenticationManager authenticationManager,
                          UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          JwtTokenProvider jwtTokenProvider,
                          AuditLogService auditLogService) {
        this(new AuthService(authenticationManager, userRepository, passwordEncoder, jwtTokenProvider, auditLogService));
    }

    /**
     * Endpoint đăng ký tài khoản thành viên mới.
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.registerUser(request);
        if (response.getToken() == null) {
            return ResponseEntity.badRequest().body(response);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Endpoint xác thực thông tin đăng nhập và trả về JWT Bearer Token.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        try {
            AuthResponse response = authService.loginUser(request);
            if (response.getToken() == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
            return ResponseEntity.ok(response);
        } catch (LockedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    AuthResponse.builder()
                            .message("Tài khoản của bạn đã bị khóa bởi Quản trị viên")
                            .build());
        }
    }

    /**
     * Endpoint đăng xuất phiên làm việc.
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(authService.logoutUser(userDetails));
    }
}
