package prj.anhzxje.aiagent.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm thử Cơ chế Tạo và Kiểm tra JWT Token (JwtTokenProviderTest):
 * Kiểm tra việc sinh token HMAC-SHA256, giải mã username và phát hiện token không hợp lệ / sai chữ ký.
 */
class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;
    private final String rawSecret = "1234567890123456789012345678901234567890123456789012345678901234";
    private final String base64Secret = Base64.getEncoder().encodeToString(rawSecret.getBytes());
    private final long expiration = 3600000; // 1 hour

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(base64Secret, expiration);
    }

    @Test
    void testGenerateAndValidateToken_Success() {
        String token = jwtTokenProvider.generateToken("testuser", "ROLE_USER");
        assertNotNull(token);
        assertTrue(jwtTokenProvider.validateToken(token));
        assertEquals("testuser", jwtTokenProvider.getUsername(token));
        assertEquals("ROLE_USER", jwtTokenProvider.getRole(token));
    }

    @Test
    void testValidateToken_InvalidOrMalformed() {
        assertFalse(jwtTokenProvider.validateToken("invalid.token.structure"));
        assertFalse(jwtTokenProvider.validateToken(""));
        assertFalse(jwtTokenProvider.validateToken(null));
    }

    @Test
    void testValidateToken_ExpiredToken() {
        JwtTokenProvider shortLivedProvider = new JwtTokenProvider(base64Secret, -1000);
        String token = shortLivedProvider.generateToken("expiredUser", "ROLE_USER");
        assertFalse(jwtTokenProvider.validateToken(token));
    }
}
