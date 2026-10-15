package prj.anhzxje.aiagent.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import prj.anhzxje.aiagent.enums.Role;

import java.time.LocalDateTime;

/**
 * Đối tượng DTO tóm tắt Thành viên Hệ thống cho Admin (User Summary Response):
 * Trả về danh sách thành viên kèm số lượng dự án đã mở, số tác vụ đã tạo và trạng thái khóa tài khoản.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSummaryResponse {
    private Long id;
    private String username;
    private String email;
    private Role role;
    private LocalDateTime createdAt;
    private Boolean isLocked;
    private long projectCount;
    private long taskCount;
}
