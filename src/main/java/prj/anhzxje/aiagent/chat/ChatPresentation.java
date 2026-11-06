package prj.anhzxje.aiagent.chat;

import prj.anhzxje.aiagent.infrastructure.persistence.entity.ChatMessage;
import prj.anhzxje.aiagent.infrastructure.persistence.entity.Project;
import java.util.List;

/** Pure presentation of existing chat context. No provider, repository or transaction access. */
public final class ChatPresentation {
    private ChatPresentation() {}

    /** System prompt định hình phong cách phản hồi của AI trong phiên Chat */
    private static final String CHAT_SYSTEM_PROMPT = """
            Bạn là Soopi - Trợ lý AI Coding Assistant chuyên sâu về lập trình phần mềm, kiến trúc hệ thống và rà soát mã nguồn (Code Review).
            - Trả lời bằng tiếng Việt chuyên nghiệp, súc tích, chính xác và thân thiện.
            - Hướng dẫn giải pháp kỹ thuật, phân tích nguyên nhân lỗi và đề xuất mã nguồn tối ưu có giải thích chi tiết.
            - Khi đưa ra code, hãy định dạng Markdown chuẩn với ngôn ngữ tương ứng (ví dụ: ```java, ```javascript).
            """;


    public static String systemPrompt(Project project) {
        if (project == null) {
            return CHAT_SYSTEM_PROMPT;
        }
        return CHAT_SYSTEM_PROMPT + """

                --- DỰ ÁN MÃ NGUỒN HIỆN TẠI (PROJECT WORKSPACE) ---
                - Tên dự án: %s
                - Thư mục gốc: %s
                - Ngôn ngữ chính: %s
                - Mô tả: %s

                --- NGUYÊN TẮC HOẠT ĐỘNG & CÔNG CỤ (TOOLS) ---
                1. Bạn có các công cụ duyệt tệp (list_directory, glob, file_system, grep) để thao tác trực tiếp trên thư mục dự án `%s`.
                2. Bạn PHẢI LUÔN chủ động dùng các công cụ này để đọc nội dung file mã nguồn thực tế trước khi đưa ra nhận định, giải thích hay sửa code.
                3. Tuyệt đối KHÔNG đoán mò hoặc bịa đặt mã nguồn không tồn tại. Mọi câu trả lời kỹ thuật phải bám sát code thật.
                4. Chỉ đọc và thao tác trong phạm vi thư mục gốc của dự án. Không truy cập các thư mục ngoài dự án.

                --- ĐỊNH DẠNG ĐẦU RA ---
                - Trả về văn bản định dạng Rich Markdown thuần, thân thiện, rõ ràng và có cấu trúc mạch lạc.
                - Sử dụng khối mã nguồn chuẩn với ngôn ngữ tương ứng (ví dụ: ```java, ```sql, ```bash).
                - Không trả về JSON thô trừ khi người dùng yêu cầu rõ ràng.
                """.formatted(
                project.getName(),
                project.getPath() != null ? project.getPath() : "Không xác định",
                project.getLanguage() != null ? project.getLanguage() : "Đa ngôn ngữ",
                project.getDescription() != null ? project.getDescription() : "Không có mô tả",
                project.getPath() != null ? project.getPath() : "."
        );
    }

    /**
     * Tạo tiêu đề ngắn gọn, chuẩn mực cho cuộc hội thoại, loại bỏ các lệnh slash thô và định dạng markdown.
     */
    public static String conversationTitle(String rawMessage, Project project) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return project != null ? "Hội thoại: " + project.getName() : "Hỏi đáp chung";
        }
        String text = rawMessage.trim();

        // 1. Nhận diện các lệnh Slash commands Antigravity
        if (text.startsWith("/review")) {
            String rest = text.substring(7).trim();
            if (rest.isBlank() || rest.toLowerCase().contains("toàn diện mã nguồn")) {
                return project != null ? "Code Review: " + project.getName() : "Code Review";
            }
            return "Review: " + cleanTextForTitle(rest, 22);
        } else if (text.startsWith("/fix")) {
            String rest = text.substring(4).trim();
            if (rest.isBlank() || rest.toLowerCase().contains("chẩn đoán") || rest.toLowerCase().contains("file:")) {
                return project != null ? "Sửa lỗi: " + project.getName() : "Sửa lỗi code";
            }
            return "Fix: " + cleanTextForTitle(rest, 24);
        } else if (text.startsWith("/explain")) {
            String rest = text.substring(8).trim();
            if (rest.isBlank() || rest.toLowerCase().contains("giải thích") || rest.toLowerCase().contains("file:")) {
                return project != null ? "Giải thích: " + project.getName() : "Giải thích code";
            }
            return "Explain: " + cleanTextForTitle(rest, 20);
        } else if (text.startsWith("/test")) {
            String rest = text.substring(5).trim();
            if (rest.isBlank() || rest.toLowerCase().contains("bộ unit tests") || rest.toLowerCase().contains("cho:")) {
                return project != null ? "Unit Test: " + project.getName() : "Sinh Unit Test";
            }
            return "Test: " + cleanTextForTitle(rest, 24);
        }

        return cleanTextForTitle(text, 28);
    }

    private static String cleanTextForTitle(String text, int maxLength) {
        if (text == null) return "Hội thoại";
        // Bỏ ký tự markdown và khoảng trắng thừa, ngắt dòng
        String clean = text.replaceAll("[#*`_~>\\[\\]()\\n\\r]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (clean.isBlank()) return "Hội thoại";
        // Viết hoa chữ cái đầu
        clean = Character.toUpperCase(clean.charAt(0)) + (clean.length() > 1 ? clean.substring(1) : "");
        if (clean.length() <= maxLength) {
            return clean;
        }
        int cutIndex = clean.lastIndexOf(' ', maxLength);
        if (cutIndex > 10) {
            return clean.substring(0, cutIndex) + "...";
        }
        return clean.substring(0, maxLength) + "...";
    }

    public static String promptWithHistory(List<ChatMessage> previousMessages, String message) {
        StringBuilder promptWithContext = new StringBuilder();
        if (!previousMessages.isEmpty()) {
            promptWithContext.append("Đây là lịch sử trao đổi trước đó giữa bạn và người dùng:\n---\n");
            int startIdx = Math.max(0, previousMessages.size() - 10);
            for (int i = startIdx; i < previousMessages.size(); i++) {
                ChatMessage m = previousMessages.get(i);
                String senderLabel = "USER".equalsIgnoreCase(m.getSender()) ? "Người dùng" : "Trợ lý AI";
                promptWithContext.append(senderLabel).append(": ").append(m.getContent()).append("\n\n");
            }
            promptWithContext.append("---\nCâu hỏi/Yêu cầu hiện tại của người dùng:\n");
        }
        promptWithContext.append(message);

        return promptWithContext.toString();
    }
}
