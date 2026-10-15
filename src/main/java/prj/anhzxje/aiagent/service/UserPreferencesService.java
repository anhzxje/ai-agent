package prj.anhzxje.aiagent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import prj.anhzxje.aiagent.dto.user.UserPreferencesRequest;
import prj.anhzxje.aiagent.dto.user.UserPreferencesResponse;
import prj.anhzxje.aiagent.entity.User;
import prj.anhzxje.aiagent.entity.UserPreferences;
import prj.anhzxje.aiagent.repository.UserPreferencesRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Dịch vụ Tùy chọn Cá nhân của Người dùng (UserPreferencesService):
 * Chịu trách nhiệm:
 * 1. Truy xuất thông tin cấu hình giao diện (ngôn ngữ vi/en, theme sáng/tối) và model Gemini ưa thích của user.
 * 2. Cập nhật và kiểm tra tính hợp lệ của model người dùng chọn (phải thuộc danh sách allowedModels do Admin quy định).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserPreferencesService {

    private final UserPreferencesRepository userPreferencesRepository;
    private final SystemConfigService systemConfigService;

    /**
     * Lấy cấu hình tùy chọn của người dùng hiện tại (nếu chưa có trong CSDL sẽ trả về mặc định).
     * Quy trình:
     * - Bước 1: Tra cứu bản ghi UserPreferences theo userId của người dùng hiện tại.
     * - Bước 2: Nếu người dùng mới (chưa có bản ghi): Cung cấp giá trị mặc định hệ thống:
     *     + language: "vi" (Tiếng Việt)
     *     + theme: "dark" (Giao diện tối hiện đại)
     *     + preferredModel: Model đang kích hoạt trong hệ thống (systemConfigService.getActiveModel()).
     * - Bước 3: Nếu đã có cấu hình: Đóng gói sang DTO UserPreferencesResponse trả về.
     */
    @Transactional(readOnly = true)
    public UserPreferencesResponse getPreferences(User user) {
        // Bước 1: Tra cứu bản ghi trong CSDL
        UserPreferences prefs = userPreferencesRepository.findByUserId(user.getId())
                .orElse(null);

        // Bước 2: Thiết lập giá trị mặc định nếu chưa lưu tùy chọn trước đây
        if (prefs == null) {
            return UserPreferencesResponse.builder()
                    .language("vi")
                    .theme("dark")
                    .preferredModel(systemConfigService.getActiveModel())
                    .updatedAt(LocalDateTime.now())
                    .build();
        }

        // Bước 3: Trả về thông số đã được người dùng tùy biến
        return UserPreferencesResponse.builder()
                .language(prefs.getLanguage())
                .theme(prefs.getTheme())
                .preferredModel(prefs.getPreferredModel())
                .updatedAt(prefs.getUpdatedAt())
                .build();
    }

    /**
     * Cập nhật tùy chọn cá nhân cho người dùng với ràng buộc bảo mật danh sách model cho phép.
     * Quy trình:
     * - Bước 1: Tìm cấu hình cũ hoặc tạo đối tượng mới gắn với người dùng hiện tại.
     * - Bước 2: Cập nhật ngôn ngữ và giao diện (chuẩn hóa trim & lowercase).
     * - Bước 3: Ràng buộc an toàn (Security Guard): Kiểm tra model người dùng chọn có nằm trong
     *           danh sách allowedModels được Quản trị viên cấp phép hay không.
     *           Nếu không hợp lệ -> ném IllegalArgumentException chặn ngay lập tức.
     * - Bước 4: Cập nhật mốc thời gian updatedAt và lưu xuống CSDL.
     * - Bước 5: Trả về DTO phản ánh thiết lập mới.
     */
    @Transactional
    public UserPreferencesResponse updatePreferences(User user, UserPreferencesRequest req) {
        // Bước 1: Tìm bản ghi hiện có hoặc khởi tạo mới
        UserPreferences prefs = userPreferencesRepository.findByUserId(user.getId())
                .orElse(UserPreferences.builder().user(user).build());

        // Bước 2: Cập nhật ngôn ngữ nếu có thay đổi
        if (req.getLanguage() != null && !req.getLanguage().isBlank()) {
            prefs.setLanguage(req.getLanguage().trim().toLowerCase());
        }

        // Cập nhật chủ đề giao diện (theme)
        if (req.getTheme() != null && !req.getTheme().isBlank()) {
            prefs.setTheme(req.getTheme().trim().toLowerCase());
        }

        // Bước 3: Kiểm tra tính hợp lệ của model AI yêu cầu
        if (req.getPreferredModel() != null && !req.getPreferredModel().isBlank()) {
            String requestedModel = req.getPreferredModel().trim();
            List<String> allowed = systemConfigService.getAllowedModels();
            if (!allowed.contains(requestedModel)) {
                throw new IllegalArgumentException("Model '" + requestedModel + "' không nằm trong danh sách model được Quản trị viên cho phép: " + allowed);
            }
            prefs.setPreferredModel(requestedModel);
        }

        // Bước 4: Lưu cập nhật vào CSDL
        prefs.setUpdatedAt(LocalDateTime.now());
        UserPreferences saved = userPreferencesRepository.save(prefs);

        // Bước 5: Trả về DTO kết quả
        return UserPreferencesResponse.builder()
                .language(saved.getLanguage())
                .theme(saved.getTheme())
                .preferredModel(saved.getPreferredModel())
                .updatedAt(saved.getUpdatedAt())
                .build();
    }
}
