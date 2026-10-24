package prj.anhzxje.aiagent.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Resolves links before comparing paths. An empty allowlist grants no access. */
public final class ProjectPathPolicy {
    private ProjectPathPolicy() {}

    public static Path requireAllowedDirectory(String path, List<String> allowedPaths) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Đường dẫn dự án không được để trống");
        }
        if (allowedPaths == null || allowedPaths.isEmpty()) {
            throw new SecurityException("Chưa cấu hình agent.allowed-paths");
        }
        try {
            Path target = Path.of(path).toRealPath();
            if (!Files.isDirectory(target)) {
                throw new IllegalArgumentException("Đường dẫn dự án phải là thư mục");
            }
            for (String allowed : allowedPaths) {
                if (allowed == null || allowed.isBlank()) continue;
                try {
                    if (target.startsWith(Path.of(allowed).toRealPath())) return target;
                } catch (IOException | java.nio.file.InvalidPathException ignored) {
                    // A missing or invalid root cannot grant access.
                }
            }
            throw new SecurityException("Đường dẫn nằm ngoài thư mục được cấp phép");
        } catch (IOException | java.nio.file.InvalidPathException e) {
            throw new IllegalArgumentException("Không thể truy cập thư mục dự án", e);
        }
    }
}
