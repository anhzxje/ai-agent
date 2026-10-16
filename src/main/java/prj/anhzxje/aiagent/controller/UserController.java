package prj.anhzxje.aiagent.controller;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import prj.anhzxje.aiagent.dto.auth.ChangePasswordRequest;
import prj.anhzxje.aiagent.dto.auth.UserProfileResponse;
import prj.anhzxje.aiagent.dto.user.UserPreferencesRequest;
import prj.anhzxje.aiagent.dto.user.UserPreferencesResponse;
import prj.anhzxje.aiagent.repository.UserRepository;
import prj.anhzxje.aiagent.security.CustomUserDetails;
import prj.anhzxje.aiagent.service.AuditLogService;
import prj.anhzxje.aiagent.service.UserPreferencesService;
import prj.anhzxje.aiagent.service.UserService;

import java.util.Map;

/**
 * Controller phục vụ người dùng tự quản lý thông tin tài khoản cá nhân:
 * - Xem hồ sơ bản thân (/api/users/me)
 * - Tự đổi mật khẩu (/api/users/me/password)
 * - Quản lý tùy chọn cá nhân (/api/users/me/preferences): Theme sáng/tối, ngôn ngữ, model AI ưa thích.
 *
 * Nghiệp vụ cốt lõi được ủy thác cho UserService theo nguyên lý Single Responsibility.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final UserPreferencesService userPreferencesService;
    private final AuditLogService auditLogService;

    @Autowired
    public UserController(UserService userService,
                          UserPreferencesService userPreferencesService,
                          @Autowired(required = false) AuditLogService auditLogService) {
        this.userService = userService;
        this.userPreferencesService = userPreferencesService;
        this.auditLogService = auditLogService;
    }

    /**
     * Constructor tương thích ngược cho Mockito Unit Tests và khởi tạo trực tiếp.
     */
    public UserController(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          UserPreferencesService userPreferencesService,
                          AuditLogService auditLogService) {
        this(new UserService(userRepository, passwordEncoder, userPreferencesService, auditLogService),
                userPreferencesService,
                auditLogService);
    }

    /**
     * Lấy thông tin hồ sơ cá nhân của người dùng đang đăng nhập.
     */
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMyProfile(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(userService.getUserProfile(userDetails.getUser()));
    }

    /**
     * Đổi mật khẩu của người dùng hiện tại.
     */
    @PutMapping("/me/password")
    public ResponseEntity<Map<String, String>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(userService.changePassword(userDetails.getUser().getId(), request));
    }

    /**
     * Alias endpoint cho thao tác đổi mật khẩu.
     */
    @PostMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePasswordAlias(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return changePassword(request, userDetails);
    }

    /**
     * Lấy cấu hình tùy chọn cá nhân hiện tại của người dùng.
     */
    @GetMapping("/me/preferences")
    public ResponseEntity<UserPreferencesResponse> getPreferences(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(userPreferencesService.getPreferences(userDetails.getUser()));
    }

    /**
     * Cập nhật tùy chọn cá nhân của người dùng.
     */
    @PutMapping("/me/preferences")
    public ResponseEntity<UserPreferencesResponse> updatePreferences(
            @RequestBody UserPreferencesRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UserPreferencesResponse updated = userPreferencesService.updatePreferences(userDetails.getUser(), request);
        if (auditLogService != null) {
            auditLogService.log(userDetails.getUsername(), "UPDATE_PREFERENCES",
                    "Cập nhật tùy chọn cá nhân [lang=" + updated.getLanguage() + ", theme=" + updated.getTheme() + ", model=" + updated.getPreferredModel() + "]");
        }
        return ResponseEntity.ok(updated);
    }
}
