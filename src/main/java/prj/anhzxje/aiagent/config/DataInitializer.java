package prj.anhzxje.aiagent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import prj.anhzxje.aiagent.entity.AgentToolConfig;
import prj.anhzxje.aiagent.entity.SystemSetting;
import prj.anhzxje.aiagent.entity.User;
import prj.anhzxje.aiagent.enums.Role;
import prj.anhzxje.aiagent.repository.AgentToolConfigRepository;
import prj.anhzxje.aiagent.repository.SystemSettingRepository;
import prj.anhzxje.aiagent.repository.UserRepository;
import prj.anhzxje.aiagent.service.AuditLogService;
import prj.anhzxje.aiagent.service.SystemConfigService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Khởi tạo dữ liệu gốc (Seed Data) khi ứng dụng chạy lần đầu tiên:
 * 1. Tài khoản quản trị viên mặc định: admin / admin (Mật khẩu được mã hóa BCrypt).
 * 2. Cấu hình các công cụ AI Agent (ListDirectory, Glob, FileSystem, Grep) vào CSDL.
 * 3. Cấu hình hệ thống (Active Model mặc định: gemini-2.5-flash-lite, danh sách model được phép).
 * 
 * Implement CommandLineRunner để chạy tự động ngay sau khi ApplicationContext sẵn sàng.
 */
@Slf4j
@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AgentToolConfigRepository toolConfigRepository;
    private final SystemSettingRepository systemSettingRepository;
    private final AuditLogService auditLogService;

    /**
     * Constructor phục vụ kiểm thử đơn vị hoặc khi chỉ cần khởi tạo User và PasswordEncoder.
     *
     * @param userRepository  repository quản lý người dùng
     * @param passwordEncoder thuật toán mã hóa mật khẩu BCrypt
     */
    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this(userRepository, passwordEncoder, null, null, null);
    }

    /**
     * Constructor đầy đủ được Spring Boot tiêm phụ thuộc tự động (@Autowired).
     *
     * @param userRepository          repository quản lý người dùng
     * @param passwordEncoder         thuật toán mã hóa mật khẩu BCrypt
     * @param toolConfigRepository    repository quản lý cấu hình công cụ Agent
     * @param systemSettingRepository repository quản lý cấu hình hệ thống
     * @param auditLogService         dịch vụ ghi nhật ký kiểm toán hệ thống
     */
    @Autowired
    public DataInitializer(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           @Autowired(required = false) AgentToolConfigRepository toolConfigRepository,
                           @Autowired(required = false) SystemSettingRepository systemSettingRepository,
                           @Autowired(required = false) AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.toolConfigRepository = toolConfigRepository;
        this.systemSettingRepository = systemSettingRepository;
        this.auditLogService = auditLogService;
    }

    /**
     * Phương thức thực thi chính khi Spring Boot khởi động xong.
     */
    @Override
    public void run(String... args) {
        initAdminUser();
        initDefaultTools();
        initDefaultSettings();
    }

    /**
     * Khởi tạo tài khoản quản trị viên mặc định (admin/admin với ROLE_ADMIN) nếu chưa tồn tại trong CSDL.
     */
    private void initAdminUser() {
        if (!userRepository.existsByUsername("admin")) {
            User admin = User.builder()
                    .username("admin")
                    .email("admin@admin.admin")
                    .password(passwordEncoder.encode("admin"))
                    .role(Role.ADMIN)
                    .isLocked(false)
                    .build();
            userRepository.save(admin);
            if (auditLogService != null) {
                auditLogService.log("SYSTEM", "INITIALIZE", "Khởi tạo tài khoản quản trị viên mặc định: admin");
            }
            log.info(">>> Đã tự động khởi tạo tài khoản quản trị viên: username=admin, role=ADMIN <<<");
        }
    }

    /**
     * Khởi tạo danh mục các công cụ mặc định của AI Agent (list_directory, glob, file_system, grep) vào CSDL nếu chưa có.
     */
    private void initDefaultTools() {
        if (toolConfigRepository == null) return;

        List<AgentToolConfig> defaultTools = List.of(
                AgentToolConfig.builder()
                        .toolName("list_directory")
                        .displayName("Duyệt thư mục (ListDirectoryTool)")
                        .description("Cho phép Agent khám phá cấu trúc cây thư mục của dự án")
                        .category("FILE_SYSTEM")
                        .enabled(true)
                        .updatedAt(LocalDateTime.now())
                        .build(),
                AgentToolConfig.builder()
                        .toolName("glob")
                        .displayName("Tìm tệp mẫu (GlobTool)")
                        .description("Tìm kiếm file theo pattern wildcard (*.java, *.ts, ...)")
                        .category("SEARCH")
                        .enabled(true)
                        .updatedAt(LocalDateTime.now())
                        .build(),
                AgentToolConfig.builder()
                        .toolName("file_system")
                        .displayName("Đọc mã nguồn (FileSystemTools)")
                        .description("Cho phép Agent đọc toàn bộ hoặc từng đoạn nội dung file")
                        .category("FILE_SYSTEM")
                        .enabled(true)
                        .updatedAt(LocalDateTime.now())
                        .build(),
                AgentToolConfig.builder()
                        .toolName("grep")
                        .displayName("Tìm kiếm từ khóa (GrepTool)")
                        .description("Tìm kiếm chuỗi hoặc biểu thức chính quy (Regex) trên toàn codebase")
                        .category("SEARCH")
                        .enabled(true)
                        .updatedAt(LocalDateTime.now())
                        .build()
        );

        for (AgentToolConfig tool : defaultTools) {
            if (toolConfigRepository.findByToolName(tool.getToolName()).isEmpty()) {
                toolConfigRepository.save(tool);
                log.info(">>> Đã khởi tạo công cụ mặc định: {} <<<", tool.getToolName());
            }
        }
    }

    /**
     * Khởi tạo các cấu hình hệ thống mặc định (Model AI đang kích hoạt và danh sách model được phép sử dụng).
     */
    private void initDefaultSettings() {
        if (systemSettingRepository == null) return;

        // Model mặc định: gemini-3.5-flash-lite (Hạn ngạch 500 RPD, siêu tốc, tối ưu phản hồi và miễn phí)
        var modelSetting = systemSettingRepository.findBySettingKey(SystemConfigService.KEY_GEMINI_MODEL);
        if (modelSetting.isEmpty()) {
            systemSettingRepository.save(SystemSetting.builder()
                    .settingKey(SystemConfigService.KEY_GEMINI_MODEL)
                    .settingValue("gemini-3.5-flash-lite")
                    .description("Model Gemini đang kích hoạt cho AI Agent (Mặc định: gemini-3.5-flash-lite)")
                    .updatedAt(LocalDateTime.now())
                    .build());
        }

        // Danh sách 3 model mạnh nhất, nhanh nhất, ổn định và miễn phí từ Google AI Studio Quota:
        // 1. gemini-3.5-flash-lite: 500 RPD, 15 RPM, siêu nhanh (Mặc định)
        // 2. gemini-3.1-flash-lite: 500 RPD, 15 RPM, thế hệ 3.1 mới nhất & ổn định
        // 3. gemini-2.5-flash-lite: 20 RPD, 10 RPM, tiết kiệm token
        // Kèm theo gemini-2.5-flash: 20 RPD, 5 RPM, lập luận chuyên sâu
        var allowedSetting = systemSettingRepository.findBySettingKey(SystemConfigService.KEY_ALLOWED_MODELS);
        String supportedList = "gemini-3.5-flash-lite,gemini-3.1-flash-lite,gemini-2.5-flash-lite,gemini-2.5-flash";
        if (allowedSetting.isEmpty()) {
            systemSettingRepository.save(SystemSetting.builder()
                    .settingKey(SystemConfigService.KEY_ALLOWED_MODELS)
                    .settingValue(supportedList)
                    .description("Danh sách model AI miễn phí, tốc độ cao được phép lựa chọn")
                    .updatedAt(LocalDateTime.now())
                    .build());
        } else {
            var setting = allowedSetting.get();
            // Cập nhật lại danh sách nếu cấu hình cũ chưa chứa gemini-3.5-flash-lite hoặc còn model lỗi
            if (setting.getSettingValue() == null || !setting.getSettingValue().contains("gemini-3.5-flash-lite") || setting.getSettingValue().contains("gemini-1.5-flash")) {
                setting.setSettingValue(supportedList);
                setting.setUpdatedAt(LocalDateTime.now());
                systemSettingRepository.save(setting);
            }
        }
    }
}
