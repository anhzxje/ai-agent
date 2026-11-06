package prj.anhzxje.aiagent.chat;

import java.util.Locale;

/** Deterministic routing for the explicit review command. */
final class ReviewCommand {
    private ReviewCommand() {}
    static boolean isReview(String message) {
        if (message == null) return false;
        String text = message.strip();
        return text.equalsIgnoreCase("/review") || (text.length() > 7
                && text.regionMatches(true, 0, "/review", 0, 7)
                && Character.isWhitespace(text.charAt(7)));
    }
    static String focus(String message) {
        if (!isReview(message)) throw new IllegalArgumentException("Lệnh review không hợp lệ");
        String rest = message.strip().substring(7).strip();
        String first = rest.isEmpty() ? "" : rest.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        return switch (first) {
            case "security", "performance" -> first;
            case "code-quality", "code_quality" -> "code_quality";
            default -> "all";
        };
    }
}
