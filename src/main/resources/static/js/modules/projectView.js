/**
 * ============================================================================
 * PROJECT VIEW MODULE - QUẢN LÝ DỰ ÁN & KHÔNG GIAN LÀM VIỆC (WORKSPACE)
 * ============================================================================
 * Chức năng:
 * 1. Hiển thị danh sách dự án dạng cây (Tree View) ở thanh bên (Sidebar).
 * 2. Nhóm các phiên hội thoại (Chat Sessions) theo từng dự án mã nguồn.
 * 3. Hiển thị các card dự án dạng Workspace trên màn hình chào mừng (Welcome View).
 * 4. Chuyển đổi ngữ cảnh dự án (Project Context Switcher).
 * 5. Xóa dự án và dọn dẹp các phiên chat liên quan (Cascade Delete).
 * ============================================================================
 */
(function() {

  function formatRelativeTime(dateString) {
    if (!dateString) return "vừa xong";
    const date = new Date(dateString);
    if (isNaN(date.getTime())) return "vừa xong";

    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffSec = Math.max(0, Math.floor(diffMs / 1000));
    const diffMin = Math.floor(diffSec / 60);
    const diffHour = Math.floor(diffMin / 60);
    const diffDay = Math.floor(diffHour / 24);

    if (diffSec < 60) return "vừa xong";
    if (diffMin < 60) return `${diffMin}m`;
    if (diffHour < 24) return `${diffHour}h`;
    if (diffDay < 30) return `${diffDay}d`;
    return date.toLocaleDateString("vi-VN");
  }

  function renderWelcomeProjects(state, el, openModalFn, selectProjectWorkspaceFn) {
    const container = el.welcomeProjectsGrid || document.getElementById("welcomeProjectsGrid") || document.getElementById("recentTasksGrid");
    const countBadge = el.welcomeProjectsCount || document.getElementById("welcomeProjectsCount") || document.getElementById("welcomeTasksCount");
    if (!container) return;

    if (countBadge) {
      countBadge.textContent = state.projects.length;
    }

    if (!state.currentUser) {
      container.innerHTML = `
        <div class="empty-tasks-card">
          <p style="font-weight: 600; margin-bottom: 6px;">Vui lòng đăng nhập để mở và quản lý các thư mục dự án.</p>
          <button class="btn-primary-sm" id="btnWelcomeLogin">Đăng nhập ngay</button>
        </div>
      `;
      const btn = document.getElementById("btnWelcomeLogin");
      if (btn && window.openLoginModal) btn.addEventListener("click", () => window.openLoginModal(false));
      return;
    }

    if (state.projects.length === 0) {
      container.innerHTML = `
        <div class="empty-tasks-card">
          <p style="font-weight: 600; margin-bottom: 4px;">Chưa có thư mục dự án nào được mở.</p>
          <p style="font-size: 12px; color: var(--text-muted); margin-bottom: 12px;">
            Mở thư mục dự án mã nguồn từ máy tính của bạn để bắt đầu làm việc trực tiếp với Antigravity AI Agent.
          </p>
          <button class="btn-primary-sm" id="btnWelcomeOpenProj">Mở thư mục dự án đầu tiên</button>
        </div>
      `;
      const btnOpen = document.getElementById("btnWelcomeOpenProj");
      if (btnOpen && openModalFn) btnOpen.addEventListener("click", () => openModalFn(el.modalOpenProject));
      return;
    }

    container.innerHTML = state.projects.map(proj => {
      return `
        <div class="welcome-project-card" data-welcome-proj-id="${proj.id}">
          <div>
            <div class="welcome-project-top">
              <div class="welcome-project-name">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="color: var(--primary-accent); flex-shrink: 0;">
                  <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"></path>
                </svg>
                <span>${proj.name}</span>
              </div>
              <span class="project-lang-pill">${proj.language || 'Mã nguồn'}</span>
            </div>
            <div class="welcome-project-path" title="${proj.path}">${proj.path || 'Local'}</div>
            <div class="welcome-project-desc">${proj.description || 'Thư mục dự án mã nguồn đã sẵn sàng tương tác cùng AI.'}</div>
          </div>
          <div class="welcome-project-footer">
            <span style="font-size: 11.5px; color: var(--text-muted);">Antigravity Workspace</span>
            <button class="btn-open-workspace" data-btn-open-proj="${proj.id}">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polygon points="5 3 19 12 5 21 5 3"></polygon>
              </svg>
              Mở không gian làm việc
            </button>
          </div>
        </div>
      `;
    }).join("");

    container.querySelectorAll("[data-btn-open-proj]").forEach(btn => {
      btn.addEventListener("click", (e) => {
        e.stopPropagation();
        const projId = Number(btn.getAttribute("data-btn-open-proj"));
        const proj = state.projects.find(p => p.id === projId);
        if (proj && typeof selectProjectWorkspaceFn === "function") {
          selectProjectWorkspaceFn(proj);
        }
      });
    });

    container.querySelectorAll(".welcome-project-card").forEach(card => {
      card.addEventListener("click", () => {
        const projId = Number(card.getAttribute("data-welcome-proj-id"));
        const proj = state.projects.find(p => p.id === projId);
        if (proj && typeof selectProjectWorkspaceFn === "function") {
          selectProjectWorkspaceFn(proj);
        }
      });
    });
  }

  function renderSidebar(state, el, callbacks = {}) {
    const {
      selectProjectWorkspaceFn,
      startNewChatConversationFn,
      selectChatConversationFn,
      deleteChatConversationFn,
      handleDeleteProjectFn,
      openModalFn,
      openLoginModalFn
    } = callbacks;

    if (el.sectionPinnedTasks) {
      el.sectionPinnedTasks.style.display = "none";
    }

    if (el.sectionSidebarConversations) {
      el.sectionSidebarConversations.style.display = "none";
    }

    if (!el.projectsTreeContainer) return;

    if (!state.currentUser) {
      el.projectsTreeContainer.innerHTML = `
        <div style="padding: 12px; font-size: 12px; color: var(--text-muted); text-align: center; border: 1px dashed var(--border-main); border-radius: 6px;">
          <button id="btnEmptyLogin" style="margin-top: 6px; padding: 6px 12px; font-size: 11.5px; cursor: pointer; border-radius: 4px; background: var(--primary-accent); color: #fff; border: none; font-weight: 600;">Đăng nhập để xem dự án</button>
        </div>
      `;
      const btnEmptyLogin = document.getElementById("btnEmptyLogin");
      if (btnEmptyLogin && typeof openLoginModalFn === "function") btnEmptyLogin.addEventListener("click", () => openLoginModalFn(false));
      return;
    }

    // 1. PINNED SECTION: Hỏi đáp chung (Always visible at the top)
    const isGeneralActive = !state.activeProject;
    const isGeneralExpanded = state.isGeneralSectionCollapsed !== true;
    const generalConvs = (state.conversations || []).filter(c => !c.projectId);

    const generalSessionsHtml = generalConvs.length === 0
      ? `<div style="font-size: 11px; color: var(--text-muted); padding: 5px 8px; font-style: italic;">Chưa có phiên hỏi đáp nào</div>`
      : generalConvs.map(c => {
          const isConvActive = Number(c.id) === Number(state.activeConversationId);
          const timeAgo = formatRelativeTime(c.updatedAt || c.createdAt);
          const safeTitle = c.title || 'Phiên #' + c.id;
          const displayTitle = safeTitle.length > 22 ? safeTitle.slice(0, 22) + "..." : safeTitle;
          return `
            <div class="project-session-row ${isConvActive ? 'active' : ''}" data-conv-id="${c.id}" title="${safeTitle}">
              <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="flex-shrink:0; margin-right: 5px; opacity: 0.7;">
                <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"></path>
              </svg>
              <span class="project-session-title">${displayTitle}</span>
              <span class="project-session-time">${timeAgo}</span>
              <button type="button" class="project-session-del" data-del-conv-id="${c.id}" title="Xóa phiên này">
                <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="18" y1="6" x2="6" y2="18"></line>
                  <line x1="6" y1="6" x2="18" y2="18"></line>
                </svg>
              </button>
            </div>
          `;
        }).join("");

    const generalHtml = `
      <div class="sidebar-pinned-section">
        <div class="project-tree-item ${isGeneralExpanded ? 'open' : ''}">
          <div class="project-tree-header ${isGeneralActive ? 'active' : ''}" data-general-chat="true" title="Hỏi đáp chung (Không gắn dự án)">
            <div class="project-tree-header-left">
              <span class="project-expand-arrow" data-toggle-general="true">
                <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                  <polyline points="9 18 15 12 9 6"></polyline>
                </svg>
              </span>
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="color: var(--primary-accent); flex-shrink: 0;">
                <circle cx="12" cy="12" r="10"></circle>
                <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"></path>
                <line x1="12" y1="17" x2="12.01" y2="17"></line>
              </svg>
              <span class="project-tree-name" style="font-weight: 600;">Hỏi đáp chung</span>
            </div>
            <div class="project-tree-header-right">
              <span class="project-sessions-badge">${generalConvs.length}</span>
              <button type="button" class="btn-project-action" data-add-general-chat="true" title="Tạo phiên hỏi đáp chung mới">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                  <line x1="12" y1="5" x2="12" y2="19"></line>
                  <line x1="5" y1="12" x2="19" y2="12"></line>
                </svg>
              </button>
            </div>
          </div>
          <div class="project-tree-sessions">
            ${generalSessionsHtml}
          </div>
        </div>
      </div>
    `;

    // 2. DIVIDER & PROJECTS HEADER
    const dividerHtml = `
      <div class="sidebar-projects-divider">
        <span class="sidebar-divider-label">DỰ ÁN MÃ NGUỒN</span>
        <button type="button" class="btn-sidebar-add-project" data-open-project-modal="true" title="Mở thêm thư mục dự án">
          <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
            <line x1="12" y1="5" x2="12" y2="19"></line>
            <line x1="5" y1="12" x2="19" y2="12"></line>
          </svg>
          <span>Mở dự án</span>
        </button>
      </div>
    `;

    // 3. PROJECTS LIST
    let projectsHtml = "";
    if (!state.projects || state.projects.length === 0) {
      projectsHtml = `
        <div style="padding: 12px 10px; font-size: 11.5px; color: var(--text-muted); text-align: center; border: 1px dashed var(--border-subtle); border-radius: 6px; margin-top: 4px;">
          Chưa mở dự án nào trong CSDL.<br>
          <button data-open-project-modal="true" style="margin-top: 6px; padding: 4px 10px; font-size: 11px; cursor: pointer; border-radius: 4px; background: var(--bg-subtle); border: 1px solid var(--border-main); color: var(--primary-accent); font-weight: 600;">+ Mở dự án đầu tiên</button>
        </div>
      `;
    } else {
      projectsHtml = state.projects.map(proj => {
        const isProjectActive = state.activeProject && Number(state.activeProject.id) === Number(proj.id);
        const isExpanded = state.expandedProjectIds.has(proj.id) || state.expandedProjectIds.has(Number(proj.id));
        const projConvs = (state.conversations || []).filter(c => Number(c.projectId) === Number(proj.id));

        const convListHtml = projConvs.length === 0
          ? `<div style="font-size: 11px; color: var(--text-muted); padding: 5px 8px; font-style: italic;">Chưa có phiên chat nào</div>`
          : projConvs.map(c => {
              const isConvActive = Number(c.id) === Number(state.activeConversationId);
              const timeAgo = formatRelativeTime(c.updatedAt || c.createdAt);
              const safeTitle = c.title || 'Phiên #' + c.id;
              const displayTitle = safeTitle.length > 22 ? safeTitle.slice(0, 22) + "..." : safeTitle;
              return `
                <div class="project-session-row ${isConvActive ? 'active' : ''}" data-conv-id="${c.id}" title="${safeTitle}">
                  <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="flex-shrink:0; margin-right: 5px; opacity: 0.7;">
                    <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"></path>
                  </svg>
                  <span class="project-session-title">${displayTitle}</span>
                  <span class="project-session-time">${timeAgo}</span>
                  <button type="button" class="project-session-del" data-del-conv-id="${c.id}" title="Xóa phiên này">
                    <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <line x1="18" y1="6" x2="6" y2="18"></line>
                      <line x1="6" y1="6" x2="18" y2="18"></line>
                    </svg>
                  </button>
                </div>
              `;
            }).join("");

        return `
          <div class="project-tree-item ${isExpanded ? 'open' : ''}" data-tree-proj-id="${proj.id}">
            <div class="project-tree-header ${isProjectActive ? 'active' : ''}" data-proj-id="${proj.id}" title="${proj.path || proj.name}">
              <div class="project-tree-header-left">
                <span class="project-expand-arrow">
                  <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                    <polyline points="9 18 15 12 9 6"></polyline>
                  </svg>
                </span>
                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="color: var(--primary-accent); flex-shrink: 0;">
                  <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"></path>
                </svg>
                <span class="project-tree-name">${proj.name}</span>
              </div>
              <div class="project-tree-header-right">
                <span class="project-sessions-badge">${projConvs.length}</span>
                <button type="button" class="btn-project-action" data-add-chat-proj-id="${proj.id}" title="Tạo phiên chat mới cho ${proj.name}">
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                    <line x1="12" y1="5" x2="12" y2="19"></line>
                    <line x1="5" y1="12" x2="19" y2="12"></line>
                  </svg>
                </button>
                <button type="button" class="btn-project-action delete" data-delete-proj-id="${proj.id}" data-proj-name="${proj.name}" title="Xóa dự án ${proj.name}">
                  <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <polyline points="3 6 5 6 21 6"></polyline>
                    <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path>
                  </svg>
                </button>
              </div>
            </div>
            <div class="project-tree-sessions">
              ${convListHtml}
            </div>
          </div>
        `;
      }).join("");
    }

    el.projectsTreeContainer.innerHTML = generalHtml + dividerHtml + projectsHtml;

    // Attach click events
    const generalHeader = document.querySelector("[data-general-chat]");
    if (generalHeader) {
      generalHeader.addEventListener("click", (e) => {
        if (e.target.closest("button") || e.target.closest("[data-toggle-general]")) return;
        state.activeProject = null;
        if (el.btnDeleteActiveProject) el.btnDeleteActiveProject.style.display = "none";
        if (typeof startNewChatConversationFn === "function") startNewChatConversationFn();
      });
    }

    const toggleGen = document.querySelector("[data-toggle-general]");
    if (toggleGen) {
      toggleGen.addEventListener("click", (e) => {
        e.stopPropagation();
        state.isGeneralSectionCollapsed = !state.isGeneralSectionCollapsed;
        renderSidebar(state, el, callbacks);
      });
    }

    const addGenBtn = document.querySelector("[data-add-general-chat]");
    if (addGenBtn) {
      addGenBtn.addEventListener("click", (e) => {
        e.stopPropagation();
        state.activeProject = null;
        if (el.btnDeleteActiveProject) el.btnDeleteActiveProject.style.display = "none";
        if (typeof startNewChatConversationFn === "function") startNewChatConversationFn();
      });
    }

    document.querySelectorAll("[data-open-project-modal]").forEach(btn => {
      btn.addEventListener("click", () => {
        if (typeof openModalFn === "function") openModalFn(el.modalOpenProject);
      });
    });

    document.querySelectorAll(".project-tree-header[data-proj-id]").forEach(header => {
      header.addEventListener("click", (e) => {
        if (e.target.closest("button")) return;
        const projId = Number(header.getAttribute("data-proj-id"));
        const project = state.projects.find(p => Number(p.id) === projId);
        if (!project) return;

        if (state.expandedProjectIds.has(projId)) {
          state.expandedProjectIds.delete(projId);
        } else {
          state.expandedProjectIds.add(projId);
          if (!state.activeProject || Number(state.activeProject.id) !== projId) {
            if (typeof selectProjectWorkspaceFn === "function") selectProjectWorkspaceFn(project);
            return;
          }
        }
        renderSidebar(state, el, callbacks);
      });
    });

    document.querySelectorAll("[data-add-chat-proj-id]").forEach(btn => {
      btn.addEventListener("click", (e) => {
        e.stopPropagation();
        const projId = Number(btn.getAttribute("data-add-chat-proj-id"));
        const project = state.projects.find(p => Number(p.id) === projId);
        if (project) {
          state.activeProject = project;
          state.expandedProjectIds.add(projId);
          if (typeof startNewChatConversationFn === "function") startNewChatConversationFn();
        }
      });
    });

    document.querySelectorAll("[data-delete-proj-id]").forEach(btn => {
      btn.addEventListener("click", (e) => {
        e.stopPropagation();
        const projId = Number(btn.getAttribute("data-delete-proj-id"));
        const projName = btn.getAttribute("data-proj-name");
        if (typeof handleDeleteProjectFn === "function") handleDeleteProjectFn(projId, projName);
      });
    });

    document.querySelectorAll(".project-session-row[data-conv-id]").forEach(row => {
      row.addEventListener("click", (e) => {
        if (e.target.closest("[data-del-conv-id]")) return;
        const convId = Number(row.getAttribute("data-conv-id"));
        if (typeof selectChatConversationFn === "function") selectChatConversationFn(convId);
      });
    });

    document.querySelectorAll("[data-del-conv-id]").forEach(btn => {
      btn.addEventListener("click", async (e) => {
        e.stopPropagation();
        const convId = Number(btn.getAttribute("data-del-conv-id"));
        if (typeof deleteChatConversationFn === "function") await deleteChatConversationFn(convId);
      });
    });
  }

  function selectProjectWorkspace(project, state, el, callbacks = {}) {
    if (!project) return;
    state.activeProject = project;
    state.expandedProjectIds.add(project.id);
    state.view = "chat";

    if (el.chatWorkspaceName) el.chatWorkspaceName.textContent = project.name || "Dự án";
    if (el.chatWorkspaceBadge) el.chatWorkspaceBadge.title = `Thư mục: ${project.path || project.name}`;
    if (el.chatWorkspacePath) {
      el.chatWorkspacePath.textContent = project.path || "";
      el.chatWorkspacePath.style.display = "none";
    }
    if (el.btnDeleteActiveProject) {
      el.btnDeleteActiveProject.style.display = "inline-flex";
      el.btnDeleteActiveProject.title = `Xóa dự án "${project.name}"`;
    }
    if (el.chatProjectHintText) {
      el.chatProjectHintText.textContent = "Enter để gửi • Shift+Enter xuống dòng";
    }
    if (el.navActiveProjectText) {
      el.navActiveProjectText.textContent = project.name;
    }
    if (el.btnSelectWorkspace) {
      el.btnSelectWorkspace.style.display = "inline-flex";
    }

    if (callbacks.renderViewFn) callbacks.renderViewFn();
    if (callbacks.renderSidebarFn) callbacks.renderSidebarFn();

    // Check if current active conversation is already for this project
    if (state.activeConversation && state.activeConversation.projectId === project.id) {
      if (callbacks.selectChatConversationFn) callbacks.selectChatConversationFn(state.activeConversation.id);
      return;
    }

    // Check if there is an existing conversation for this project
    const projConv = state.conversations.find(c => c.projectId === project.id);
    if (projConv) {
      if (callbacks.selectChatConversationFn) callbacks.selectChatConversationFn(projConv.id);
    } else {
      if (callbacks.startNewChatConversationFn) callbacks.startNewChatConversationFn();
    }
  }

  function populateProjectSelect(state, el) {
    if (!el.selectTaskProject) return;
    if (state.projects.length === 0) {
      el.selectTaskProject.innerHTML = `<option value="">Chưa có dự án nào trong CSDL - hãy mở dự án trước</option>`;
      return;
    }
    el.selectTaskProject.innerHTML = state.projects.map(p => `
      <option value="${p.id}">${p.name} (${p.language || 'Java'})</option>
    `).join("");
  }

  async function handleDeleteProject(projId, projName, state, el, apiClient, callbacks = {}) {
    const numId = Number(projId);
    const targetProj = state.projects.find(p => Number(p.id) === numId);
    const name = projName || (targetProj ? targetProj.name : "dự án");
    if (!confirm(`CẢNH BÁO QUAN TRỌNG:\nBạn có chắc chắn muốn xóa vĩnh viễn dự án "${name}" không?\n\nToàn bộ các phiên trò chuyện và tác vụ AI liên quan đến dự án này sẽ bị xóa sạch khỏi hệ thống!`)) {
      return;
    }

    try {
      await apiClient.deleteProject(numId);
    } catch (e) {
      console.warn("Lỗi API khi xóa dự án:", e);
    }

    state.projects = state.projects.filter(p => Number(p.id) !== numId);
    state.conversations = state.conversations.filter(c => Number(c.projectId) !== numId);
    state.tasks = state.tasks.filter(t => Number(t.projectId) !== numId);
    state.expandedProjectIds.delete(numId);
    state.expandedProjectIds.delete(projId);

    if (state.activeProject && Number(state.activeProject.id) === numId) {
      state.activeProject = null;
      state.activeConversationId = null;
      state.activeConversation = null;
      if (el.btnDeleteActiveProject) el.btnDeleteActiveProject.style.display = "none";
      if (typeof callbacks.startNewChatConversationFn === "function") {
        callbacks.startNewChatConversationFn();
      }
    }

    if (typeof callbacks.renderSidebarFn === "function") callbacks.renderSidebarFn();
    if (typeof callbacks.renderWelcomeProjectsFn === "function") callbacks.renderWelcomeProjectsFn();
    populateProjectSelect(state, el);
  }

  window.ProjectView = {
    formatRelativeTime,
    renderWelcomeProjects,
    renderSidebar,
    selectProjectWorkspace,
    populateProjectSelect,
    handleDeleteProject
  };
})();
