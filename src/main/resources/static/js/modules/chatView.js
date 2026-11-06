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
          const sev = String(f.severity || 'INFO').toUpperCase();
          const sevClass = (sev === 'CRITICAL' || sev === 'HIGH') ? 'HIGH' : (sev === 'MEDIUM' ? 'MEDIUM' : 'LOW');
          const file = f.filePath || f.file;
          const start = f.lineStart || f.line;
          const range = start ? `:${start}${f.lineEnd && f.lineEnd !== start ? '-' + f.lineEnd : ''}` : '';
          const fileLoc = file ? `${file}${range}` : '';
          const suggestion = f.suggestion || f.recommendation;
          const suggestionHtml = suggestion ? `
            <div style="margin-top: 8px; font-size: 12px;">
              <strong style="color: var(--text-secondary);">Đề xuất khắc phục:</strong>
              <div class="chat-code-block" style="margin-top: 4px;">
                <pre><code>${escapeHtml(suggestion)}</code></pre>
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
                ${f.classification ? `<p class="muted">${escapeHtml(f.classification)}</p>` : ''}
                ${f.category ? `<span class="finding-category">${escapeHtml(f.category)}</span>` : ''}${escapeHtml(f.explanation || f.description || '')}
              </div>
              ${f.evidence ? `<p class="muted">Bằng chứng từ mã nguồn:</p><div class="chat-code-block"><pre><code>${escapeHtml(f.evidence)}</code></pre></div>` : ''}
              ${suggestionHtml}
            </div>
          `;
        }).join("")}
      </div>
    `;
  }

  const reviews = new WeakMap();
  const reviewRequests = new WeakMap();
  const sends = new WeakMap();
  function reviewController(state, api) {
    if (!window.ChatReview) return null;
    if (!reviews.has(state)) reviews.set(state, window.ChatReview.create({api, state,
      render: tasks => {
        const cards = document.getElementById('chatReviewCards'); if (cards) cards.innerHTML = tasks.map(window.ChatReview.renderCard).join('');
        const empty = document.getElementById('chatEmptyState'); if (empty && tasks.length) empty.style.display = 'none';
      },
      onError: error => { const cards = document.getElementById('chatReviewCards'); if (cards) cards.innerHTML += `<p role="alert">Không thể cập nhật review: ${escapeHtml(error.message)}</p>`; }
    }));
    return reviews.get(state);
  }
  function clearReviewContext(state) {
    state.reviewFollowTaskId = null;
    const label = document.getElementById('chatFollowContext'); if (label) label.textContent = '';
    const clear = document.getElementById('chatClearFollow'); if (clear) clear.hidden = true;
  }
  function resetReviews(state) {
    reviews.get(state)?.invalidate(); clearReviewContext(state);
    sends.delete(state); reviewRequests.delete(state); state.isChatSending = false;
    const send = document.getElementById("btnSendCentralChat"); if (send) send.disabled = false;
    const cards = document.getElementById('chatReviewCards'); if (cards) cards.innerHTML = '';
  }

  // Load and Render Conversations
  async function loadChatConversations(state, el, apiClient, shouldAutoSelect = false, renderSidebarFn) {
    if (!state.currentUser) return;
    const session = apiClient.getToken?.();
    try {
      const convs = await apiClient.getChatConversations();
      if (session !== apiClient.getToken?.() || !state.currentUser) return;
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
    const session = apiClient.getToken?.();
    resetReviews(state);
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
      if (Number(state.activeConversationId) !== Number(id) || session !== apiClient.getToken?.()) return;
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
      reviewController(state, apiClient)?.load().catch(error => console.warn("Không thể tải review:", error));
      if (el.chatStreamArea) el.chatStreamArea.scrollTop = el.chatStreamArea.scrollHeight;
    } catch (e) {
      if (Number(state.activeConversationId) !== Number(id) || session !== apiClient.getToken?.()) return;
      console.error("Lỗi khi tải chi tiết cuộc hội thoại:", e);
      if (el.chatMessagesList) {
        el.chatMessagesList.innerHTML = `<div style="color: var(--diff-del); text-align: center; padding: 20px;">Không thể tải tin nhắn: ${escapeHtml(e.message)}</div>`;
      }
    }
  }

  function isFollowing(area) {
    if (!area || !Number.isFinite(area.clientHeight)) return true;
    return area.scrollHeight - area.scrollTop - area.clientHeight < 100;
  }
  function renderChatMessagesStream(messages, state, el) {
    const follow = isFollowing(el.chatStreamArea);
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

    if (follow && el.chatStreamArea) {
      el.chatStreamArea.scrollTop = el.chatStreamArea.scrollHeight;
    }
  }

  // Start New Conversation
  function startNewChatConversation(state, el, renderSidebarFn) {
    resetReviews(state);
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
      el.emptyStateTitle.textContent = "HUS AI Agent";
    }
    if (el.emptyStateDesc) {
      el.emptyStateDesc.textContent = state.activeProject
        ? `Đang kết nối: ${state.activeProject.name}. Hỏi về mã nguồn, kiến trúc hoặc gõ /review để bắt đầu đánh giá.`
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
      if (window.WorkspaceView) window.WorkspaceView.notify(e.message || "Không thể xóa cuộc hội thoại", true);
      else alert(e.message || "Không thể xóa cuộc hội thoại");
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

    let message = customPrompt || (el.centralChatTextarea ? el.centralChatTextarea.value.trim() : "");
    if (!message) return;
    const mode = document.getElementById('chatReviewMode');
    if (mode?.value === 'review' && !window.ChatReview?.isReview(message)) message = `/review ${message}`;
    if (window.ChatReview?.isReview(message)) {
      await submitReview(message, state, el, apiClient, renderSidebarFn);
      return;
    }

    if (el.centralChatTextarea) {
      el.centralChatTextarea.value = "";
      el.centralChatTextarea.style.height = "24px";
    }
    state.isChatSending = true;
    const request = {};
    sends.set(state, request);
    const session = apiClient.getToken?.();
    let conversationId = state.activeConversationId;
    const activeProjectId = state.activeProject?.id;
    const isCurrent = () => sends.get(state) === request && session === apiClient.getToken?.()
      && state.activeConversationId === conversationId && state.activeProject?.id === activeProjectId;

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
    let followResponse = true;

    try {
      const response = await apiClient.sendChatMessage(
        message,
        state.activeConversationId,
        selectedModel,
        projectId,
        state.reviewFollowTaskId || undefined
      );

      if (!isCurrent()) return;
      followResponse = isFollowing(el.chatStreamArea);
      if (el.chatTypingIndicator) el.chatTypingIndicator.style.display = "none";

      if (response) {
        if (response.conversationId) {
          state.activeConversationId = response.conversationId;
          conversationId = response.conversationId;
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
            if (!isCurrent()) return;
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
      if (!isCurrent()) return;
      followResponse = isFollowing(el.chatStreamArea);
      if (el.chatTypingIndicator) el.chatTypingIndicator.style.display = "none";
      const isModerationError = e.message && (e.message.includes("kiểm duyệt") || e.message.includes("vi phạm quy chuẩn"));
      if (isModerationError && userRow) {
        userRow.classList.add("rejected");
      }
      const errRow = document.createElement("div");
      errRow.className = "chat-msg-row agent";
      errRow.innerHTML = `
        <div class="chat-msg-avatar" style="color: var(--diff-del); border-color: rgba(239, 68, 68, 0.4);">!</div>
        <div class="chat-msg-bubble" style="border-color: rgba(239, 68, 68, 0.4); color: var(--diff-del); background: rgba(239, 68, 68, 0.05);">
          ${isModerationError ? '<div style="font-weight: 600; font-size: 12.5px; margin-bottom: 4px; display: flex; align-items: center; gap: 5px;">🛡️ Cảnh báo Kiểm duyệt Nội dung An toàn</div>' : ''}
          ${isModerationError ? escapeHtml(e.message) : `Đã xảy ra lỗi khi trao đổi với AI: ${escapeHtml(e.message)}`}
        </div>
      `;
      if (el.chatMessagesList) el.chatMessagesList.appendChild(errRow);
    } finally {
      if (sends.get(state) === request) {
        sends.delete(state);
        if (el.chatTypingIndicator) el.chatTypingIndicator.style.display = "none";
        state.isChatSending = false;
      if (el.btnSendCentralChat) el.btnSendCentralChat.disabled = false;
        if (followResponse && session === apiClient.getToken?.() && state.activeConversationId === conversationId && el.chatStreamArea) el.chatStreamArea.scrollTop = el.chatStreamArea.scrollHeight;
      }
    }
  }

  async function submitReview(message, state, el, api, renderSidebarFn) {
    const projectId = state.activeConversation?.projectId || state.activeProject?.id;
    const selected = window.SkillView.readSelection(document.getElementById('chatReviewSkills'));
    if (!projectId) {
      window.WorkspaceView.notify('Chọn dự án trước khi review.', true); return;
    }
    state.isChatSending = true;
    const request = {}; sends.set(state, request);
    if (el.btnSendCentralChat) el.btnSendCentralChat.disabled = true;
    const session = api.getToken?.(), initialId = state.activeConversationId, initialProject = state.activeProject?.id;
    let conversationId = initialId;
    const current = () => sends.get(state) === request && session === api.getToken?.() && state.activeConversationId === conversationId && state.activeProject?.id === initialProject;
    try {
      if (!conversationId) {
        const conversation = await api.createChatConversation('Code review', el.selectChatModel?.value || null, projectId);
        if (!current()) return;
        conversationId = conversation.id; state.activeConversationId = conversationId; state.activeConversation = conversation;
      }
      const selectedTools = Array.from(document.getElementById('chatReviewTools')?.querySelectorAll('input:checked') || []).map(input => input.value);
      const payload = {message, model: el.selectChatModel?.value || null, selectedTools, skills: selected};
      const fingerprint = JSON.stringify({conversationId, payload});
      let pending = reviewRequests.get(state);
      if (!pending || pending.fingerprint !== fingerprint) {
        pending = {fingerprint, requestId: crypto.randomUUID()}; reviewRequests.set(state, pending);
      }
      await api.createConversationReview(conversationId, {...payload, requestId: pending.requestId});
      if (!current()) return;
      if (reviewRequests.get(state) === pending) reviewRequests.delete(state);
      if (el.centralChatTextarea) el.centralChatTextarea.value = '';
      if (el.chatEmptyState) el.chatEmptyState.style.display = 'none';
      clearReviewContext(state);
      const controller = reviewController(state, api); controller.invalidate();
      await controller.load();
      if (current()) await loadChatConversations(state, el, api, false, renderSidebarFn);
    } catch (error) {
      if (current()) window.WorkspaceView.notify(`Không thể gửi review: ${error.message}. Yêu cầu không được tự gửi lại; kiểm tra lịch sử trước khi thử lại.`, true);
    } finally {
      if (sends.get(state) === request) {
        sends.delete(state); state.isChatSending = false;
        if (el.btnSendCentralChat) el.btnSendCentralChat.disabled = false;
      }
    }
  }

  function onViewChanged(state, api) {
    const controller = reviews.get(state);
    if (!controller) return;
    if (state.view !== 'chat') controller.invalidate();
    else if (state.activeConversationId) controller.load().catch(error => console.warn('Không thể tải review:', error));
  }

  function mount({el, state, renderView, startNewChatConversation, deleteChatConversationById, handleDeleteProject, handleSendCentralChatMessage, updateChatProjectHint, signal}) {
    function listen(target, type, handler) { target.addEventListener(type, handler, {signal}); }

    const cards = document.getElementById('chatReviewCards');
    const clearFollow = document.getElementById('chatClearFollow');
    if (clearFollow) listen(clearFollow, 'click', () => clearReviewContext(state));
    if (cards) {
      listen(cards, 'change', event => {
        if (!event.target.matches('[data-review-filter]')) return;
        const card = event.target.closest('[data-review-id]');
        const severity = card.querySelector('[data-review-filter="severity"]').value;
        const category = card.querySelector('[data-review-filter="category"]').value;
        card.querySelectorAll('.chat-review-finding').forEach(f => { f.hidden = !!((severity && f.dataset.severity !== severity) || (category && f.dataset.category !== category)); });
      });
      listen(cards, 'click', async event => {
        const button = event.target.closest('button'); if (!button) return;
        const card = button.closest('[data-review-id]'); if (!card) return;
        const task = reviewController(state, window.apiClient)?.getTasks().find(t => String(t.id) === card.dataset.reviewId); if (!task) return;
        const action = button.dataset.reviewAction;
        if (button.dataset.copyFinding !== undefined) {
          const finding = button.closest('.chat-review-finding');
          try { await navigator.clipboard.writeText(finding.innerText.replace('Sao chép finding', '').trim()); }
          catch (_) { window.WorkspaceView.notify('Không thể sao chép finding.', true); }
        } else if (action === 'follow') {
          state.reviewFollowTaskId = task.id;
          document.getElementById('chatFollowContext').textContent = `Đang thảo luận báo cáo #${task.id}`;
          clearFollow.hidden = false;
          const mode = document.getElementById('chatReviewMode'); if (mode) mode.value = 'chat';
          el.centralChatTextarea?.focus();
        } else if (action === 'open') {
          document.getElementById('taskReportTitle').textContent = `Báo cáo review #${task.id}`;
          document.getElementById('taskReportContent').innerHTML = window.SkillView.renderTaskReport(task);
          const modal = document.getElementById('modalTaskReport');
          if (window.Dialogs?.openModal) window.Dialogs.openModal(modal); else modal?.classList.add('active');
        } else if (action === 'cancel' || action === 'retry') {
          button.disabled = true; const id = state.activeConversationId, session = window.apiClient.getToken?.();
          try { await window.apiClient[action === 'cancel' ? 'cancelTask' : 'retryTask'](task.id);
            if (id === state.activeConversationId && session === window.apiClient.getToken?.()) {
              const controller = reviewController(state, window.apiClient); controller.invalidate(); await controller.load();
            }
          } catch (error) { if (id === state.activeConversationId && session === window.apiClient.getToken?.()) window.WorkspaceView.notify(error.message, true); }
          finally { button.disabled = false; }
        }
      });
    }
    // Mode Switcher Pills (Chatbot vs Code Review)
    if (el.btnModeChat) {
      listen(el.btnModeChat, "click", () => {
        state.view = "chat";
        renderView();
      });
    }

    if (el.btnModeReview) {
      listen(el.btnModeReview, "click", () => {
        window.WorkspaceView.navigate("projects");
      });
    }

    // Sidebar & Header New Chat buttons
    if (el.btnSidebarNewChat) {
      listen(el.btnSidebarNewChat, "click", startNewChatConversation);
    }

    if (el.btnHeaderNewChat) {
      listen(el.btnHeaderNewChat, "click", startNewChatConversation);
    }

    // Header Delete Conversation button
    if (el.btnDeleteActiveChat) {
      listen(el.btnDeleteActiveChat, "click", () => {
        if (state.activeConversationId) {
          deleteChatConversationById(state.activeConversationId);
        } else {
          const hasMessages = el.chatMessagesList && el.chatMessagesList.children.length > 0;
          if (hasMessages) {
            if (confirm("Bạn có muốn làm mới và xóa tin nhắn trong phiên hội thoại hiện tại không?")) {
              startNewChatConversation();
            }
          } else {
            startNewChatConversation();
          }
        }
      });
    }

    // Header Delete Project button
    if (el.btnDeleteActiveProject) {
      listen(el.btnDeleteActiveProject, "click", () => {
        if (state.activeProject) {
          handleDeleteProject(state.activeProject.id, state.activeProject.name);
        } else {
          window.WorkspaceView.notify("Chưa chọn dự án nào để xóa.", true);
        }
      });
    }

    // Chat Model Selector change
    if (el.selectChatModel) {
      listen(el.selectChatModel, "change", async () => {
        const newModel = el.selectChatModel.value;
        const conversationId = state.activeConversationId;
        const session = window.apiClient.getToken?.();
        if (conversationId) {
          try {
            await window.apiClient.updateChatConversationModel(conversationId, newModel);
            if (session !== window.apiClient.getToken?.() || state.activeConversationId !== conversationId) return;
            if (state.activeConversation) state.activeConversation.model = newModel;
            const convInList = state.conversations.find(c => c.id === state.activeConversationId);
            if (convInList) convInList.model = newModel;
          } catch (e) {
            console.warn("Lỗi cập nhật mô hình cho hội thoại:", e);
          }
        }
      });
    }

    // Central Chat Send Button & Enter Key
    if (el.btnSendCentralChat) {
      listen(el.btnSendCentralChat, "click", () => handleSendCentralChatMessage());
    }

    if (el.centralChatTextarea) {
      listen(el.centralChatTextarea, "keydown", (e) => {
        if (e.key === "Enter" && !e.shiftKey) {
          e.preventDefault();
          handleSendCentralChatMessage();
        }
      });
      listen(el.centralChatTextarea, "input", () => {
        el.centralChatTextarea.style.height = "auto";
        el.centralChatTextarea.style.height = Math.min(el.centralChatTextarea.scrollHeight, 120) + "px";
      });
    }

    // Chat Project Context Selector
    if (el.selectChatProject) {
      listen(el.selectChatProject, "change", () => {
        updateChatProjectHint();
      });
    }

    // Suggestion Chips
    document.querySelectorAll(".suggestion-chip[data-prompt]").forEach(chip => {
      listen(chip, "click", () => {
        const prompt = chip.getAttribute("data-prompt");
        if (prompt) {
          handleSendCentralChatMessage(prompt);
        }
      });
    });

    // Legacy Workspace Chat Tab Controls
    const btnNewChat = document.getElementById("btnNewChatConversation");
    if (btnNewChat) {
      listen(btnNewChat, "click", startNewChatConversation);
    }

    const btnSendChat = document.getElementById("btnSendChatMessage");
    if (btnSendChat) {
      listen(btnSendChat, "click", () => {
        const inputChat = document.getElementById("inputChatMessage");
        if (inputChat && inputChat.value.trim()) {
          const val = inputChat.value.trim();
          inputChat.value = "";
          handleSendCentralChatMessage(val);
        }
      });
    }

    const inputChat = document.getElementById("inputChatMessage");
    if (inputChat) {
      listen(inputChat, "keydown", (e) => {
        if (e.key === "Enter" && !e.shiftKey) {
          e.preventDefault();
          if (inputChat.value.trim()) {
            const val = inputChat.value.trim();
            inputChat.value = "";
            handleSendCentralChatMessage(val);
          }
        }
      });
    }

  }

  window.ChatView = {
    mount,
    loadChatConversations,
    selectChatConversation,
    startNewChatConversation,
    deleteChatConversationById,
    handleSendCentralChatMessage,
    renderReviewFindingsHtml,
    renderChatMessagesStream,
    onViewChanged,
    resetReviews
  };
})();
