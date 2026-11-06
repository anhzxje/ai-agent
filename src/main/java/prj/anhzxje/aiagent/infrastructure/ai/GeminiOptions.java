package prj.anhzxje.aiagent.infrastructure.ai;

import org.springframework.ai.google.genai.common.GoogleGenAiSafetySetting;
import org.springframework.ai.google.genai.common.GoogleGenAiSafetySetting.HarmCategory;
import org.springframework.ai.google.genai.common.GoogleGenAiSafetySetting.HarmBlockThreshold;
import org.springframework.ai.google.genai.common.GoogleGenAiSafetySetting.HarmBlockMethod;
import java.util.List;

/** Existing application safety settings shared by review and chat, without changing provider policy. */
public final class GeminiOptions {
    private GeminiOptions() {}

    private static final List<GoogleGenAiSafetySetting> SETTINGS = List.of(
            new GoogleGenAiSafetySetting(
                    HarmCategory.HARM_CATEGORY_DANGEROUS_CONTENT,
                    HarmBlockThreshold.BLOCK_ONLY_HIGH,
                    HarmBlockMethod.PROBABILITY
            ),
            new GoogleGenAiSafetySetting(
                    HarmCategory.HARM_CATEGORY_HARASSMENT,
                    HarmBlockThreshold.BLOCK_ONLY_HIGH,
                    HarmBlockMethod.PROBABILITY
            ),
            new GoogleGenAiSafetySetting(
                    HarmCategory.HARM_CATEGORY_HATE_SPEECH,
                    HarmBlockThreshold.BLOCK_ONLY_HIGH,
                    HarmBlockMethod.PROBABILITY
            ),
            new GoogleGenAiSafetySetting(
                    HarmCategory.HARM_CATEGORY_SEXUALLY_EXPLICIT,
                    HarmBlockThreshold.BLOCK_ONLY_HIGH,
                    HarmBlockMethod.PROBABILITY
            )
    );

    public static List<GoogleGenAiSafetySetting> settings() {
        return SETTINGS;
    }
    /** Provider-neutral contract used by chat and review orchestration. */
    public static org.springframework.ai.chat.prompt.ChatOptions.Builder<?> options(String model) {
        var options = org.springframework.ai.google.genai.GoogleGenAiChatOptions.builder()
                .safetySettings(SETTINGS);
        if (model != null && !model.isBlank()) options.model(model);
        return options;
    }
}
