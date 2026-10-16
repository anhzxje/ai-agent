/**
 * ============================================================================
 * MARKDOWN UTILITIES - ĐỊNH DẠNG VĂN BẢN VÀ CODE SNIPPET CHO AI CHATBOT
 * ============================================================================
 * Chức năng:
 * 1. Chống tấn công XSS bằng cơ chế escape HTML an toàn.
 * 2. Phân tích cú pháp code blocks (```language\n...```) kèm thanh công cụ header
 *    hiển thị tên ngôn ngữ lập trình và nút "Sao chép" (Copy code).
 * 3. Hỗ trợ inline code (`code`), chữ đậm (**bold**), chữ nghiêng (*italic*).
 * 4. Hỗ trợ tiêu đề các cấp (h1, h2, h3), danh sách bullet points (ul/li).
 * ============================================================================
 */
(function() {
  function formatMarkdown(content) {
    if (!content) return "";
    let html = content;

    // 1. Escape HTML first to prevent XSS
    html = html
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;");

    // 2. Multi-line code blocks with language header and copy button
    html = html.replace(/```([a-zA-Z0-9_-]*)\n([\s\S]*?)```/g, (match, lang, code) => {
      const language = lang ? lang.toLowerCase() : "code";
      const cleanCode = code.replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/&amp;/g, "&");
      const encodedCode = encodeURIComponent(cleanCode);
      return `
        <div class="chat-code-block">
          <div class="code-header-bar">
            <span>${language}</span>
            <button class="btn-code-copy" onclick="window.MarkdownUtils.copyCodeSnippet(this, decodeURIComponent('${encodedCode}'))">Sao chép</button>
          </div>
          <pre><code class="language-${language}">${code}</code></pre>
        </div>
      `;
    });

    // 3. Inline code
    html = html.replace(/`([^`]+)`/g, "<code>$1</code>");

    // 4. Bold text
    html = html.replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>");

    // 5. Italic text
    html = html.replace(/\*([^*]+)\*/g, "<em>$1</em>");

    // 6. Headings (h1, h2, h3)
    html = html.replace(/^### (.*$)/gim, "<h3>$1</h3>");
    html = html.replace(/^## (.*$)/gim, "<h2>$1</h2>");
    html = html.replace(/^# (.*$)/gim, "<h1>$1</h1>");

    // 7. Bullet point lists
    html = html.replace(/^\s*[-*]\s+(.*$)/gim, "<li>$1</li>");
    html = html.replace(/(<li>.*<\/li>)/gims, "<ul>$1</ul>");

    // 8. Paragraphs and line breaks
    html = html.replace(/\n\n/g, "</p><p>");
    html = html.replace(/\n/g, "<br>");

    return `<p>${html}</p>`;
  }

  // Helper copy code snippet to clipboard with visual feedback
  function copyCodeSnippet(btn, code) {
    if (!navigator.clipboard) return;
    navigator.clipboard.writeText(code).then(() => {
      const orig = btn.textContent;
      btn.textContent = "Đã chép!";
      setTimeout(() => { btn.textContent = orig; }, 2000);
    }).catch(e => console.warn("Lỗi sao chép clipboard:", e));
  }

  // Export to window
  window.MarkdownUtils = {
    formatMarkdown,
    copyCodeSnippet
  };
  // Backward compatibility alias for existing inline onclick calls
  window.copyCodeSnippet = copyCodeSnippet;
})();
