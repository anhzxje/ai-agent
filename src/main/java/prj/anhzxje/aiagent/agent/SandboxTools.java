package prj.anhzxje.aiagent.agent;

import org.springframework.ai.tool.annotation.Tool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/** Read-only tools. Every requested path is resolved and checked against the project root. */
public final class SandboxTools {
    private static final int MAX_BYTES = 256 * 1024;
    private static final int MAX_OUTPUT = 32 * 1024;
    private static final int MAX_ENTRIES = 2000;
    private static final Set<String> EXCLUDED = Set.of(
            ".git", ".idea", ".vscode", "node_modules", "target", "build", "dist", "out", "venv", ".aws", ".ssh");
    private final Path root;

    public SandboxTools(Path root) {
        try {
            this.root = root.toRealPath();
        } catch (IOException e) {
            throw new IllegalArgumentException("Không thể truy cập thư mục dự án", e);
        }
    }

    public Object tool(String name) {
        return switch (name) {
            case "list_directory" -> new DirectoryTool();
            case "glob" -> new GlobTool();
            case "file_system" -> new ReadTool();
            case "grep" -> new SearchTool();
            default -> throw new IllegalArgumentException("Công cụ không được hỗ trợ: " + name);
        };
    }

    private boolean excluded(Path path) {
        for (Path part : root.relativize(path)) {
            String name = part.toString().toLowerCase(Locale.ROOT);
            if (EXCLUDED.contains(name) || name.equals(".env") || name.startsWith(".env.")) return true;
        }
        return false;
    }

    private Path resolve(String path) throws IOException {
        Path target = root.resolve(path == null || path.isBlank() ? "." : path).normalize();
        if (!target.startsWith(root) || excluded(target)) throw new SecurityException("Đường dẫn không được cấp phép");
        Path real = target.toRealPath();
        if (!real.startsWith(root) || excluded(real)) throw new SecurityException("Đường dẫn không được cấp phép");
        return real;
    }

    private String relative(Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    private String read(Path path) throws IOException {
        Path safe = resolve(path.toString());
        if (!Files.isRegularFile(safe)) throw new IllegalArgumentException("Đường dẫn phải là file");
        byte[] bytes;
        try (var input = Files.newInputStream(safe)) {
            bytes = input.readNBytes(MAX_BYTES + 1);
        }
        if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("File vượt quá giới hạn 256 KiB");
        for (byte value : bytes) if (value == 0) throw new IllegalArgumentException("Không đọc file nhị phân");
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private List<Path> files() throws IOException {
        List<Path> result = new ArrayList<>();
        Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), 20, new SimpleFileVisitor<>() {
            private int visited;
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (++visited > MAX_ENTRIES) return FileVisitResult.TERMINATE;
                return excluded(dir) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (++visited > MAX_ENTRIES) return FileVisitResult.TERMINATE;
                if (attrs.isRegularFile() && !excluded(file)) result.add(file);
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult visitFileFailed(Path file, IOException error) {
                return FileVisitResult.CONTINUE;
            }
        });
        return result;
    }

    public final class DirectoryTool {
        @Tool(name = "list_directory", description = "List up to 200 entries inside the project. Use '.' for its root.")
        public String listDirectory(String path) throws IOException {
            Path dir = resolve(path);
            StringBuilder output = new StringBuilder();
            try (var entries = Files.list(dir)) {
                var iterator = entries.filter(p -> !excluded(p)).limit(200).iterator();
                while (iterator.hasNext() && output.length() < MAX_OUTPUT) {
                    Path entry = iterator.next();
                    try {
                        Path safe = resolve(entry.toString());
                        output.append(relative(entry)).append(Files.isDirectory(safe) ? "/" : "").append('\n');
                    } catch (SecurityException | IOException ignored) { }
                }
            }
            return output + "\n[Giới hạn: 200 mục, 32 KiB]";
        }
    }

    public final class GlobTool {
        @Tool(name = "glob", description = "Find project files by a relative glob such as **/*.java. Bounded to 2000 scanned entries and depth 20.")
        public String glob(String pattern) throws IOException {
            if (pattern == null || pattern.isBlank() || pattern.length() > 512) throw new IllegalArgumentException("Glob không hợp lệ");
            PathMatcher matcher = root.getFileSystem().getPathMatcher("glob:" + pattern);
            PathMatcher rootMatcher = pattern.startsWith("**/") ? root.getFileSystem().getPathMatcher("glob:" + pattern.substring(3)) : matcher;
            StringBuilder output = new StringBuilder();
            for (Path file : files()) {
                Path relative = root.relativize(file);
                if (matcher.matches(relative) || rootMatcher.matches(relative)) output.append(relative(file)).append('\n');
                if (output.length() >= MAX_OUTPUT) break;
            }
            return output + "\n[Giới hạn: 2000 mục quét, độ sâu 20, 32 KiB]";
        }
    }

    public final class ReadTool {
        @Tool(name = "file_system", description = "Read UTF-8 source code inside the project with line numbers. startLine and endLine are one-based; at most 400 lines per call.")
        public String readFile(String path, int startLine, int endLine) throws IOException {
            if (startLine < 1 || endLine < startLine || endLine - (long) startLine >= 400) {
                throw new IllegalArgumentException("Chọn từ 1 đến 400 dòng, bắt đầu từ dòng 1");
            }
            String[] lines = read(resolve(path)).split("\\R", -1);
            StringBuilder output = new StringBuilder();
            for (int i = startLine - 1; i < Math.min(lines.length, endLine); i++) {
                String line = (i + 1) + ": " + lines[i] + "\n";
                if (output.length() + line.length() > MAX_OUTPUT) {
                    output.append("[Đã đạt giới hạn 32 KiB]");
                    break;
                }
                output.append(line);
            }
            return output.toString();
        }
    }

    public final class SearchTool {
        @Tool(name = "grep", description = "Search a literal text string in project files, returning paths and line numbers. This is literal search, not regular expressions; bounded to 2000 scanned entries.")
        public String grep(String query) throws IOException {
            if (query == null || query.isBlank() || query.length() > 512) throw new IllegalArgumentException("Từ khóa không hợp lệ");
            StringBuilder output = new StringBuilder();
            for (Path file : files()) {
                try {
                    String[] lines = read(file).split("\\R");
                    for (int i = 0; i < lines.length; i++) {
                        if (lines[i].contains(query)) {
                            String line = lines[i].length() > 1000 ? lines[i].substring(0, 1000) + "…" : lines[i];
                            output.append(relative(file)).append(':').append(i + 1).append(':').append(line).append('\n');
                            if (output.length() >= MAX_OUTPUT) return output + "[Đã đạt giới hạn kết quả]";
                        }
                    }
                } catch (IOException | IllegalArgumentException | SecurityException ignored) { }
            }
            return output + "\n[Giới hạn: 2000 mục quét, độ sâu 20, 32 KiB]";
        }
    }
}
