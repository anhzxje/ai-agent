package prj.anhzxje.aiagent.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/** One-click login for explicitly enabled, loopback-only development servers. */
@RestController
@ConditionalOnProperty(name = "app.local-quick-login.enabled", havingValue = "true")
public class LocalLoginController {
    private static final Set<String> LOOPBACK = Set.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1");
    private final AuthService authService;
    private final String adminPassword;

    public LocalLoginController(AuthService authService,
                                @Value("${bootstrap.admin-password:}") String adminPassword) {
        this.authService = authService;
        this.adminPassword = adminPassword;
    }

    @PostMapping("/api/auth/local-admin")
    public ResponseEntity<AuthResponse> login(HttpServletRequest request,
            @RequestHeader(value = "X-Local-Quick-Login", defaultValue = "") String intent) {
        String origin = request.getHeader("Origin");
        String expectedOrigin = request.getScheme() + "://" + request.getHeader("Host");
        if (!"1".equals(intent) || !LOOPBACK.contains(request.getRemoteAddr())
                || !LOOPBACK.contains(request.getLocalAddr())
                || request.getHeader("Forwarded") != null
                || request.getHeader("X-Forwarded-For") != null
                || "cross-site".equals(request.getHeader("Sec-Fetch-Site"))
                || (origin != null && !origin.equals(expectedOrigin))
                || adminPassword.length() < 12) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(AuthResponse.builder()
                    .message("Đăng nhập nhanh chỉ khả dụng trên máy local.").build());
        }
        LoginRequest login = new LoginRequest();
        login.setUsername("admin");
        login.setPassword(adminPassword);
        AuthResponse response = authService.loginUser(login);
        return ResponseEntity.status(response.getToken() == null ? HttpStatus.FORBIDDEN : HttpStatus.OK)
                .body(response);
    }
}
