package prj.anhzxje.aiagent.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Ngoại lệ Tài nguyên Không tìm thấy (Resource Not Found Exception):
 * Ném ra khi không tìm thấy thực thể tương ứng trong CSDL (User, Project, Task, Conversation).
 * Được GlobalExceptionHandler bắt và chuyển thành HTTP 404 NOT_FOUND.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
