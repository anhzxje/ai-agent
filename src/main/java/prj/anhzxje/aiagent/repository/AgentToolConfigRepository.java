package prj.anhzxje.aiagent.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import prj.anhzxje.aiagent.entity.AgentToolConfig;

import java.util.List;
import java.util.Optional;

/**
 * Kho lưu trữ Cấu hình Công cụ Agent (AgentToolConfigRepository):
 * Quản lý các truy vấn liên quan đến trạng thái kích hoạt của Tool (ListDirectory, Glob, FileSystem, Grep).
 */
@Repository
public interface AgentToolConfigRepository extends JpaRepository<AgentToolConfig, Long> {
    /** Tìm cấu hình công cụ theo định danh duy nhất (ví dụ: "list_directory") */
    Optional<AgentToolConfig> findByToolName(String toolName);

    /** Lấy danh sách toàn bộ các công cụ đang ở trạng thái kích hoạt (enabled = true) */
    List<AgentToolConfig> findByEnabledTrue();
}
