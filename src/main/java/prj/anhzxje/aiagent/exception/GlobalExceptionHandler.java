package prj.anhzxje.aiagent.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import prj.anhzxje.aiagent.dto.common.ErrorResponse;

import java.time.LocalDateTime;

/**
 * Bộ xử lý ngoại lệ tập trung toàn ứng dụng (Centralized Global Exception Handler):
 * Bắt toàn bộ các exception xảy ra trong tầng Controller và chuyển đổi thành cấu trúc JSON ErrorResponse chuẩn:
 * - 404 Not Found: Khi không tìm thấy tài nguyên (ResourceNotFoundException).
 * - 401 Unauthorized: Sai thông tin đăng nhập hoặc chưa truyền Bearer Token.
 * - 403 Forbidden: Không có quyền truy cập (AccessDeniedException, tài khoản bị khóa).
 * - 400 Bad Request: Dữ liệu đầu vào không hợp lệ (MethodArgumentNotValidException, IllegalArgumentException).
 * - 409 Conflict: Lỗi xung đột hoặc ràng buộc dữ liệu (DataIntegrityViolationException).
 * - 500 Internal Server Error: Các lỗi ngoại lệ chưa lường trước (che giấu thông tin nội bộ).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ErrorResponse> handleSandboxException(SecurityException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse.builder()
                .status(403).message(ex.getMessage()).path(request.getRequestURI())
                .timestamp(LocalDateTime.now()).build());
    }

    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleMalformedRequest(Exception ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ErrorResponse.builder()
                .status(400).message("Dữ liệu yêu cầu không hợp lệ").path(request.getRequestURI())
                .timestamp(LocalDateTime.now()).build());
    }

    /**
     * Xử lý lỗi không tìm thấy tài nguyên (404 Not Found).
     * Phát sinh khi: User tìm Task, Project, Conversation hoặc User không tồn tại hoặc không thuộc quyền sở hữu.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(ResourceNotFoundException ex, HttpServletRequest request) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(HttpStatus.NOT_FOUND.value())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    /**
     * Xử lý lỗi sai tên đăng nhập hoặc mật khẩu (401 Unauthorized).
     * Phát sinh từ Spring Security AuthenticationManager khi so khớp hash mật khẩu BCrypt không thành công.
     */
    @ExceptionHandler(org.springframework.security.authentication.BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentialsException(
            org.springframework.security.authentication.BadCredentialsException ex,
            HttpServletRequest request) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(HttpStatus.UNAUTHORIZED.value())
                .message("Username hoặc password không chính xác")
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.UNAUTHORIZED);
    }

    /**
     * Xử lý lỗi chưa đăng nhập hoặc token không hợp lệ (401 Unauthorized).
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedException(UnauthorizedException ex, HttpServletRequest request) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(HttpStatus.UNAUTHORIZED.value())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.UNAUTHORIZED);
    }

    /**
     * Xử lý lỗi từ chối truy cập do thiếu quyền hạn (403 Forbidden).
     * Phát sinh khi user thông thường cố tình gọi các API quản trị (@PreAuthorize("hasRole('ADMIN')")).
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex, HttpServletRequest request) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(HttpStatus.FORBIDDEN.value())
                .message("Access Denied: " + ex.getMessage())
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
    }

    /**
     * Xử lý lỗi dữ liệu đầu vào không hợp lệ khi kiểm tra Bean Validation @Valid (400 Bad Request).
     * Trích xuất thông báo lỗi từ FieldError đầu tiên để trả về thông điệp dễ hiểu cho người dùng.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse(ex.getMessage());
                
        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .message(message)
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Xử lý lỗi tài khoản đang bị khóa bởi Quản trị viên (403 Forbidden).
     */
    @ExceptionHandler(org.springframework.security.authentication.LockedException.class)
    public ResponseEntity<ErrorResponse> handleLockedException(
            org.springframework.security.authentication.LockedException ex,
            HttpServletRequest request) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(HttpStatus.FORBIDDEN.value())
                .message("Tài khoản của bạn đã bị khóa bởi Quản trị viên")
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
    }

    /**
     * Xử lý lỗi tham số truyền vào không hợp lệ logic nghiệp vụ (400 Bad Request).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex,
            HttpServletRequest request) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Xử lý lỗi trạng thái đối tượng không phù hợp thao tác (400 Bad Request).
     * Ví dụ: Hủy một tác vụ đã hoàn thành hoặc đã thất bại.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalStateException(
            IllegalStateException ex,
            HttpServletRequest request) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Xử lý lỗi vi phạm ràng buộc dữ liệu toàn vẹn hoặc trùng lặp bản ghi (409 Conflict).
     * Phát sinh khi vi phạm khóa ngoại, ràng buộc unique trong CSDL MySQL.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolationException(
            DataIntegrityViolationException ex,
            HttpServletRequest request) {
        log.warn("Lỗi vi phạm ràng buộc dữ liệu tại {}: {}", request.getRequestURI(), ex.getMessage());
        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(HttpStatus.CONFLICT.value())
                .message("Thao tác không thể hoàn tất do vi phạm ràng buộc dữ liệu hoặc trùng lặp bản ghi.")
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }

    /**
     * Bắt tất cả các ngoại lệ hệ thống còn lại chưa được phân loại cụ thể (500 Internal Server Error).
     * Che giấu thông tin kỹ thuật nội bộ (StackTrace, SQL Schema) để tránh lỗ hổng Information Disclosure.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(Exception ex, HttpServletRequest request) {
        log.error("Lỗi hệ thống chưa phân loại tại {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .message("Đã xảy ra lỗi nội bộ trên hệ thống. Vui lòng thử lại sau hoặc liên hệ quản trị viên.")
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
