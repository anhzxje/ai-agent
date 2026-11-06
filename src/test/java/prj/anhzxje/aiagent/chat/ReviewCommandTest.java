package prj.anhzxje.aiagent.chat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReviewCommandTest {
    @Test
    void recognizesOnlyWholeReviewSlashCommandAndExtractsFocus() throws Exception {
        Class<?> router;
        try { router = Class.forName("prj.anhzxje.aiagent.chat.ReviewCommand"); }
        catch (ClassNotFoundException e) { fail("deterministic review router is missing"); return; }
        var isReview = router.getDeclaredMethod("isReview", String.class);
        var focus = router.getDeclaredMethod("focus", String.class);
        isReview.setAccessible(true);
        focus.setAccessible(true);
        assertEquals(true, isReview.invoke(null, "/review security examine auth"));
        assertEquals(false, isReview.invoke(null, "/reviewing the code"));
        assertEquals(false, isReview.invoke(null, "please review the code"));
        assertEquals("security", focus.invoke(null, "/review security examine auth"));
        assertEquals("code_quality", focus.invoke(null, "/review code-quality"));
        assertEquals("all", focus.invoke(null, "/review examine auth"));
    }
}
