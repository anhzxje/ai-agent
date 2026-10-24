package prj.anhzxje.aiagent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.google.genai.Client;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.core.retry.RetryTemplate;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import prj.anhzxje.aiagent.dto.admin.SystemSettingsResponse;
import prj.anhzxje.aiagent.dto.admin.SystemSettingsUpdateRequest;
import prj.anhzxje.aiagent.entity.SystemSetting;
import prj.anhzxje.aiagent.repository.SystemSettingRepository;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConfigService {

    /** Key lưu tên mô hình AI đang kích hoạt trong CSDL */
    public static final String KEY_GEMINI_MODEL = "gemini.model";
    /** Key lưu khóa API Key người dùng/quản trị viên tùy chỉnh */
    public static final String KEY_GEMINI_API_KEY = "gemini.api_key";
    /** Key lưu danh sách mô hình được phép sử dụng trong hệ thống */
    public static final String KEY_ALLOWED_MODELS = "gemini.allowed_models";

    private final SystemSettingRepository systemSettingRepository;
    private final AuditLogService auditLogService;

    /** Model mặc định: gemini-3.5-flash-lite (Hạn ngạch cao 500 RPD, tốc độ phản hồi nhanh nhất) */
    @Value("${spring.ai.google.genai.chat.options.model:gemini-3.5-flash-lite}")
    private String defaultModel;

    /** Khóa API nạp từ biến môi trường nếu có */
    @Value("${spring.ai.google.genai.api-key:}")
    private String envApiKey;

    /** Danh sách đường dẫn thư mục được cấp phép quét mã nguồn */
    private List<String> allowedPaths = List.of();

    @org.springframework.beans.factory.annotation.Autowired
    void configureAgentPaths(prj.anhzxje.aiagent.config.AgentProperties properties) {
        this.allowedPaths = properties.getAllowedPaths();
    }

    /**
     * Lấy tên mô hình AI đang kích hoạt hiện tại trong hệ thống.
     * Quy tắc ưu tiên bậc thang (Fallback cascade):
     * - Ưu tiên 1: Cấu hình do Admin lưu trong CSDL (bảng system_settings với key 'gemini.model').
     * - Ưu tiên 2: Biến cấu hình ứng dụng application.yml / defaultModel ('gemini-3.5-flash-lite').
     */
    @Transactional(readOnly = true)
    public String getActiveModel() {
        return systemSettingRepository.findBySettingKey(KEY_GEMINI_MODEL)
                .map(SystemSetting::getSettingValue)
                .filter(s -> !s.isBlank())
                .orElse(defaultModel);
    }

    /**
     * Lấy khóa API thực thi có hiệu lực.
     * Quy tắc ưu tiên bậc thang (Fallback cascade):
     * - Ưu tiên 1: Khóa API do Admin lưu động trong CSDL (key 'gemini.api_key').
     * - Ưu tiên 2: Biến môi trường hệ thống SPRING_AI_GOOGLE_GENAI_API_KEY / envApiKey.
     */
    @Transactional(readOnly = true)
    public String getEffectiveApiKey() {
        return systemSettingRepository.findBySettingKey(KEY_GEMINI_API_KEY)
                .map(SystemSetting::getSettingValue)
                .filter(s -> !s.isBlank())
                .orElse(envApiKey);
    }

    /**
     * Kiểm tra xem Quản trị viên có cấu hình API Key tùy chỉnh trong CSDL hay không.
     */
    @Transactional(readOnly = true)
    public boolean hasCustomApiKey() {
        return systemSettingRepository.findBySettingKey(KEY_GEMINI_API_KEY)
                .map(SystemSetting::getSettingValue)
                .filter(s -> !s.isBlank())
                .isPresent();
    }

    /**
     * Tạo hoặc tùy biến ChatClient.Builder với API Key có hiệu lực ở runtime.
     * Nếu có API Key riêng trong CSDL (khác với biến môi trường), khởi tạo ChatClient
     * trỏ đến Google GenAI client sử dụng API Key đó.
     * Nếu không có API Key riêng hoặc trong môi trường test (fallback), sử dụng defaultBuilder.
     */
    public ChatClient.Builder getChatClientBuilder(ChatClient.Builder defaultBuilder) {
        String effectiveKey = getEffectiveApiKey();
        if (effectiveKey != null && !effectiveKey.isBlank() && !effectiveKey.equals(envApiKey)) {
            try {
                Client client = Client.builder().apiKey(effectiveKey).build();
                GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder().build();
                RetryTemplate retryTemplate = new RetryTemplate();
                ObservationRegistry registry = ObservationRegistry.NOOP;
                ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();
                GoogleGenAiChatModel chatModel = new GoogleGenAiChatModel(client, options, toolCallingManager, retryTemplate, registry);
                return ChatClient.builder(chatModel);
            } catch (Throwable e) {
                log.warn("Không thể khởi tạo custom Google GenAI Client với custom API Key, fallback về defaultBuilder: {}", e.getMessage());
            }
        }
        return defaultBuilder;
    }

    /**
     * Lấy danh sách mô hình AI được cấp phép sử dụng trong hệ thống.
     * Phân tách chuỗi lưu trữ phân tách bằng dấu phẩy trong CSDL thành List<String>.
     * Fallback về danh sách các model tối ưu: gemini-3.5-flash-lite, gemini-3.1-flash-lite, gemini-2.5-flash.
     */
    @Transactional(readOnly = true)
    public List<String> getAllowedModels() {
        return systemSettingRepository.findBySettingKey(KEY_ALLOWED_MODELS)
                .map(s -> Arrays.stream(s.getSettingValue().split(","))
                        .map(String::trim)
                        .filter(str -> !str.isEmpty())
                        .collect(Collectors.toList()))
                .filter(list -> !list.isEmpty())
                .orElse(List.of("gemini-3.5-flash-lite", "gemini-3.1-flash-lite", "gemini-2.5-flash"));
    }

    /**
     * Trả về thông tin cấu hình phục vụ màn hình Quản trị (Admin System Settings).
     * Điểm bảo mật quan trọng:
     * Khóa API Key luôn được che dấu (Masked) dạng 'AIza****1234' trước khi trả về client,
     * ngăn chặn nguy cơ lộ lọt API Key qua giao diện hoặc Network Inspector.
     */
    @Transactional(readOnly = true)
    public SystemSettingsResponse getSettingsForAdmin() {
        String activeModel = getActiveModel();
        String currentKey = systemSettingRepository.findBySettingKey(KEY_GEMINI_API_KEY)
                .map(SystemSetting::getSettingValue)
                .orElse(null);

        boolean hasCustomKey = currentKey != null && !currentKey.isBlank();
        String maskedKey;
        if (hasCustomKey) {
            maskedKey = maskKey(currentKey);
        } else if (envApiKey != null && !envApiKey.isBlank()) {
            maskedKey = "Biến môi trường: " + maskKey(envApiKey);
        } else {
            maskedKey = "Chưa cấu hình API Key";
        }

        String defaultPath = (allowedPaths != null && !allowedPaths.isEmpty()) ? allowedPaths.get(0) : "Chưa cấu hình";

        return SystemSettingsResponse.builder()
                .activeModel(activeModel)
                .maskedApiKey(maskedKey)
                .hasCustomApiKey(hasCustomKey)
                .allowedModels(getAllowedModels())
                .defaultAllowedPath(defaultPath)
                .build();
    }

    /**
     * Cập nhật các thông số cấu hình hệ thống từ phía Quản trị viên.
     * Quy trình xử lý:
     * - Bước 1: Cập nhật model AI mặc định nếu có truyền.
     * - Bước 2: Kiểm tra bảo mật API Key: Nếu người dùng không nhập key mới mà gửi lại chuỗi masked (chứa '****'),
     *           hệ thống BỎ QUA để không ghi đè mất API Key thật đang lưu.
     * - Bước 3: Nối danh sách các model cho phép thành chuỗi phân cách bởi dấu phẩy và lưu vào CSDL.
     * - Bước 4: Ghi nhật ký kiểm toán (AuditLog) kèm chuỗi API Key đã che giấu (maskKey).
     */
    @Transactional
    public void updateSettings(SystemSettingsUpdateRequest req, String adminUsername) {
        StringBuilder changeLog = new StringBuilder();

        // Bước 1: Cập nhật model AI mặc định
        if (req.getModel() != null && !req.getModel().isBlank()) {
            saveOrUpdate(KEY_GEMINI_MODEL, req.getModel().trim(), "Active Gemini Model");
            changeLog.append("Model: ").append(req.getModel().trim()).append("; ");
        }

        // Bước 2: Kiểm tra và lưu API Key mới (bảo vệ chống ghi đè chuỗi masked)
        if (req.getApiKey() != null && !req.getApiKey().isBlank()) {
            String trimmedKey = req.getApiKey().trim();
            // Nếu người dùng không sửa trường key mà gửi lại chuỗi hiển thị masked -> Bỏ qua
            if (!trimmedKey.contains("****") && !trimmedKey.startsWith("Biến môi trường")) {
                saveOrUpdate(KEY_GEMINI_API_KEY, trimmedKey, "Custom Google GenAI API Key");
                changeLog.append("API Key: ").append(maskKey(trimmedKey)).append("; ");
            }
        }

        // Bước 3: Cập nhật danh sách models cho phép
        if (req.getAllowedModels() != null && !req.getAllowedModels().isEmpty()) {
            String joined = String.join(",", req.getAllowedModels());
            saveOrUpdate(KEY_ALLOWED_MODELS, joined, "Danh sách models cho phép người dùng chọn");
            changeLog.append("AllowedModels: ").append(joined).append("; ");
        }

        // Bước 4: Ghi nhật ký Audit Log
        auditLogService.log(adminUsername, "UPDATE_CONFIG", "Cập nhật cấu hình hệ thống: " + changeLog);
        log.info("Admin [{}] đã cập nhật cấu hình: {}", adminUsername, changeLog);
    }

    /**
     * Lưu hoặc cập nhật một mục cài đặt vào bảng SystemSetting trong CSDL.
     */
    private void saveOrUpdate(String key, String value, String desc) {
        SystemSetting setting = systemSettingRepository.findBySettingKey(key)
                .orElse(SystemSetting.builder().settingKey(key).build());
        setting.setSettingValue(value);
        setting.setDescription(desc);
        setting.setUpdatedAt(LocalDateTime.now());
        systemSettingRepository.save(setting);
    }

    /**
     * Làm mờ / che giấu khóa API Key để hiển thị an toàn trên giao diện Quản trị.
     * Định dạng: Giữ lại 4 ký tự đầu và 4 ký tự cuối, thay thế phần giữa bằng '****' (VD: AIza****1234).
     *
     * @param key chuỗi khóa API thô
     * @return chuỗi khóa đã được che giấu
     */
    private String maskKey(String key) {
        if (key == null || key.length() < 8) return "********";
        int len = key.length();
        return key.substring(0, 4) + "****" + key.substring(len - 4);
    }
}
