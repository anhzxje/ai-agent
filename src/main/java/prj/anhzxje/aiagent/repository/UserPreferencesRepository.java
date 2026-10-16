package prj.anhzxje.aiagent.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import prj.anhzxje.aiagent.entity.UserPreferences;

import java.util.Optional;

/**
 * Kho lưu trữ Tùy chọn Cá nhân của Người dùng (UserPreferencesRepository):
 * Quản lý các thiết lập giao diện (giao diện sáng/tối, ngôn ngữ, model Gemini ưa thích).
 */
@Repository
public interface UserPreferencesRepository extends JpaRepository<UserPreferences, Long> {
    /** Tìm cài đặt tùy chọn của người dùng theo mã định danh tài khoản (userId) */
    Optional<UserPreferences> findByUserId(Long userId);

    /** Xóa cài đặt tùy chọn của người dùng theo userId */
    void deleteByUserId(Long userId);
}
