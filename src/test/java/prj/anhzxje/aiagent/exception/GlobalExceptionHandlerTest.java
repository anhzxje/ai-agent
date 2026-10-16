package prj.anhzxje.aiagent.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import prj.anhzxje.aiagent.dto.common.ErrorResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm thử Đơn vị cho Bộ xử lý Ngoại lệ Toàn cục (GlobalExceptionHandlerTest):
 * Kiểm tra các tình huống lỗi:
 * 1. 404 ResourceNotFoundException
 * 2. 401 BadCredentialsException
 * 3. 403 AccessDeniedException
 * 4. 409 DataIntegrityViolationException (bảo mật thông tin ràng buộc)
 * 5. 500 Unhandled Exception (che giấu StackTrace/SQL schema nội bộ)
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest();
        request.setRequestURI("/api/test/resource");
    }

    @Test
    void testHandleResourceNotFoundException() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Không tìm thấy dự án");
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleResourceNotFoundException(ex, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().getStatus());
        assertEquals("Không tìm thấy dự án", response.getBody().getMessage());
        assertEquals("/api/test/resource", response.getBody().getPath());
    }

    @Test
    void testHandleBadCredentialsException() {
        BadCredentialsException ex = new BadCredentialsException("Bad credentials");
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleBadCredentialsException(ex, request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(401, response.getBody().getStatus());
        assertEquals("Username hoặc password không chính xác", response.getBody().getMessage());
    }

    @Test
    void testHandleAccessDeniedException() {
        AccessDeniedException ex = new AccessDeniedException("Forbidden action");
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleAccessDeniedException(ex, request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(403, response.getBody().getStatus());
        assertTrue(response.getBody().getMessage().contains("Access Denied"));
    }

    @Test
    void testHandleDataIntegrityViolationException() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("FK constraint failure table projects");
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleDataIntegrityViolationException(ex, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(409, response.getBody().getStatus());
        // Đảm bảo không phơi bày chuỗi SQL gốc "FK constraint failure table projects"
        assertFalse(response.getBody().getMessage().contains("table projects"));
        assertTrue(response.getBody().getMessage().contains("vi phạm ràng buộc dữ liệu"));
    }

    @Test
    void testHandleGlobalException_SanitizesMessage() {
        Exception ex = new RuntimeException("SELECT * FROM users WHERE password = secret - syntax error");
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleGlobalException(ex, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(500, response.getBody().getStatus());
        // Đảm bảo không làm lộ câu lệnh SQL nhạy cảm ra ngoài
        assertFalse(response.getBody().getMessage().contains("SELECT * FROM users"));
        assertEquals("Đã xảy ra lỗi nội bộ trên hệ thống. Vui lòng thử lại sau hoặc liên hệ quản trị viên.", response.getBody().getMessage());
    }
}
