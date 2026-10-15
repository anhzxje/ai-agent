package prj.anhzxje.aiagent.dto.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Đối tượng DTO phản hồi lỗi chuẩn toàn hệ thống (Standard Error Response):
 * Định dạng thống nhất cho tất cả các thông báo lỗi HTTP trả về client (400, 401, 403, 404, 500).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ErrorResponse {
    private int status;
    private String message;
    private String path;
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
