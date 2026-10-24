/** Escaping and a deliberately small Markdown renderer for untrusted AI output. */
(function() {
  function escapeHtml(value) {
    return String(value ?? "").replace(/[&<>"']/g, char => ({
      "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
    })[char]);
  }

  function inline(text) {
    return text.split(/(`[^`]+`)/g).map(part => {
      if (part.startsWith("`") && part.endsWith("`")) {
        return `<code>${escapeHtml(part.slice(1, -1))}</code>`;
      }
      return escapeHtml(part)
        .replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>")
        .replace(/\*([^*]+)\*/g, "<em>$1</em>");
    }).join("");
  }

  function prose(text) {
    let result = "";
    let inList = false;
    for (const line of text.split("\n")) {
      const item = line.match(/^\s*[-*]\s+(.+)$/);
      if (item) {
        if (!inList) result += "<ul>";
        inList = true;
        result += `<li>${inline(item[1])}</li>`;
        continue;
      }
      if (inList) { result += "</ul>"; inList = false; }
      const heading = line.match(/^(#{1,3})\s+(.+)$/);
      if (heading) {
        const level = heading[1].length;
        result += `<h${level}>${inline(heading[2])}</h${level}>`;
      } else if (line.trim()) {
        result += `<p>${inline(line)}</p>`;
      }
    }
    return result + (inList ? "</ul>" : "");
  }

  function formatMarkdown(content) {
    if (content == null) return "";
    const text = String(content).replace(/\r\n?/g, "\n");
    const fences = /```([a-zA-Z0-9_-]*)\n([\s\S]*?)```/g;
    let html = "";
    let cursor = 0;
    for (const match of text.matchAll(fences)) {
      html += prose(text.slice(cursor, match.index));
      const language = (match[1] || "code").toLowerCase();
      html += `<div class="chat-code-block"><div class="code-header-bar"><span>${language}</span>
        <button class="btn-code-copy" onclick="window.MarkdownUtils.copyCodeSnippet(this)">Sao chép</button>
        </div><pre><code class="language-${language}">${escapeHtml(match[2])}</code></pre></div>`;
      cursor = match.index + match[0].length;
    }
    return html + prose(text.slice(cursor));
  }

  function copyCodeSnippet(btn, code) {
    if (!navigator.clipboard) return;
    const text = code ?? btn.closest(".chat-code-block")?.querySelector("pre code")?.textContent ?? "";
    navigator.clipboard.writeText(text).then(() => {
      const original = btn.textContent;
      btn.textContent = "Đã chép!";
      setTimeout(() => { btn.textContent = original; }, 2000);
    }).catch(e => console.warn("Lỗi sao chép clipboard:", e));
  }

  window.MarkdownUtils = { escapeHtml, formatMarkdown, copyCodeSnippet };
  window.copyCodeSnippet = copyCodeSnippet;
})();
