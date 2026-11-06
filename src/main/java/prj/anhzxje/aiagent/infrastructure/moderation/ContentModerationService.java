package prj.anhzxje.aiagent.infrastructure.moderation;

import prj.anhzxje.aiagent.infrastructure.audit.AuditLogService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Dịch vụ Tự động Kiểm duyệt Nội dung Tin nhắn (ContentModerationService):
 * Đảm bảo an toàn nội dung trước khi chuyển tiếp tới cơ sở dữ liệu và AI Agent.
 *
 * Cơ chế kiểm duyệt bao gồm:
 * 1. Phát hiện rò rỉ secret / khóa nhạy cảm (Google API key, Bearer tokens, OpenAI keys, AWS keys, Private keys).
 * 2. Phát hiện Prompt Injection và câu lệnh thao túng / phá hoại hệ thống (SQL DDL/DML phá hủy, vượt rào chỉ dẫn).
 * 3. Phát hiện từ khóa thô tục, bạo lực, khủng bố, tự hại hoặc thù địch (Tiếng Việt & Tiếng Anh).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentModerationService {

    private final AuditLogService auditLogService;

    // Pattern nhận diện rò rỉ thông tin xác thực / secrets / khóa API
    private static final List<Pattern> SECRET_PATTERNS = List.of(
            Pattern.compile("AIza[0-9A-Za-z-_]{35}"), // Google API Key
            Pattern.compile("Bearer\\s+[A-Za-z0-9_\\-\\.]{20,}", Pattern.CASE_INSENSITIVE), // Bearer Token
            Pattern.compile("sk-(?:proj-)?[a-zA-Z0-9_-]{20,}"), // OpenAI API Key
            Pattern.compile("(?:AKIA|ABIA|ACCA|ASIA)[0-9A-Z]{16}"), // AWS Access Key
            Pattern.compile("-----BEGIN (?:[A-Z0-9_-]+ )?PRIVATE KEY-----") // Private Keys
    );

    // Pattern nhận diện Prompt Injection và cố ý thao túng / phá hủy hệ thống
    private static final Pattern PROMPT_INJECTION_PATTERN = Pattern.compile(
            "(?i)(ignore\\s+(all\\s+)?previous\\s+instructions" +
            "|disregard\\s+(all\\s+)?previous\\s+instructions" +
            "|bypass\\s+security" +
            "|reveal\\s+(the\\s+)?system\\s+prompt" +
            "|show\\s+(me\\s+)?(the\\s+)?system\\s+prompt" +
            "|system\\s+prompt\\s+leak" +
            "|override\\s+system\\s+prompt" +
            "|jailbreak)"
    );

    // Pattern nhận diện câu lệnh thao túng / phá hoại cơ sở dữ liệu
    private static final Pattern DB_DESTRUCTION_PATTERN = Pattern.compile(
            "(?i)\\b(drop\\s+table|delete\\s+database|drop\\s+database|truncate\\s+table)\\b"
    );

    // Pattern nhận diện từ cấm, thô tục, nguy hại (Tiếng Việt & Tiếng Anh)
    private static final Pattern BANNED_OR_HARMFUL_PATTERN = Pattern.compile(
            "(?i)(?:^|[\\s\\p{Punct}])(fuck|bitch|terrorist|suicide|make\\s+bomb|" +
            "đm|địt|lồn|cặc|chết\\s+tiệt|chế\\s+tạo\\s+bom|tự\\s+tử|khủng\\s+bố)(?:$|[\\s\\p{Punct}])",
            Pattern.UNICODE_CHARACTER_CLASS
    );

    // Pattern nhận diện tấn công XSS và mã độc JavaScript / HTML nhúng
    private static final Pattern XSS_ATTACK_PATTERN = Pattern.compile(
            "(?i)(<script\\b[^>]*>.*?</script>|<script\\b|javascript:\\s*|" +
            "\\bon(?:error|load|click|mouseover|focus|blur|change|submit)\\s*=|" +
            "<(?:iframe|object|embed)\\b|document\\.cookie|document\\.write|\\beval\\s*\\()",
            Pattern.DOTALL
    );

    /**
     * Xác thực nội dung tin nhắn của người dùng.
     * Nếu vi phạm, ghi nhận vào Audit Log và ném ngoại lệ IllegalArgumentException.
     *
     * @param content  nội dung tin nhắn cần kiểm duyệt
     * @param username tên người dùng gửi tin nhắn
     * @throws IllegalArgumentException khi nội dung vi phạm quy chuẩn kiểm duyệt
     */
    public void validateContent(String content, String username) {
        if (content == null || content.isBlank()) {
            return;
        }

        // 1. Kiểm tra rò rỉ secret / khóa bí mật
        for (Pattern pattern : SECRET_PATTERNS) {
            if (pattern.matcher(content).find()) {
                handleViolation(username, "Phát hiện rò rỉ secret / khóa nhạy cảm");
            }
        }

        // 2. Kiểm tra Prompt Injection
        if (PROMPT_INJECTION_PATTERN.matcher(content).find()) {
            handleViolation(username, "Phát hiện hành vi Prompt Injection hoặc can thiệp an toàn hệ thống");
        }

        // 3. Kiểm tra câu lệnh phá hoại CSDL
        if (DB_DESTRUCTION_PATTERN.matcher(content).find()) {
            handleViolation(username, "Phát hiện câu lệnh phá hoại cơ sở dữ liệu");
        }

        // 4. Kiểm tra từ cấm / nhạy cảm / nguy hại
        if (BANNED_OR_HARMFUL_PATTERN.matcher(content).find()) {
            handleViolation(username, "Phát hiện từ khóa cấm, thô tục hoặc nguy hại");
        }

        // 5. Kiểm tra tấn công Cross-Site Scripting (XSS) và mã độc nhúng
        if (XSS_ATTACK_PATTERN.matcher(content).find()) {
            handleViolation(username, "Phát hiện mã độc Cross-Site Scripting (XSS)");
        }
    }

    private void handleViolation(String username, String reason) {
        String logUser = (username != null && !username.isBlank()) ? username : "ANONYMOUS";
        log.warn("Nội dung tin nhắn từ người dùng '{}' bị từ chối kiểm duyệt. Lý do: {}", logUser, reason);
        auditLogService.log(logUser, "CONTENT_VIOLATION", "Vi phạm kiểm duyệt nội dung: " + reason);
        throw new IllegalArgumentException("Tin nhắn bị từ chối do vi phạm quy chuẩn kiểm duyệt nội dung: " + reason);
    }
}
