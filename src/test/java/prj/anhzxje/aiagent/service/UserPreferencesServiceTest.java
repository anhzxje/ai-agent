package prj.anhzxje.aiagent.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import prj.anhzxje.aiagent.dto.user.UserPreferencesRequest;
import prj.anhzxje.aiagent.dto.user.UserPreferencesResponse;
import prj.anhzxje.aiagent.entity.User;
import prj.anhzxje.aiagent.entity.UserPreferences;
import prj.anhzxje.aiagent.enums.Role;
import prj.anhzxje.aiagent.repository.UserPreferencesRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Kiểm thử Đơn vị cho Tùy chọn Cá nhân (UserPreferencesServiceTest):
 * Kiểm tra:
 * 1. Lấy tùy chọn mặc định nếu chưa lưu.
 * 2. Cập nhật tùy chọn hợp lệ.
 * 3. Ngăn chặn cập nhật model không nằm trong allowedModels.
 */
@ExtendWith(MockitoExtension.class)
class UserPreferencesServiceTest {

    @Mock
    private UserPreferencesRepository userPreferencesRepository;

    @Mock
    private SystemConfigService systemConfigService;

    @InjectMocks
    private UserPreferencesService userPreferencesService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).username("testuser").role(Role.USER).build();
    }

    @Test
    void testGetPreferences_DefaultWhenNotFound() {
        when(userPreferencesRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(systemConfigService.getActiveModel()).thenReturn("gemini-3.5-flash-lite");

        UserPreferencesResponse response = userPreferencesService.getPreferences(user);

        assertNotNull(response);
        assertEquals("vi", response.getLanguage());
        assertEquals("dark", response.getTheme());
        assertEquals("gemini-3.5-flash-lite", response.getPreferredModel());
    }

    @Test
    void testUpdatePreferences_Success() {
        UserPreferences existing = UserPreferences.builder()
                .id(10L)
                .user(user)
                .language("vi")
                .theme("dark")
                .preferredModel("gemini-3.5-flash-lite")
                .build();

        when(userPreferencesRepository.findByUserId(1L)).thenReturn(Optional.of(existing));
        when(systemConfigService.getAllowedModels()).thenReturn(List.of("gemini-3.5-flash-lite", "gemini-3.1-flash-lite"));
        when(userPreferencesRepository.save(any(UserPreferences.class))).thenAnswer(i -> i.getArgument(0));

        UserPreferencesRequest req = UserPreferencesRequest.builder()
                .language("en")
                .theme("light")
                .preferredModel("gemini-3.1-flash-lite")
                .build();

        UserPreferencesResponse response = userPreferencesService.updatePreferences(user, req);

        assertNotNull(response);
        assertEquals("en", response.getLanguage());
        assertEquals("light", response.getTheme());
        assertEquals("gemini-3.1-flash-lite", response.getPreferredModel());
    }

    @Test
    void testUpdatePreferences_DisallowedModel_ThrowsException() {
        when(userPreferencesRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(systemConfigService.getAllowedModels()).thenReturn(List.of("gemini-3.5-flash-lite"));

        UserPreferencesRequest req = UserPreferencesRequest.builder()
                .preferredModel("unallowed-model")
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                userPreferencesService.updatePreferences(user, req));

        assertTrue(ex.getMessage().contains("không nằm trong danh sách"));
    }
}
