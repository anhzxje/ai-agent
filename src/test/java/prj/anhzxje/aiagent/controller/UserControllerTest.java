package prj.anhzxje.aiagent.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import prj.anhzxje.aiagent.dto.auth.ChangePasswordRequest;
import prj.anhzxje.aiagent.dto.auth.UserProfileResponse;
import prj.anhzxje.aiagent.dto.user.UserPreferencesRequest;
import prj.anhzxje.aiagent.dto.user.UserPreferencesResponse;
import prj.anhzxje.aiagent.entity.User;
import prj.anhzxje.aiagent.enums.Role;
import prj.anhzxje.aiagent.repository.UserRepository;
import prj.anhzxje.aiagent.security.CustomUserDetails;
import prj.anhzxje.aiagent.service.AuditLogService;
import prj.anhzxje.aiagent.service.UserPreferencesService;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Kiểm thử Đơn vị cho UserController (UserControllerTest):
 * Kiểm tra các tính năng:
 * 1. Lấy thông tin cá nhân (Profile) của người dùng đang đăng nhập.
 * 2. Đổi mật khẩu cá nhân (thành công, sai mật khẩu hiện tại, xác nhận mật khẩu không khớp).
 * 3. Lấy và cập nhật tùy chọn người dùng (ngôn ngữ, theme, preferredModel).
 */
@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserPreferencesService userPreferencesService;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private UserController userController;

    private User sampleUser;
    private CustomUserDetails userDetails;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(1L)
                .username("john")
                .email("john@example.com")
                .password("encoded_old_pass")
                .role(Role.USER)
                .isLocked(false)
                .build();

        userDetails = new CustomUserDetails(sampleUser);
    }

    @Test
    void testGetMyProfile_Success() {
        UserPreferencesResponse prefs = UserPreferencesResponse.builder()
                .language("vi")
                .theme("dark")
                .preferredModel("gemini-3.5-flash-lite")
                .build();
        when(userPreferencesService.getPreferences(any(User.class))).thenReturn(prefs);

        ResponseEntity<UserProfileResponse> res = userController.getMyProfile(userDetails);

        assertNotNull(res.getBody());
        assertEquals("john", res.getBody().getUsername());
        assertEquals("vi", res.getBody().getLanguage());
        assertEquals("dark", res.getBody().getTheme());
    }

    @Test
    void testChangePassword_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("old_pass", "encoded_old_pass")).thenReturn(true);
        when(passwordEncoder.matches("new_pass", "encoded_old_pass")).thenReturn(false);
        when(passwordEncoder.encode("new_pass")).thenReturn("encoded_new_pass");

        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("old_pass")
                .newPassword("new_pass")
                .confirmPassword("new_pass")
                .build();

        ResponseEntity<Map<String, String>> res = userController.changePassword(request, userDetails);

        assertNotNull(res.getBody());
        assertEquals("Đổi mật khẩu thành công", res.getBody().get("message"));
        assertEquals("encoded_new_pass", sampleUser.getPassword());
        verify(userRepository, times(1)).save(sampleUser);
        verify(auditLogService, times(1)).log(eq("john"), eq("CHANGE_PASSWORD"), any());
    }

    @Test
    void testChangePassword_WrongCurrentPassword() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("wrong_pass", "encoded_old_pass")).thenReturn(false);

        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("wrong_pass")
                .newPassword("new_pass")
                .confirmPassword("new_pass")
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                userController.changePassword(request, userDetails));

        assertEquals("Mật khẩu hiện tại không chính xác", ex.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void testChangePassword_ConfirmMismatch() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("old_pass", "encoded_old_pass")).thenReturn(true);

        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("old_pass")
                .newPassword("new_pass1")
                .confirmPassword("new_pass2")
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                userController.changePassword(request, userDetails));

        assertEquals("Xác nhận mật khẩu mới không khớp", ex.getMessage());
    }

    @Test
    void testPreferences_GetAndUpdate() {
        UserPreferencesRequest req = UserPreferencesRequest.builder()
                .language("en")
                .theme("light")
                .preferredModel("gemini-1.5-flash")
                .build();

        UserPreferencesResponse resp = UserPreferencesResponse.builder()
                .language("en")
                .theme("light")
                .preferredModel("gemini-1.5-flash")
                .build();

        when(userPreferencesService.updatePreferences(any(User.class), any())).thenReturn(resp);

        ResponseEntity<UserPreferencesResponse> res = userController.updatePreferences(req, userDetails);
        assertNotNull(res.getBody());
        assertEquals("en", res.getBody().getLanguage());
        assertEquals("light", res.getBody().getTheme());
    }
}
