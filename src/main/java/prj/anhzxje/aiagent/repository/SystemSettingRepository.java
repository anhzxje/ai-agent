package prj.anhzxje.aiagent.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import prj.anhzxje.aiagent.entity.SystemSetting;

import java.util.Optional;

/**
 * Kho lưu trữ Cài đặt Hệ thống (SystemSettingRepository):
 * Quản lý các cấu hình động trong CSDL như Gemini model đang kích hoạt, API Key và danh sách model cho phép.
 */
@Repository
public interface SystemSettingRepository extends JpaRepository<SystemSetting, Long> {
    /** Tìm cài đặt theo khóa định danh duy nhất (ví dụ: "gemini.model") */
    Optional<SystemSetting> findBySettingKey(String settingKey);
}
