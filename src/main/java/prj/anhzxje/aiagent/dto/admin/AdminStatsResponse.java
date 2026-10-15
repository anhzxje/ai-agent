package prj.anhzxje.aiagent.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Đối tượng DTO Thống kê Toàn diện Hệ thống (Admin Stats Response):
 * Cung cấp số liệu tổng quan về người dùng, dự án, tác vụ AI theo trạng thái và chủng loại cho Dashboard Quản trị.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminStatsResponse {
    private long totalUsers;
    private long totalAdmins;
    private long totalRegularUsers;
    private long totalProjects;
    private long totalTasks;
    private long completedTasks;
    private long pendingOrProcessingTasks;
    private long failedTasks;
    private long reviewTasksCount;
    private long fixTasksCount;
    private long testGenTasksCount;
}
