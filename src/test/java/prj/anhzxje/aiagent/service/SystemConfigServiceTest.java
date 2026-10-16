package prj.anhzxje.aiagent.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.util.ReflectionTestUtils;
import prj.anhzxje.aiagent.dto.admin.SystemSettingsResponse;
import prj.anhzxje.aiagent.dto.admin.SystemSettingsUpdateRequest;
import prj.anhzxje.aiagent.entity.SystemSetting;
import prj.anhzxje.aiagent.repository.SystemSettingRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SystemConfigServiceTest {

    @Mock
    private SystemSettingRepository systemSettingRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private ChatClient.Builder defaultChatClientBuilder;

    @InjectMocks
    private SystemConfigService systemConfigService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(systemConfigService, "defaultModel", "gemini-3.5-flash-lite");
        ReflectionTestUtils.setField(systemConfigService, "envApiKey", "env-api-key-12345678");
        ReflectionTestUtils.setField(systemConfigService, "allowedPaths", List.of("/workspace/allowed"));
    }

    @Test
    void testGetActiveModel_DefaultFallback() {
        when(systemSettingRepository.findBySettingKey(SystemConfigService.KEY_GEMINI_MODEL))
                .thenReturn(Optional.empty());

        assertEquals("gemini-3.5-flash-lite", systemConfigService.getActiveModel());
    }

    @Test
    void testGetActiveModel_FromDatabase() {
        SystemSetting setting = SystemSetting.builder()
                .settingKey(SystemConfigService.KEY_GEMINI_MODEL)
                .settingValue("gemini-2.5-flash")
                .build();
        when(systemSettingRepository.findBySettingKey(SystemConfigService.KEY_GEMINI_MODEL))
                .thenReturn(Optional.of(setting));

        assertEquals("gemini-2.5-flash", systemConfigService.getActiveModel());
    }

    @Test
    void testGetEffectiveApiKey_FallbackToEnv() {
        when(systemSettingRepository.findBySettingKey(SystemConfigService.KEY_GEMINI_API_KEY))
                .thenReturn(Optional.empty());

        assertEquals("env-api-key-12345678", systemConfigService.getEffectiveApiKey());
        assertFalse(systemConfigService.hasCustomApiKey());
    }

    @Test
    void testGetEffectiveApiKey_CustomKeyFromDb() {
        SystemSetting setting = SystemSetting.builder()
                .settingKey(SystemConfigService.KEY_GEMINI_API_KEY)
                .settingValue("custom-db-key-99999999")
                .build();
        when(systemSettingRepository.findBySettingKey(SystemConfigService.KEY_GEMINI_API_KEY))
                .thenReturn(Optional.of(setting));

        assertEquals("custom-db-key-99999999", systemConfigService.getEffectiveApiKey());
        assertTrue(systemConfigService.hasCustomApiKey());
    }

    @Test
    void testGetChatClientBuilder_FallbackToDefaultWhenNoCustomKey() {
        when(systemSettingRepository.findBySettingKey(SystemConfigService.KEY_GEMINI_API_KEY))
                .thenReturn(Optional.empty());

        ChatClient.Builder builder = systemConfigService.getChatClientBuilder(defaultChatClientBuilder);
        assertSame(defaultChatClientBuilder, builder);
    }

    @Test
    void testGetChatClientBuilder_CustomKeyBuildsNewBuilder() {
        SystemSetting setting = SystemSetting.builder()
                .settingKey(SystemConfigService.KEY_GEMINI_API_KEY)
                .settingValue("AIzaSyNewCustomKeyForGeminiTest12345")
                .build();
        when(systemSettingRepository.findBySettingKey(SystemConfigService.KEY_GEMINI_API_KEY))
                .thenReturn(Optional.of(setting));

        ChatClient.Builder builder = systemConfigService.getChatClientBuilder(defaultChatClientBuilder);
        assertNotNull(builder);
        assertNotSame(defaultChatClientBuilder, builder);
    }

    @Test
    void testGetSettingsForAdmin_MasksApiKeySafely() {
        when(systemSettingRepository.findBySettingKey(SystemConfigService.KEY_GEMINI_MODEL))
                .thenReturn(Optional.empty());
        when(systemSettingRepository.findBySettingKey(SystemConfigService.KEY_GEMINI_API_KEY))
                .thenReturn(Optional.empty());
        when(systemSettingRepository.findBySettingKey(SystemConfigService.KEY_ALLOWED_MODELS))
                .thenReturn(Optional.empty());

        SystemSettingsResponse res = systemConfigService.getSettingsForAdmin();
        assertNotNull(res);
        assertEquals("gemini-3.5-flash-lite", res.getActiveModel());
        assertTrue(res.getMaskedApiKey().contains("****"));
        assertFalse(res.isHasCustomApiKey());
    }

    @Test
    void testUpdateSettings_IgnoresMaskedKeyAndSavesModel() {
        SystemSettingsUpdateRequest req = SystemSettingsUpdateRequest.builder()
                .model("gemini-2.5-flash")
                .apiKey("AIza****1234") // Masked key should NOT overwrite db
                .allowedModels(List.of("gemini-3.5-flash-lite", "gemini-2.5-flash"))
                .build();

        when(systemSettingRepository.findBySettingKey(SystemConfigService.KEY_GEMINI_MODEL))
                .thenReturn(Optional.empty());
        when(systemSettingRepository.findBySettingKey(SystemConfigService.KEY_ALLOWED_MODELS))
                .thenReturn(Optional.empty());

        systemConfigService.updateSettings(req, "admin");

        verify(systemSettingRepository, times(2)).save(any(SystemSetting.class));
        verify(auditLogService, times(1)).log(eq("admin"), eq("UPDATE_CONFIG"), anyString());
    }
}
