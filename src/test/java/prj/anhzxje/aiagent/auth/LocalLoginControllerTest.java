package prj.anhzxje.aiagent.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import prj.anhzxje.aiagent.auth.AuthResponse;
import prj.anhzxje.aiagent.auth.LoginRequest;
import prj.anhzxje.aiagent.auth.AuthService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class LocalLoginControllerTest {
    private final AuthService authService = mock(AuthService.class);
    private final LocalLoginController controller = new LocalLoginController(authService, "local-test-password");

    private MockHttpServletRequest localRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.setLocalAddr("127.0.0.1");
        request.addHeader("Host", "localhost:8080");
        request.addHeader("Origin", "http://localhost:8080");
        return request;
    }

    @Test
    void localLoginUsesNormalAuthenticationWithServerSidePassword() {
        when(authService.loginUser(any())).thenReturn(AuthResponse.builder().token("token").build());
        assertEquals(HttpStatus.OK, controller.login(localRequest(), "1").getStatusCode());
        verify(authService).loginUser(argThat(login -> "admin".equals(login.getUsername())
                && "local-test-password".equals(login.getPassword())));
    }

    @Test
    void rejectsRemoteOrPubliclyBoundServers() {
        MockHttpServletRequest remote = localRequest();
        remote.setRemoteAddr("192.168.1.5");
        assertEquals(HttpStatus.FORBIDDEN, controller.login(remote, "1").getStatusCode());
        MockHttpServletRequest publicServer = localRequest();
        publicServer.setLocalAddr("192.168.1.2");
        assertEquals(HttpStatus.FORBIDDEN, controller.login(publicServer, "1").getStatusCode());
        verifyNoInteractions(authService);
    }

    @Test
    void rejectsCrossOriginRequestsAndProxyForwarding() {
        MockHttpServletRequest foreign = localRequest();
        foreign.removeHeader("Origin");
        foreign.addHeader("Origin", "https://other.example");
        assertEquals(HttpStatus.FORBIDDEN, controller.login(foreign, "1").getStatusCode());
        MockHttpServletRequest proxy = localRequest();
        proxy.addHeader("X-Forwarded-For", "192.168.1.5");
        assertEquals(HttpStatus.FORBIDDEN, controller.login(proxy, "1").getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, controller.login(localRequest(), "").getStatusCode());
        verifyNoInteractions(authService);
    }

    @Test
    void preservesAuthenticationFailures() {
        when(authService.loginUser(any(LoginRequest.class)))
                .thenReturn(AuthResponse.builder().message("Account locked").build());
        assertEquals(HttpStatus.FORBIDDEN, controller.login(localRequest(), "1").getStatusCode());
    }
}
