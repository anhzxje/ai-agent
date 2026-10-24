package prj.anhzxje.aiagent.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import prj.anhzxje.aiagent.dto.admin.AgentToolDto;
import prj.anhzxje.aiagent.entity.AgentToolConfig;
import prj.anhzxje.aiagent.exception.ResourceNotFoundException;
import prj.anhzxje.aiagent.repository.AgentToolConfigRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Kiểm thử Đơn vị cho Tầng Quản lý Công cụ Agent (ToolManagerServiceTest):
 * Kiểm tra:
 * 1. Bật/tắt công cụ và ghi vết kiểm toán.
 * 2. Master switch: Phân giải công cụ dựa trên whitelist của Admin.
 * 3. Xử lý khi công cụ không tồn tại.
 */
@ExtendWith(MockitoExtension.class)
class ToolManagerServiceTest {

    @Mock
    private AgentToolConfigRepository toolConfigRepository;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private ToolManagerService toolManagerService;

    @TempDir
    private Path tempDir;

    private AgentToolConfig listDirTool;
    private AgentToolConfig grepTool;

    @BeforeEach
    void setUp() {
        org.springframework.test.util.ReflectionTestUtils.setField(toolManagerService, "allowedPaths", List.of(System.getProperty("user.dir")));
        listDirTool = AgentToolConfig.builder()
                .id(1L)
                .toolName("list_directory")
                .displayName("List Directory")
                .enabled(true)
                .updatedAt(LocalDateTime.now())
                .build();

        grepTool = AgentToolConfig.builder()
                .id(2L)
                .toolName("grep")
                .displayName("Grep Tool")
                .enabled(false)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void testGetAllTools() {
        when(toolConfigRepository.findAll()).thenReturn(List.of(listDirTool, grepTool));

        List<AgentToolDto> tools = toolManagerService.getAllTools();

        assertEquals(2, tools.size());
        assertEquals("list_directory", tools.get(0).getToolName());
        assertTrue(tools.get(0).getEnabled());
        assertFalse(tools.get(1).getEnabled());
    }

    @Test
    void testSetToolStatus_Success() {
        when(toolConfigRepository.findByToolName("grep")).thenReturn(Optional.of(grepTool));
        when(toolConfigRepository.save(any(AgentToolConfig.class))).thenAnswer(i -> i.getArgument(0));

        AgentToolDto dto = toolManagerService.setToolStatus("grep", true, "admin");

        assertNotNull(dto);
        assertTrue(dto.getEnabled());
        verify(auditLogService, times(1)).log(eq("admin"), eq("ENABLE_TOOL"), anyString());
    }

    @Test
    void testSetToolStatus_NotFound_ThrowsException() {
        when(toolConfigRepository.findByToolName("unknown")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                toolManagerService.setToolStatus("unknown", true, "admin"));
    }

    @Test
    void testResolveActiveTools_EnforcesAdminMasterSwitch() {
        // Admin chỉ bật list_directory, grep bị tắt
        when(toolConfigRepository.findByEnabledTrue()).thenReturn(List.of(listDirTool));

        // Người dùng yêu cầu cả list_directory VÀ grep
        List<Object> tools = toolManagerService.resolveActiveTools(List.of("list_directory", "grep"), System.getProperty("user.dir"));

        // Chỉ có list_directory được cấp phép, grep bị loại bỏ
        assertEquals(1, tools.size());
    }

    @Test
    void testResolveActiveTools_WithAllowedSandboxPath() {
        when(toolConfigRepository.findByEnabledTrue()).thenReturn(List.of(listDirTool));
        org.springframework.test.util.ReflectionTestUtils.setField(toolManagerService, "allowedPaths",
                List.of(System.getProperty("user.dir")));

        List<Object> tools = toolManagerService.resolveActiveTools(
                List.of("list_directory"), System.getProperty("user.dir"));

        assertNotNull(tools);
        assertEquals(1, tools.size());
    }

    @Test
    void testResolveActiveTools_WithDisallowedSandboxPath_ThrowsSecurityException() throws Exception {
        Path allowedDir = Files.createDirectories(tempDir.resolve("allowed"));
        Path disallowedDir = Files.createDirectories(tempDir.resolve("disallowed"));
        org.springframework.test.util.ReflectionTestUtils.setField(toolManagerService, "allowedPaths",
                List.of(allowedDir.toString()));

        assertThrows(SecurityException.class, () ->
                toolManagerService.resolveActiveTools(List.of("list_directory"), disallowedDir.toString()));
    }

    @Test
    void testResolveActiveTools_WithPathTraversal_ThrowsSecurityException() throws Exception {
        Path allowedDir = Files.createDirectories(tempDir.resolve("parent").resolve("allowed"));
        org.springframework.test.util.ReflectionTestUtils.setField(toolManagerService, "allowedPaths",
                List.of(allowedDir.toString()));

        assertThrows(SecurityException.class, () ->
                toolManagerService.resolveActiveTools(List.of("list_directory"), allowedDir.getParent().toString()));
    }
}
