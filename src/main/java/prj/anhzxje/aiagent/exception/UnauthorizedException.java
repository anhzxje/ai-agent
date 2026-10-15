package prj.anhzxje.aiagent.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Ngoại lệ Không có quyền truy cập / Xác thực thất bại (Unauthorized Exception):
 * Ném ra khi người dùng chưa đăng nhập, token hết hạn hoặc thông tin đăng nhập sai.
 * Được GlobalExceptionHandler bắt và chuyển thành HTTP 401 UNAUTHORIZED.
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
