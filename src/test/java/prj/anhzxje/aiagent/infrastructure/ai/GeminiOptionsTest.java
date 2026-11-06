package prj.anhzxje.aiagent.infrastructure.ai;

import org.junit.jupiter.api.Test;
import org.springframework.ai.google.genai.common.GoogleGenAiSafetySetting;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.ai.google.genai.common.GoogleGenAiSafetySetting.HarmCategory.*;
import static org.springframework.ai.google.genai.common.GoogleGenAiSafetySetting.HarmBlockThreshold.BLOCK_ONLY_HIGH;
import static org.springframework.ai.google.genai.common.GoogleGenAiSafetySetting.HarmBlockMethod.PROBABILITY;

class GeminiOptionsTest {
    @Test void preservesAllFourExistingCategoriesAndThresholdsInImmutableOrder() {
        var expected = List.of(
                new GoogleGenAiSafetySetting(HARM_CATEGORY_DANGEROUS_CONTENT, BLOCK_ONLY_HIGH, PROBABILITY),
                new GoogleGenAiSafetySetting(HARM_CATEGORY_HARASSMENT, BLOCK_ONLY_HIGH, PROBABILITY),
                new GoogleGenAiSafetySetting(HARM_CATEGORY_HATE_SPEECH, BLOCK_ONLY_HIGH, PROBABILITY),
                new GoogleGenAiSafetySetting(HARM_CATEGORY_SEXUALLY_EXPLICIT, BLOCK_ONLY_HIGH, PROBABILITY));
        assertEquals(expected, GeminiOptions.settings());
        assertThrows(UnsupportedOperationException.class, () -> GeminiOptions.settings().clear());
    }
    @Test void modelOptionsPreserveExplicitSelectionAndExistingLibraryDefault() {
        var selected = (org.springframework.ai.google.genai.GoogleGenAiChatOptions) GeminiOptions.options("approved-model").build();
        assertEquals("approved-model", selected.getModel());
        assertEquals(GeminiOptions.settings(), selected.getSafetySettings());
        assertEquals("gemini-2.5-flash", GeminiOptions.options(null).build().getModel());
        assertEquals("gemini-2.5-flash", GeminiOptions.options(" ").build().getModel());
    }
}
