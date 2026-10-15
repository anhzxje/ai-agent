package prj.anhzxje.aiagent.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import prj.anhzxje.aiagent.dto.auth.ChangePasswordRequest;
import prj.anhzxje.aiagent.dto.auth.UserProfileResponse;
import prj.anhzxje.aiagent.dto.user.UserPreferencesRequest;
import prj.anhzxje.aiagent.dto.user.UserPreferencesResponse;
import prj.anhzxje.aiagent.entity.User;
import prj.anhzxje.aiagent.repository.UserRepository;
import prj.anhzxje.aiagent.security.CustomUserDetails;
import prj.anhzxje.aiagent.service.AuditLogService;
import prj.anhzxje.aiagent.service.UserPreferencesService;

import java.util.Map;

/**
 * Controller phục vụ người dùng tự quản lý thông tin tài khoản cá nhân:
 * - Xem hồ sơ bản thân (/api/users/me)
 * - Tự đổi mật khẩu (/api/users/me/password)
 * - Quản lý tùy chọn cá nhân (/api/users/me/preferences): Theme sáng/tối, ngôn ngữ, model AI ưa thích.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserPreferencesService userPreferencesService;
    private final AuditLogService auditLogService;

    /**
     * Lấy thông tin cá nhân của chính mình.
     */
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMyProfile(@AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userDetails.getUser();
        UserPreferencesResponse prefs = userPreferencesService.getPreferences(user);

        return ResponseEntity.ok(UserProfileResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .isLocked(Boolean.TRUE.equals(user.getIsLocked()))
                .createdAt(user.getCreatedAt())
                .language(prefs.getLanguage())
                .theme(prefs.getTheme())
                .preferredModel(prefs.getPreferredModel())
                .build());
    }

    /**
     * Đổi mật khẩu của người dùng hiện tại.
     * Quy trình kiểm tra bảo mật đa tầng:
     * - Bước 1: Tra cứu người dùng từ CSDL theo ID trong Security Context.
     * - Bước 2: Kiểm tra mật khẩu hiện tại bằng passwordEncoder.matches() (so sánh mật khẩu thô với hash BCrypt).
     * - Bước 3: So khớp mật khẩu mới (newPassword) và xác nhận mật khẩu (confirmPassword).
     * - Bước 4: Kiểm tra chống đặt lại mật khẩu cũ (mật khẩu mới không được giống mật khẩu hiện tại).
     * - Bước 5: Băm mật khẩu mới bằng BCrypt và cập nhật vào CSDL.
     * - Bước 6: Ghi nhật ký kiểm toán (AuditLog) hành vi đổi mật khẩu thành công.
     */
    @PutMapping("/me/password")
    public ResponseEntity<Map<String, String>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        // Bước 1: Lấy thông tin user từ CSDL
        User user = userRepository.findById(userDetails.getUser().getId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thông tin tài khoản"));

        // Bước 2: Xác thực mật khẩu cũ có chính xác không
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Mật khẩu hiện tại không chính xác");
        }

        // Bước 3: Kiểm tra khớp giữa mật khẩu mới và chuỗi xác nhận
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Xác nhận mật khẩu mới không khớp");
        }

        // Bước 4: Chặn việc đặt lại mật khẩu cũ
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Mật khẩu mới không được trùng với mật khẩu cũ");
        }

        // Bước 5: Băm mật khẩu mới và lưu xuống CSDL
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // Bước 6: Ghi nhận vết kiểm toán bảo mật
        auditLogService.log(user.getUsername(), "CHANGE_PASSWORD", "Người dùng đã đổi mật khẩu thành công");

        return ResponseEntity.ok(Map.of("message", "Đổi mật khẩu thành công"));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePasswordAlias(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return changePassword(request, userDetails);
    }

    /**
     * Cài đặt cá nhân nhẹ: ngôn ngữ, giao diện sáng/tối, model ưa thích.
     */
    @GetMapping("/me/preferences")
    public ResponseEntity<UserPreferencesResponse> getPreferences(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(userPreferencesService.getPreferences(userDetails.getUser()));
    }

    @PutMapping("/me/preferences")
    public ResponseEntity<UserPreferencesResponse> updatePreferences(
            @RequestBody UserPreferencesRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UserPreferencesResponse updated = userPreferencesService.updatePreferences(userDetails.getUser(), request);
        auditLogService.log(userDetails.getUsername(), "UPDATE_PREFERENCES",
                "Cập nhật tùy chọn cá nhân [lang=" + updated.getLanguage() + ", theme=" + updated.getTheme() + ", model=" + updated.getPreferredModel() + "]");
        return ResponseEntity.ok(updated);
    }
}
