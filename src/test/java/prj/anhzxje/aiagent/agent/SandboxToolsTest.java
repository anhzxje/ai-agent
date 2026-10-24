package prj.anhzxje.aiagent.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import prj.anhzxje.aiagent.security.ProjectPathPolicy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SandboxToolsTest {
    @TempDir Path root;

    @Test
    void rejectsEmptyAllowlistAndSiblingWithSamePrefix() throws Exception {
        Path project = Files.createDirectory(root.resolve("project"));
        Path sibling = Files.createDirectory(root.resolve("project-other"));
        assertThrows(SecurityException.class, () -> ProjectPathPolicy.requireAllowedDirectory(project.toString(), List.of()));
        assertThrows(SecurityException.class, () -> ProjectPathPolicy.requireAllowedDirectory(sibling.toString(), List.of(project.toString())));
        assertEquals(project.toRealPath(), ProjectPathPolicy.requireAllowedDirectory(project.toString(), List.of(root.toString())));
    }

    @Test
    void allToolsStayInsideProjectAndDoNotReadSecrets() throws Exception {
        Path project = Files.createDirectory(root.resolve("project"));
        Path outside = Files.writeString(root.resolve("outside.txt"), "secret needle");
        Files.writeString(project.resolve(".env"), "secret needle");
        Files.writeString(project.resolve("Hello.java"), "class Hello {}\nneedle\n");
        SandboxTools tools = new SandboxTools(project);
        var reader = (SandboxTools.ReadTool) tools.tool("file_system");
        var directory = (SandboxTools.DirectoryTool) tools.tool("list_directory");
        var glob = (SandboxTools.GlobTool) tools.tool("glob");
        var search = (SandboxTools.SearchTool) tools.tool("grep");
        assertThrows(SecurityException.class, () -> reader.readFile(outside.toString(), 1, 10));
        assertThrows(SecurityException.class, () -> directory.listDirectory(".."));
        assertThrows(SecurityException.class, () -> reader.readFile(".env", 1, 10));
        assertTrue(reader.readFile("Hello.java", 1, 2).contains("2: needle"));
        assertTrue(glob.glob("**/*.java").contains("Hello.java"));
        assertFalse(search.grep("secret").contains("secret needle"));
        assertTrue(search.grep("needle").contains("Hello.java:2:needle"));
        assertFalse(directory.listDirectory(".").contains(".env"));
        assertEquals(1, org.springframework.ai.support.ToolCallbacks.from(reader).length);
    }

    @Test
    void rejectsLinksOutsideProject() throws Exception {
        Path project = Files.createDirectory(root.resolve("project"));
        Path outside = Files.createDirectory(root.resolve("outside"));
        Files.writeString(outside.resolve("secret.txt"), "secret");
        Path link = project.resolve("link");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (java.io.IOException | UnsupportedOperationException e) {
            assumeTrue(false, "OS does not permit symbolic links: " + e.getMessage());
        }
        assertThrows(SecurityException.class, () -> ProjectPathPolicy.requireAllowedDirectory(link.toString(), List.of(project.toString())));
        var reader = (SandboxTools.ReadTool) new SandboxTools(project).tool("file_system");
        assertThrows(SecurityException.class, () -> reader.readFile("link/secret.txt", 1, 10));
    }

    @Test
    void boundsFileReads() throws Exception {
        Files.writeString(root.resolve("large.txt"), "x".repeat(256 * 1024 + 1));
        var reader = (SandboxTools.ReadTool) new SandboxTools(root).tool("file_system");
        assertThrows(IllegalArgumentException.class, () -> reader.readFile("large.txt", 1, 1));
        assertThrows(IllegalArgumentException.class, () -> reader.readFile("large.txt", 1, 1000));
    }
}
