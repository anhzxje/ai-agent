/**
 * ============================================================================
 * CHAT VIEW MODULE - KHÔNG GIAN TRÒ CHUYỆN & AI CODING ASSISTANT
 * ============================================================================
 * Chức năng:
 * 1. Quản lý danh sách các cuộc hội thoại (Chat Conversations) theo từng người dùng.
 * 2. Hiển thị dòng tin nhắn thời gian thực (User & AI Agent), auto-scroll, typing indicator.
 * 3. Hỗ trợ định dạng Rich Markdown kèm code blocks và copy snippet.
 * 4. Hỗ trợ hiển thị trực quan Review Findings (File, Line, Severity, Suggestion Diff).
 * 5. Tự động liên kết Context dự án (Project Context) khi trao đổi với Google Gemini.
 * ============================================================================
 */
(function() {
  const escapeHtml = window.MarkdownUtils.escapeHtml;

  /**
   * Render danh sách các phát hiện lỗi / bảo mật từ kết quả Code Review (B5-03).
   *
   * @param {Array} findings mảng các đối tượng finding { severity, file, line, title, description, suggestion }
   * @returns {string} chuỗi HTML trực quan các thẻ thẻ phát hiện
   */
  function renderReviewFindingsHtml(findings) {
    if (!Array.isArray(findings) || findings.length === 0) return "";

    return `
      <div class="review-findings-panel" style="margin-top: 14px; display: flex; flex-direction: column; gap: 10px;">
        <div style="font-weight: 600; font-size: 13px; color: var(--text-primary); display: flex; align-items: center; gap: 6px;">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"></path>
            <line x1="12" y1="9" x2="12" y2="13"></line>
            <line x1="12" y1="17" x2="12.01" y2="17"></line>
          </svg>
          Phát hiện Đánh giá Mã nguồn (${findings.length} vấn đề)
        </div>
        ${findings.map(f => {
          const sev = (f.severity || 'INFO').toUpperCase();
          const sevClass = (sev === 'CRITICAL' || sev === 'HIGH') ? 'HIGH' : (sev === 'MEDIUM' ? 'MEDIUM' : 'LOW');
          const fileLoc = f.file ? `${f.file}${f.line ? ':' + f.line : ''}` : '';
          const suggestionHtml = f.suggestion ? `
            <div style="margin-top: 8px; font-size: 12px;">
              <strong style="color: var(--text-secondary);">Đề xuất khắc phục:</strong>
              <div class="chat-code-block" style="margin-top: 4px;">
                <pre><code>${escapeHtml(f.suggestion)}</code></pre>
              </div>
            </div>
          ` : '';

          return `
            <div class="finding-card" style="border: 1px solid var(--border-color); background: var(--bg-surface); border-radius: 8px; padding: 12px;">
              <div style="display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 6px;">
                <div style="display: flex; align-items: center; gap: 8px;">
                  <span class="severity-tag ${sevClass}" style="font-size: 10px; font-weight: 700; padding: 2px 6px;">${escapeHtml(sev)}</span>
                  <strong style="font-size: 13px; color: var(--text-primary);">${escapeHtml(f.title || 'Phát hiện mã nguồn')}</strong>
                </div>
                ${fileLoc ? `<span style="font-family: var(--font-mono); font-size: 11px; color: var(--text-muted);">${escapeHtml(fileLoc)}</span>` : ''}
              </div>
              <div style="font-size: 12.5px; color: var(--text-secondary); line-height: 1.5;">
                ${escapeHtml(f.description || '')}
              </div>
              ${suggestionHtml}
            </div>
          `;
        }).join("")}
      </div>
    `;
  }

  // Load and Render Conversations
  async function loadChatConversations(state, el, apiClient, shouldAutoSelect = false, renderSidebarFn) {
    if (!state.currentUser) return;
    try {
      const convs = await apiClient.getChatConversations();
      state.conversations = convs || [];
      if (typeof renderSidebarFn === "function") renderSidebarFn();

      // Đồng bộ danh sách trong workspace tab nếu có
      const wsChatList = document.getElementById("chatConversationsList");
      if (wsChatList) {
        if (state.conversations.length === 0) {
          wsChatList.innerHTML = `<div style="font-size: 11px; color: var(--text-muted); padding: 8px;">Chưa có hội thoại nào</div>`;
        } else {
          wsChatList.innerHTML = state.conversations.map(c => `
            <div class="chat-conv-item ${c.id === state.activeConversationId ? 'active' : ''}" data-conv-id="${c.id}" title="${escapeHtml(c.title)}">
              ${escapeHtml(c.title || 'Hội thoại #' + c.id)}
            </div>
          `).join("");
          wsChatList.querySelectorAll(".chat-conv-item").forEach(item => {
            item.onclick = () => selectChatConversation(Number(item.getAttribute("data-conv-id")), state, el, apiClient, renderSidebarFn);
          });
        }
      }

      // Auto-select latest conversation or start new ONLY if explicitly requested AND view is 'chat'
      if (shouldAutoSelect && state.view === "chat") {
        if (!state.activeConversationId && state.conversations.length > 0) {
          await selectChatConversation(state.conversations[0].id, state, el, apiClient, renderSidebarFn);
        } else if (!state.activeConversationId && state.conversations.length === 0) {
          startNewChatConversation(state, el, renderSidebarFn);
        }
      }
    } catch (e) {
      console.warn("Không thể tải danh sách cuộc hội thoại:", e);
    }
  }

  // Select a Conversation and Load Messages
  async function selectChatConversation(id, state, el, apiClient, renderSidebarFn) {
    state.activeConversationId = id;
    state.view = "chat";
    if (window.renderView) window.renderView();

    if (el.chatStreamArea) {
      if (el.chatEmptyState) el.chatEmptyState.style.display = "none";
      if (el.chatMessagesList) {
        el.chatMessagesList.innerHTML = `<div style="text-align: center; color: var(--text-muted); padding: 30px;">Đang tải tin nhắn...</div>`;
      }
    }

    try {
      const detail = await apiClient.getChatConversationDetail(id);
      state.activeConversation = detail;

      const convTitle = detail.title ? (detail.title.length > 28 ? detail.title.slice(0, 28) + "..." : detail.title) : "Phiên #" + detail.id;
      if (el.activeChatTitle) el.activeChatTitle.textContent = convTitle;
      if (el.chatModelBadge) el.chatModelBadge.textContent = detail.model || "gemini-3.5-flash-lite";
      if (el.selectChatModel && detail.model) {
        el.selectChatModel.value = detail.model;
      }

      if (detail.projectId) {
        const found = state.projects.find(p => p.id === detail.projectId);
        if (found) {
          state.activeProject = found;
          state.expandedProjectIds.add(found.id);
          if (el.chatWorkspaceName) el.chatWorkspaceName.textContent = found.name;
          if (el.chatWorkspaceBadge) el.chatWorkspaceBadge.title = `Thư mục: ${found.path || found.name}`;
          if (el.chatWorkspacePath) {
            el.chatWorkspacePath.textContent = found.path || "";
            el.chatWorkspacePath.style.display = "none";
          }
          if (el.navActiveProjectText) el.navActiveProjectText.textContent = found.name;
          if (el.btnSelectWorkspace) el.btnSelectWorkspace.style.display = "inline-flex";
          if (el.btnDeleteActiveProject) {
            el.btnDeleteActiveProject.style.display = "inline-flex";
            el.btnDeleteActiveProject.title = `Xóa dự án "${found.name}"`;
          }
        }
      } else if (state.activeProject) {
        if (el.chatWorkspaceName) el.chatWorkspaceName.textContent = state.activeProject.name;
        if (el.chatWorkspaceBadge) el.chatWorkspaceBadge.title = `Thư mục: ${state.activeProject.path || state.activeProject.name}`;
        if (el.chatWorkspacePath) {
          el.chatWorkspacePath.textContent = state.activeProject.path || "";
          el.chatWorkspacePath.style.display = "none";
        }
        if (el.btnDeleteActiveProject) {
          el.btnDeleteActiveProject.style.display = "inline-flex";
          el.btnDeleteActiveProject.title = `Xóa dự án "${state.activeProject.name}"`;
        }
      } else {
        if (el.chatWorkspaceName) el.chatWorkspaceName.textContent = "Chưa chọn dự án";
        if (el.chatWorkspaceBadge) el.chatWorkspaceBadge.title = "Hỏi đáp chung";
        if (el.btnDeleteActiveProject) el.btnDeleteActiveProject.style.display = "none";
      }

      if (el.chatProjectHintText) {
        el.chatProjectHintText.textContent = "Enter để gửi • Shift+Enter xuống dòng";
      }

      if (el.selectChatProject && detail.projectId) {
        el.selectChatProject.value = detail.projectId;
      }

      if (typeof renderSidebarFn === "function") renderSidebarFn();
      renderChatMessagesStream(detail.messages || [], state, el);
    } catch (e) {
      console.error("Lỗi khi tải chi tiết cuộc hội thoại:", e);
      if (el.chatMessagesList) {
        el.chatMessagesList.innerHTML = `<div style="color: var(--diff-del); text-align: center; padding: 20px;">Không thể tải tin nhắn: ${escapeHtml(e.message)}</div>`;
      }
    }
  }

  function renderChatMessagesStream(messages, state, el) {
    if (!el.chatMessagesList) return;
    if (!messages || messages.length === 0) {
      if (el.chatEmptyState) el.chatEmptyState.style.display = "flex";
      el.chatMessagesList.innerHTML = "";
      return;
    }

    if (el.chatEmptyState) el.chatEmptyState.style.display = "none";

    const formatMd = window.MarkdownUtils ? window.MarkdownUtils.formatMarkdown : escapeHtml;

    el.chatMessagesList.innerHTML = messages.map(m => {
      const isUser = m.sender === "USER";
      const userInitial = state.currentUser ? state.currentUser.username.charAt(0).toUpperCase() : "U";

      let bodyHtml;
      if (isUser) {
        bodyHtml = `<div class="chat-msg-bubble">${escapeHtml(m.content)}</div>`;
      } else {
        // Kiểm tra xem message có chứa JSON findings không để render trực quan (B5-03)
        let findingsHtml = "";
        try {
          if (m.content && m.content.includes('"findings"')) {
            const parsed = JSON.parse(m.content);
            if (parsed && Array.isArray(parsed.findings)) {
              findingsHtml = renderReviewFindingsHtml(parsed.findings);
            }
          }
        } catch (_) {}

        bodyHtml = `<div class="chat-msg-bubble">${formatMd(m.content)}${findingsHtml}</div>`;
      }

      return `
        <div class="chat-msg-row ${isUser ? 'user' : 'agent'}">
          <div class="chat-msg-avatar">${escapeHtml(isUser ? userInitial : 'AI')}</div>
          ${bodyHtml}
        </div>
      `;
    }).join("");

    if (el.chatStreamArea) {
      el.chatStreamArea.scrollTop = el.chatStreamArea.scrollHeight;
    }
  }

  // Start New Conversation
  function startNewChatConversation(state, el, renderSidebarFn) {
    state.activeConversationId = null;
    state.activeConversation = null;
    state.view = "chat";
    if (window.renderView) window.renderView();
    if (typeof renderSidebarFn === "function") renderSidebarFn();

    if (el.activeChatTitle) {
      el.activeChatTitle.textContent = state.activeProject ? state.activeProject.name : "Hỏi đáp chung";
    }
    if (el.chatWorkspaceName) {
      el.chatWorkspaceName.textContent = state.activeProject ? state.activeProject.name : "Hỏi đáp chung";
    }
    if (el.chatWorkspaceBadge) {
      el.chatWorkspaceBadge.title = state.activeProject ? `Thư mục: ${state.activeProject.path || state.activeProject.name}` : "Hỏi đáp chung (Không gắn dự án)";
    }
    if (el.chatWorkspacePath) {
      el.chatWorkspacePath.textContent = state.activeProject ? (state.activeProject.path || "") : "";
      el.chatWorkspacePath.style.display = "none";
    }
    if (el.chatProjectHintText) {
      el.chatProjectHintText.textContent = "Enter để gửi • Shift+Enter xuống dòng";
    }
    if (el.emptyStateTitle) {
      el.emptyStateTitle.textContent = "Soopi AI Coding Assistant";
    }
    if (el.emptyStateDesc) {
      el.emptyStateDesc.textContent = state.activeProject
        ? `Đang kết nối: ${state.activeProject.name}. Hỏi đáp về mã nguồn hoặc gõ /review, /fix, /explain, /test.`
        : "Không gian làm việc trực tiếp trên dự án. Chọn hoặc mở dự án để AI đọc mã nguồn và thực thi các tính năng chuyên sâu.";
    }

    if (el.chatEmptyState) el.chatEmptyState.style.display = "flex";
    if (el.chatMessagesList) el.chatMessagesList.innerHTML = "";
    if (el.btnDeleteActiveProject) {
      el.btnDeleteActiveProject.style.display = state.activeProject ? "inline-flex" : "none";
      if (state.activeProject) el.btnDeleteActiveProject.title = `Xóa dự án "${state.activeProject.name}"`;
    }
    if (el.centralChatTextarea) {
      el.centralChatTextarea.value = "";
      el.centralChatTextarea.style.height = "24px";
      el.centralChatTextarea.focus();
    }
  }

  // Delete Conversation
  async function deleteChatConversationById(convId, state, el, apiClient, renderSidebarFn) {
    if (!confirm(`Bạn có chắc chắn muốn xóa cuộc hội thoại này không?`)) return;
    try {
      await apiClient.deleteChatConversation(convId);
    } catch (e) {
      console.warn("Lỗi API khi xóa cuộc hội thoại:", e);
      alert(e.message || "Không thể xóa cuộc hội thoại");
      return;
    }
    state.conversations = state.conversations.filter(c => Number(c.id) !== Number(convId));
    if (Number(state.activeConversationId) === Number(convId)) {
      startNewChatConversation(state, el, renderSidebarFn);
    } else {
      if (typeof renderSidebarFn === "function") renderSidebarFn();
    }
  }

  // Send Message from Central Chat Input
  async function handleSendCentralChatMessage(customPrompt, state, el, apiClient, renderSidebarFn) {
    if (state.isChatSending) return;

    const message = customPrompt || (el.centralChatTextarea ? el.centralChatTextarea.value.trim() : "");
    if (!message) return;

    if (el.centralChatTextarea) {
      el.centralChatTextarea.value = "";
      el.centralChatTextarea.style.height = "24px";
    }
    state.isChatSending = true;

    // Show message immediately in UI
    if (el.chatEmptyState) el.chatEmptyState.style.display = "none";
    const userInitial = state.currentUser ? state.currentUser.username.charAt(0).toUpperCase() : "U";

    const userRow = document.createElement("div");
    userRow.className = "chat-msg-row user";
    userRow.innerHTML = `
      <div class="chat-msg-avatar">${escapeHtml(userInitial)}</div>
      <div class="chat-msg-bubble">${escapeHtml(message)}</div>
    `;
    if (el.chatMessagesList) el.chatMessagesList.appendChild(userRow);

    // Show Typing Indicator
    if (el.chatTypingIndicator) el.chatTypingIndicator.style.display = "flex";
    if (el.btnSendCentralChat) el.btnSendCentralChat.disabled = true;
    if (el.chatStreamArea) el.chatStreamArea.scrollTop = el.chatStreamArea.scrollHeight;

    // Determine Project Context: prioritize activeProject
    let projectId = null;
    if (state.activeProject && state.activeProject.id) {
      projectId = state.activeProject.id;
    } else if (state.activeConversation && state.activeConversation.projectId) {
      projectId = state.activeConversation.projectId;
    } else if (el.selectChatProject && el.selectChatProject.value) {
      projectId = Number(el.selectChatProject.value);
    }

    const selectedModel = el.selectChatModel ? el.selectChatModel.value : null;
    const formatMd = window.MarkdownUtils ? window.MarkdownUtils.formatMarkdown : escapeHtml;

    try {
      const response = await apiClient.sendChatMessage(
        message,
        state.activeConversationId,
        selectedModel,
        projectId
      );

      if (el.chatTypingIndicator) el.chatTypingIndicator.style.display = "none";

      if (response) {
        if (response.conversationId) {
          state.activeConversationId = response.conversationId;
        }

        // Render review findings if findings JSON is in response (B5-03)
        let findingsHtml = "";
        try {
          if (response.content && response.content.includes('"findings"')) {
            const parsed = JSON.parse(response.content);
            if (parsed && Array.isArray(parsed.findings)) {
              findingsHtml = renderReviewFindingsHtml(parsed.findings);
            }
          }
        } catch (_) {}

        const agentRow = document.createElement("div");
        agentRow.className = "chat-msg-row agent";
        agentRow.innerHTML = `
          <div class="chat-msg-avatar">AI</div>
          <div class="chat-msg-bubble">${formatMd(response.content)}${findingsHtml}</div>
        `;
        if (el.chatMessagesList) el.chatMessagesList.appendChild(agentRow);

        // Fetch detail to sync state and update title
        if (state.activeConversationId) {
          try {
            const detail = await apiClient.getChatConversationDetail(state.activeConversationId);
            if (detail) {
              state.activeConversation = detail;
              const convTitle = detail.title ? (detail.title.length > 28 ? detail.title.slice(0, 28) + "..." : detail.title) : "Phiên #" + detail.id;
              if (el.activeChatTitle) el.activeChatTitle.textContent = convTitle;
              if (detail.projectId && (!state.activeProject || state.activeProject.id !== detail.projectId)) {
                const found = state.projects.find(p => p.id === detail.projectId);
                if (found) {
                  state.activeProject = found;
                  state.expandedProjectIds.add(found.id);
                  if (el.chatWorkspaceName) el.chatWorkspaceName.textContent = found.name;
                  if (el.chatWorkspaceBadge) el.chatWorkspaceBadge.title = `Thư mục: ${found.path || found.name}`;
                }
              }
            }
          } catch (_) {}
        }

        await loadChatConversations(state, el, apiClient, false, renderSidebarFn);
      }
    } catch (e) {
      if (el.chatTypingIndicator) el.chatTypingIndicator.style.display = "none";
      const errRow = document.createElement("div");
      errRow.className = "chat-msg-row agent";
      errRow.innerHTML = `
        <div class="chat-msg-avatar" style="color: var(--diff-del);">!</div>
        <div class="chat-msg-bubble" style="border-color: rgba(239, 68, 68, 0.4); color: var(--diff-del);">
          Đã xảy ra lỗi khi trao đổi với AI: ${escapeHtml(e.message)}
        </div>
      `;
      if (el.chatMessagesList) el.chatMessagesList.appendChild(errRow);
    } finally {
      state.isChatSending = false;
      if (el.btnSendCentralChat) el.btnSendCentralChat.disabled = false;
      if (el.chatStreamArea) el.chatStreamArea.scrollTop = el.chatStreamArea.scrollHeight;
    }
  }

  window.ChatView = {
    loadChatConversations,
    selectChatConversation,
    startNewChatConversation,
    deleteChatConversationById,
    handleSendCentralChatMessage,
    renderReviewFindingsHtml,
    renderChatMessagesStream
  };
})();
