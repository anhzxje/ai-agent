/** Application chrome and DOM event wiring; feature state and transport live elsewhere. */
(function () {
  function create(state, api) {
  const el = Object.fromEntries([
    'appSidebar', 'landingView', 'welcomeView', 'workspaceView', 'chatAssistantView',
    'navModePills', 'btnModeChat', 'btnModeReview', 'sectionSidebarConversations', 'btnSidebarNewChat',
    'sidebarConversationsContainer', 'activeChatTitle', 'chatWorkspaceName', 'chatWorkspacePath', 'chatWorkspaceBadge',
    'btnSwitchWorkspace', 'btnDeleteActiveProject', 'btnChatBackHome', 'selectChatProject', 'chatModelBadge',
    'selectChatModel', 'btnHeaderNewChat', 'btnDeleteActiveChat', 'chatStreamArea', 'chatEmptyState',
    'emptyStateTitle', 'emptyStateDesc', 'chatMessagesList', 'chatTypingIndicator', 'chatTypingText',
    'centralChatTextarea', 'btnSendCentralChat', 'chatProjectHintText', 'welcomeProjectsGrid', 'welcomeProjectsCount',
    'btnLandingLogin', 'btnLandingRegister', 'sectionPinnedTasks', 'pinnedTasksContainer', 'projectsTreeContainer',
    'pinnedCountBadge', 'navActiveProjectText', 'dbStatusPill', 'dbIndicator', 'dbStatusText',
    'btnThemeToggle', 'themeIcon', 'btnToggleSidebar', 'brandLink', 'btnBackToHome',
    'userAvatarSm', 'userNameSm', 'userAvatarSidebar', 'userNameSidebar', 'userEmailSidebar',
    'userMenuDropdown', 'btnLogoutBtn', 'userCardBtn', 'btnTopLogin', 'btnTopOpenProject',
    'btnTopNewTask', 'btnQuickAddProject', 'btnWorkspaceRunReview', 'btnPinActiveTask', 'pinActiveTaskIcon',
    'pinActiveTaskLabel', 'cardActionOpenProject', 'cardActionNewReview', 'btnBrowseFolder', 'folderPickerInput',
    'inputProjectPath', 'inputProjectName', 'viewProjectTitle', 'viewProjectPath', 'viewProjectLang',
    'viewTaskStatusPill', 'viewTaskTimestamp', 'metricCritical', 'metricHigh', 'metricMedium',
    'metricLow', 'totalFindingsNum', 'totalTasksNum', 'findingsListWrapper', 'toolTracesContainer',
    'tasksHistoryWrapper', 'modalOpenProject', 'modalCreateTask', 'modalAuth', 'modalAccountManager',
    'accProfileAvatar', 'accProfileUsername', 'accProfileRole', 'accProfileEmail', 'accStatProjects',
    'accStatTasks', 'accStatPinned', 'btnAccSwitchAccount', 'btnAccLogout', 'btnToggleLoginPassword',
    'btnToggleRegPassword', 'selectTaskProject', 'formOpenProject', 'formCreateTask', 'formLogin',
    'formRegister', 'tabAuthLogin', 'tabAuthRegister', 'btnTopAdmin', 'btnSelectWorkspace',
    'navDivider', 'btnAccAdminPanel', 'modalDbDiagnostics', 'diagStatusDot', 'diagStatusTitle',
    'diagStatusSub', 'diagDbHost', 'diagDbName', 'diagBackendUrl', 'diagLatencyVal',
    'btnRefreshDbDiag', 'modalAdminPanel', 'adminStatUsers', 'adminStatRoles', 'adminStatProjects',
    'adminStatTasks', 'adminStatCompleted', 'adminStatPending', 'adminStatFailed', 'adminStatTypes',
    'adminUsersTableBody', 'btnRefreshAdminData', 'adminCommandCenter', 'btnAdminQuickOpenPanel', 'quickStatUsersBadge',
    'quickStatProjectsBadge', 'quickStatTasksBadge', 'quickStatDbBadge', 'modalAdminInspectUser', 'inspectUserAvatar',
    'inspectUsername', 'inspectUserRole', 'inspectUserId', 'inspectUserEmail', 'tabInspectTasks',
    'tabInspectChats', 'tabInspectProjects', 'inspectTasksCount', 'inspectChatsCount', 'inspectProjectsCount',
    'inspectTasksContainer', 'inspectChatsContainer', 'inspectProjectsContainer',
  ].map(id => [id, document.getElementById(id)]));
  el.viewTabs = document.querySelectorAll(".view-tab-btn");
  function applyTheme(theme) {
    state.theme = theme;
    document.documentElement.setAttribute("data-theme", theme);
    localStorage.setItem("ai_agent_theme", theme);
    window.WorkspaceView.refresh();
    if (el.themeIcon) {
      if (theme === "dark") {
        el.themeIcon.innerHTML = `
          <circle cx="12" cy="12" r="5"></circle>
          <line x1="12" y1="1" x2="12" y2="3"></line>
          <line x1="12" y1="21" x2="12" y2="23"></line>
          <line x1="4.22" y1="4.22" x2="5.64" y2="5.64"></line>
          <line x1="18.36" y1="18.36" x2="19.78" y2="19.78"></line>
          <line x1="1" y1="12" x2="3" y2="12"></line>
          <line x1="21" y1="12" x2="23" y2="12"></line>
          <line x1="4.22" y1="19.78" x2="5.64" y2="18.36"></line>
          <line x1="18.36" y1="5.64" x2="19.78" y2="4.22"></line>
        `;
      } else {
        el.themeIcon.innerHTML = `<path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"></path>`;
      }
    }
  }

  function renderView() {
    if (!state.currentUser) {
      if (el.landingView) el.landingView.style.display = "flex";
      if (el.chatAssistantView) el.chatAssistantView.style.display = "none";
      if (el.welcomeView) el.welcomeView.style.display = "none";
      if (el.workspaceView) el.workspaceView.style.display = "none";
      if (el.navModePills) el.navModePills.style.display = "none";
      if (el.sectionSidebarConversations) el.sectionSidebarConversations.style.display = "none";
      if (el.appSidebar) el.appSidebar.classList.add("collapsed");
    } else {
      if (el.landingView) el.landingView.style.display = "none";
      if (el.appSidebar) el.appSidebar.classList.remove("collapsed");
      if (el.navModePills) el.navModePills.style.display = "inline-flex";
      if (el.sectionSidebarConversations) el.sectionSidebarConversations.style.display = "block";

      if (el.btnModeChat) el.btnModeChat.classList.toggle("active", state.view === "chat");
      if (el.btnModeReview) el.btnModeReview.classList.toggle("active", state.view !== "chat");

      if (state.view === "chat") {
        if (el.chatAssistantView) el.chatAssistantView.style.display = "flex";
        if (el.welcomeView) el.welcomeView.style.display = "none";
        if (el.workspaceView) el.workspaceView.style.display = "none";
      } else if (state.view === "workspace" && state.activeTask) {
        if (el.chatAssistantView) el.chatAssistantView.style.display = "none";
        if (el.welcomeView) el.welcomeView.style.display = "none";
        if (el.workspaceView) el.workspaceView.style.display = "flex";
      } else {
        if (el.chatAssistantView) el.chatAssistantView.style.display = "none";
        if (el.welcomeView) el.welcomeView.style.display = "flex";
        if (el.workspaceView) el.workspaceView.style.display = "none";
      }
    }
    window.WorkspaceView.sync();
  }

  function updateUserDisplays() {
    state.currentUser = api.getCurrentUser();
    if (state.currentUser && state.currentUser.username) {
      const u = state.currentUser.username;
      const initial = u.charAt(0).toUpperCase();

      if (el.btnTopLogin) el.btnTopLogin.style.display = "none";
      if (el.userMenuDropdown) el.userMenuDropdown.style.display = "flex";

      // Show authenticated navbar controls
      if (el.btnTopOpenProject) el.btnTopOpenProject.style.display = "inline-flex";
      if (el.btnTopNewTask) el.btnTopNewTask.style.display = "inline-flex";
      if (el.dbStatusPill) el.dbStatusPill.style.display = "inline-flex";
      if (el.btnToggleSidebar) el.btnToggleSidebar.style.display = "inline-flex";
      if (el.btnSelectWorkspace) el.btnSelectWorkspace.style.display = "inline-flex";
      if (el.navDivider) el.navDivider.style.display = "inline-block";

      // Admin role features
      const isAdmin = state.currentUser.role === 'ADMIN';
      if (el.btnTopAdmin) el.btnTopAdmin.style.display = isAdmin ? "inline-flex" : "none";
      if (el.btnAccAdminPanel) el.btnAccAdminPanel.style.display = isAdmin ? "flex" : "none";
      if (el.adminCommandCenter) el.adminCommandCenter.style.display = isAdmin ? "block" : "none";

      el.userNameSm.textContent = u;
      el.userAvatarSm.textContent = initial;
      el.userNameSidebar.textContent = u;
      el.userAvatarSidebar.textContent = initial;
      el.userEmailSidebar.textContent = state.currentUser.email || "Chưa có email";
      if (el.btnLogoutBtn) {
        el.btnLogoutBtn.title = `Đăng xuất khỏi ${u}`;
      }
    } else {
      if (el.btnTopLogin) el.btnTopLogin.style.display = "inline-flex";
      if (el.userMenuDropdown) el.userMenuDropdown.style.display = "none";

      // Hide all functional navbar controls when unauthenticated
      if (el.btnTopOpenProject) el.btnTopOpenProject.style.display = "none";
      if (el.btnTopNewTask) el.btnTopNewTask.style.display = "none";
      if (el.btnTopAdmin) el.btnTopAdmin.style.display = "none";
      if (el.dbStatusPill) el.dbStatusPill.style.display = "none";
      if (el.btnToggleSidebar) el.btnToggleSidebar.style.display = "none";
      if (el.btnSelectWorkspace) el.btnSelectWorkspace.style.display = "none";
      if (el.navDivider) el.navDivider.style.display = "none";
      if (el.btnAccAdminPanel) el.btnAccAdminPanel.style.display = "none";
      if (el.adminCommandCenter) el.adminCommandCenter.style.display = "none";

      el.userNameSm.textContent = "Đăng nhập";
      el.userAvatarSm.textContent = "?";
      el.userNameSidebar.textContent = "Chưa đăng nhập";
      el.userAvatarSidebar.textContent = "?";
      el.userEmailSidebar.textContent = "Bấm để đăng nhập";
      if (el.btnLogoutBtn) {
        el.btnLogoutBtn.title = "Đăng nhập tài khoản";
      }
    }
    renderView();
  }
  let dispose = () => {};
  function mount({state, openModal, closeModals, openLoginModal, openAccountOrAuthModal, performLogout, openDbDiagnosticsModal, runDbDiagnosticsPing, openAdminPanelModal, loadAdminDashboardData, setupAdminTabs, setupPasswordToggle, resetPasswordEyeIcons, loadDatabaseData, checkConnection, clearChatSessionState, selectProjectWorkspace, startNewChatConversation, renderSidebar, renderWelcomeTasks, selectTask, openAdminLogin, loadChatConversations, populateProjectSelect, deleteChatConversationById, handleDeleteProject, handleSendCentralChatMessage, updateChatProjectHint}) {
    dispose();
    const controller = new AbortController();
    dispose = () => controller.abort();
    function listen(target, type, handler) { target.addEventListener(type, handler, {signal: controller.signal}); }

    window.ChatView.mount({el, state, renderView, startNewChatConversation, deleteChatConversationById, handleDeleteProject, handleSendCentralChatMessage, updateChatProjectHint, signal: controller.signal});
    window.ProjectView.mount({el, state, closeModals, loadDatabaseData, selectProjectWorkspace, signal: controller.signal});
    // Antigravity Quick Commands (/review, /fix, /explain, /test)
    document.querySelectorAll("[data-cmd]").forEach(btn => {
      listen(btn, "click", () => {
        const cmd = btn.getAttribute("data-cmd");
        if (!el.centralChatTextarea) return;
        let text = cmd + " ";
        if (cmd === "/review") {
          text = "/review Hãy rà soát toàn diện mã nguồn dự án, phát hiện các lỗi logic, lỗ hổng bảo mật và đề xuất cải tiến.";
        } else if (cmd === "/fix") {
          text = "/fix Hãy chẩn đoán nguyên nhân lỗi và cung cấp bản vá hoàn chỉnh cho file: ";
        } else if (cmd === "/explain") {
          text = "/explain Hãy giải thích chi tiết cấu trúc, luồng thực thi và thuật toán của file: ";
        } else if (cmd === "/test") {
          text = "/test Hãy phân tích và tự động sinh bộ unit tests đầy đủ cho: ";
        }
        el.centralChatTextarea.value = text;
        el.centralChatTextarea.focus();
        const len = el.centralChatTextarea.value.length;
        el.centralChatTextarea.setSelectionRange(len, len);
      });
    });

    // Antigravity Workspace Switcher & Back Buttons
    if (el.btnSwitchWorkspace) {
      listen(el.btnSwitchWorkspace, "click", () => {
        window.WorkspaceView.navigate("projects");
      });
    }
    if (el.btnChatBackHome) {
      listen(el.btnChatBackHome, "click", () => {
        window.WorkspaceView.navigate("projects");
      });
    }
    if (el.btnSelectWorkspace) {
      listen(el.btnSelectWorkspace, "click", () => {
        window.WorkspaceView.navigate("projects");
      });
    }

    // Theme toggle
    if (el.btnThemeToggle) {
      listen(el.btnThemeToggle, "click", () => {
        applyTheme(state.theme === "dark" ? "light" : "dark");
        window.WorkspaceView.refresh();
      });
    }

    // Sidebar toggle
    if (el.btnToggleSidebar) {
      listen(el.btnToggleSidebar, "click", () => {
        if (window.matchMedia('(max-width: 800px)').matches) {
          const open = document.body.classList.toggle('sidebar-mobile-open');
          el.btnToggleSidebar.setAttribute('aria-expanded', String(open));
          if (open) document.querySelector('[data-nav-page]')?.focus();
        } else {
          const collapsed = document.body.classList.toggle('sidebar-mini');
          localStorage.setItem('ai_agent_sidebar', collapsed ? 'collapsed' : 'expanded');
          el.btnToggleSidebar.setAttribute('aria-expanded', String(!collapsed));
        }
      });
    }

    // Brand link / Back to Home
    if (el.brandLink) {
      listen(el.brandLink, "click", (event) => {
        event.preventDefault();
        if (!state.currentUser) {
          state.view = "landing";
          renderView();
          return;
        }
        window.WorkspaceView.navigate("dashboard");
        if (el.navActiveProjectText) el.navActiveProjectText.textContent = "HUS AI Agent";
        document.querySelectorAll(".task-item-card").forEach(i => i.classList.remove("active"));
      });
    }

    if (el.btnBackToHome) {
      listen(el.btnBackToHome, "click", () => {
        if (el.brandLink) el.brandLink.click();
      });
    }

    // Tab buttons in workspace (if present)
    if (el.viewTabs) {
      el.viewTabs.forEach(btn => {
        listen(btn, "click", () => {
          el.viewTabs.forEach(b => b.classList.remove("active"));
          btn.classList.add("active");
          const tab = btn.getAttribute("data-tab");
          const fb = document.getElementById("tabFindingsBody");
          if (fb) fb.style.display = tab === "findings" ? "block" : "none";
          const tb = document.getElementById("tabToolTraceBody");
          if (tb) tb.style.display = tab === "tooltrace" ? "block" : "none";
          const hb = document.getElementById("tabTasksHistoryBody");
          if (hb) hb.style.display = tab === "tasks" ? "block" : "none";
          const tabChat = document.getElementById("tabChatBody");
          if (tabChat) {
            tabChat.style.display = tab === "chat" ? "block" : "none";
            if (tab === "chat") loadChatConversations();
          }
        });
      });
    }

    setupPasswordToggle(el.btnToggleLoginPassword, document.getElementById("loginPassword"), controller.signal);
    setupPasswordToggle(el.btnToggleRegPassword, document.getElementById("regPassword"), controller.signal);

    // Modal Triggers
    const requireAuthOrOpen = (modal) => {
      if (!state.currentUser) {
        openLoginModal(false);
      } else {
        openModal(modal);
      }
    };

    if (el.btnTopOpenProject) listen(el.btnTopOpenProject, "click", () => requireAuthOrOpen(el.modalOpenProject));
    if (el.btnQuickAddProject) listen(el.btnQuickAddProject, "click", () => requireAuthOrOpen(el.modalOpenProject));
    if (el.cardActionOpenProject) listen(el.cardActionOpenProject, "click", () => requireAuthOrOpen(el.modalOpenProject));

    if (el.btnTopNewTask) {
      listen(el.btnTopNewTask, "click", () => {
        if (!state.currentUser) {
          openLoginModal(false);
          return;
        }
        populateProjectSelect();
        openModal(el.modalCreateTask);
      });
    }

    if (el.btnWorkspaceRunReview) {
      listen(el.btnWorkspaceRunReview, "click", () => {
        if (!state.currentUser) {
          openLoginModal(false);
          return;
        }
        populateProjectSelect();
        openModal(el.modalCreateTask);
      });
    }

    if (el.cardActionNewReview) {
      listen(el.cardActionNewReview, "click", () => {
        if (!state.currentUser) {
          openLoginModal(false);
          return;
        }
        populateProjectSelect();
        openModal(el.modalCreateTask);
      });
    }

    // Top Login button
    if (el.btnTopLogin) {
      listen(el.btnTopLogin, "click", () => openLoginModal(false));
    }

    // Folder Picker: Electron Native or Browser directory input
    if (el.btnBrowseFolder) {
      listen(el.btnBrowseFolder, "click", async () => {
        if (window.electronAPI && typeof window.electronAPI.openDirectory === "function") {
          try {
            const selectedPath = await window.electronAPI.openDirectory();
            if (selectedPath) {
              if (el.inputProjectPath) el.inputProjectPath.value = selectedPath;
              const parts = selectedPath.replace(/\\/g, '/').split('/').filter(Boolean);
              if (parts.length > 0 && el.inputProjectName) {
                el.inputProjectName.value = parts[parts.length - 1];
              }
            }
          } catch (e) {
            console.error("Lỗi khi mở hộp thoại thư mục Electron:", e);
            if (el.folderPickerInput) el.folderPickerInput.click();
          }
        } else if (el.folderPickerInput) {
          el.folderPickerInput.click();
        }
      });
    }

    if (el.folderPickerInput) {
      listen(el.folderPickerInput, "change", (e) => {
        if (e.target.files && e.target.files.length > 0) {
          const file = e.target.files[0];
          let dirPath = file.path;
          let folderName = "";
          if (file.webkitRelativePath) {
            folderName = file.webkitRelativePath.split('/')[0];
          }
          if (dirPath) {
            const cleanDir = dirPath.substring(0, dirPath.lastIndexOf(file.name)).replace(/[\\\/]$/, '');
            if (el.inputProjectPath) el.inputProjectPath.value = cleanDir || dirPath;
            const parts = (cleanDir || dirPath).replace(/\\/g, '/').split('/').filter(Boolean);
            if (parts.length > 0 && el.inputProjectName) el.inputProjectName.value = parts[parts.length - 1];
          } else if (folderName) {
            if (el.inputProjectPath) el.inputProjectPath.value = folderName;
            if (el.inputProjectName) el.inputProjectName.value = folderName;
          }
        }
      });
    }

    // Workspace Pin Active Task button
    if (el.btnPinActiveTask) {
      listen(el.btnPinActiveTask, "click", async () => {
        if (!state.activeTask) return;
        const updated = await window.apiClient.togglePin(state.activeTask.id);
        if (updated) {
          state.activeTask.isPinned = updated.isPinned;
          const idx = state.tasks.findIndex(t => t.id === state.activeTask.id);
          if (idx >= 0) state.tasks[idx].isPinned = updated.isPinned;

          if (el.pinActiveTaskIcon) el.pinActiveTaskIcon.style.opacity = updated.isPinned ? "1" : "0.6";
          if (el.pinActiveTaskLabel) el.pinActiveTaskLabel.textContent = updated.isPinned ? "Đã ghim" : "Ghim tác vụ";
          renderSidebar();
          renderWelcomeTasks();
        }
      });
    }

    // User Profile & Account Manager Modal Triggers
    if (el.userMenuDropdown) listen(el.userMenuDropdown, "click", openAccountOrAuthModal);
    if (el.userCardBtn) listen(el.userCardBtn, "click", openAccountOrAuthModal);

    window.AccountView.mount({api: window.apiClient, openModal, closeModals, applyTheme});

    if (el.btnAccSwitchAccount) {
      listen(el.btnAccSwitchAccount, "click", () => {
        closeModals();
        openLoginModal(false);
      });
    }

    if (el.btnAccAdminPanel) {
      listen(el.btnAccAdminPanel, "click", () => {
        closeModals();
        openAdminPanelModal();
      });
    }

    if (el.btnAccLogout) {
      listen(el.btnAccLogout, "click", performLogout);
    }

    // Top Admin Button Action
    if (el.btnTopAdmin) {
      listen(el.btnTopAdmin, "click", openAdminPanelModal);
    }

    // Admin Panel Refresh Data Button & Tabs
    setupAdminTabs();
    if (el.btnRefreshAdminData) {
      listen(el.btnRefreshAdminData, "click", loadAdminDashboardData);
    }

    // Admin Command Center Quick Open Button on Welcome Screen
    if (el.btnAdminQuickOpenPanel) {
      listen(el.btnAdminQuickOpenPanel, "click", openAdminPanelModal);
    }

    // Inspect Modal View Tabs Switcher (Tasks / Chats / Projects)
    function switchInspectTab(activeTab) {
      if (el.tabInspectTasks) el.tabInspectTasks.classList.toggle("active", activeTab === "tasks");
      if (el.tabInspectChats) el.tabInspectChats.classList.toggle("active", activeTab === "chats");
      if (el.tabInspectProjects) el.tabInspectProjects.classList.toggle("active", activeTab === "projects");
      if (el.inspectTasksContainer) el.inspectTasksContainer.style.display = activeTab === "tasks" ? "flex" : "none";
      if (el.inspectChatsContainer) el.inspectChatsContainer.style.display = activeTab === "chats" ? "flex" : "none";
      if (el.inspectProjectsContainer) el.inspectProjectsContainer.style.display = activeTab === "projects" ? "flex" : "none";
    }

    if (el.tabInspectTasks) {
      listen(el.tabInspectTasks, "click", () => switchInspectTab("tasks"));
    }
    if (el.tabInspectChats) {
      listen(el.tabInspectChats, "click", () => switchInspectTab("chats"));
    }
    if (el.tabInspectProjects) {
      listen(el.tabInspectProjects, "click", () => switchInspectTab("projects"));
    }

    // Database Diagnostics Modal Triggers & Refresh
    if (el.dbStatusPill) {
      listen(el.dbStatusPill, "click", openDbDiagnosticsModal);
    }

    if (el.btnRefreshDbDiag) {
      listen(el.btnRefreshDbDiag, "click", runDbDiagnosticsPing);
    }

    // Landing View CTA buttons
    if (el.btnLandingLogin) {
      listen(el.btnLandingLogin, "click", () => openLoginModal(false));
    }

    if (el.btnLandingRegister) {
      listen(el.btnLandingRegister, "click", () => openLoginModal(true));
    }

    const btnLandingQuick = document.getElementById("btnLandingQuickLogin");
    if (btnLandingQuick) {
      listen(btnLandingQuick, "click", openAdminLogin);
    }

    const btnQuickAdmin = document.getElementById("btnQuickLoginAdmin");
    if (btnQuickAdmin) {
      listen(btnQuickAdmin, "click", openAdminLogin);
    }

    // Sidebar LOGOUT Button
    if (el.btnLogoutBtn) {
      listen(el.btnLogoutBtn, "click", (e) => {
        e.stopPropagation();
        if (state.currentUser) {
          performLogout();
        } else {
          openLoginModal(false);
        }
      });
    }

    // Modal Close buttons
    document.querySelectorAll("[data-close-modal]").forEach(b => {
      listen(b, "click", () => {
        const modal = b.closest('.modal-backdrop');
        if (modal?.id === 'modalAdminInspectUser' && document.getElementById('modalAdminPanel')?.classList.contains('active')) {
          modal.classList.remove('active');
        } else closeModals();
      });
    });

    window.TaskForm.mount({state, el, closeModals, loadDatabaseData, selectTask});

    window.AuthView.mount({el, state, closeModals, clearChatSessionState, updateUserDisplays, checkConnection, loadDatabaseData, renderView});

    // Keyboard Shortcuts (Enter on Welcome -> Open Project; Ctrl+K -> Search)
    listen(document, "keydown", (e) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        requireAuthOrOpen(el.modalCreateTask);
      }
    });

    // Refresh Database Tasks button
    const btnRefresh = document.getElementById("btnRefreshTasks");
    if (btnRefresh) {
      listen(btnRefresh, "click", async () => {
        btnRefresh.textContent = "Đang đồng bộ...";
        await loadDatabaseData();
        btnRefresh.innerHTML = `
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="23 4 23 10 17 10"></polyline>
            <polyline points="1 20 1 14 7 14"></polyline>
            <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"></path>
          </svg>
          Đồng bộ CSDL
        `;
      });
    }

    // Listen to background task completion
    listen(window, "agent-task-updated", (e) => {
      loadDatabaseData();
    });
  }
  async function openDbDiagnosticsModal() {
    el.modalDbDiagnostics?.classList.add("active");
    await runDbDiagnosticsPing();
  }
  async function runDbDiagnosticsPing() {
    if (el.diagLatencyVal) el.diagLatencyVal.textContent = "Đang kiểm tra...";
    if (el.diagStatusTitle) el.diagStatusTitle.textContent = "Đang ping CSDL MySQL...";

    const pingResult = await api.pingDatabase();
    if (pingResult && pingResult.online) {
      if (el.diagStatusDot) el.diagStatusDot.className = "db-diag-dot online";
      if (el.diagStatusTitle) el.diagStatusTitle.textContent = "CSDL MySQL: Đã kết nối (Sẵn sàng)";
      if (el.diagStatusSub) el.diagStatusSub.textContent = "Hệ thống cơ sở dữ liệu quan hệ lưu trữ thực thể dự án và kết quả tác tử AI";
      if (el.diagLatencyVal) el.diagLatencyVal.textContent = `${pingResult.latency} ms`;
      if (el.diagBackendUrl) el.diagBackendUrl.textContent = pingResult.url || "http://localhost:8080";
      if (el.diagDbHost) el.diagDbHost.textContent = "localhost:3306";
      if (el.diagDbName) el.diagDbName.textContent = "ai_agent (MySQL 8.0+)";
    } else {
      if (el.diagStatusDot) el.diagStatusDot.className = "db-diag-dot offline";
      if (el.diagStatusTitle) el.diagStatusTitle.textContent = "Chưa kết nối CSDL MySQL hoặc Backend";
      if (el.diagStatusSub) el.diagStatusSub.textContent = pingResult.error || "Không thể gửi yêu cầu đến Spring Boot backend";
      if (el.diagLatencyVal) el.diagLatencyVal.textContent = "Mất kết nối";
    }
  }
  async function checkConnection() {
    if (el.dbStatusText) el.dbStatusText.textContent = "Kiểm tra CSDL...";
    try {
      const isOnline = await api.checkHealth();
      if (isOnline) {
        if (el.dbStatusPill) el.dbStatusPill.className = "db-status-pill";
        if (el.dbIndicator) el.dbIndicator.style.background = "var(--diff-add)";
        if (el.dbStatusText) el.dbStatusText.textContent = "CSDL MySQL: Đã kết nối";
      } else {
        if (el.dbStatusPill) el.dbStatusPill.className = "db-status-pill demo";
        if (el.dbIndicator) el.dbIndicator.style.background = "#f59e0b";
        if (el.dbStatusText) el.dbStatusText.textContent = "Chưa kết nối máy chủ";
      }
    } catch (_) {
      if (el.dbStatusPill) el.dbStatusPill.className = "db-status-pill demo";
      if (el.dbIndicator) el.dbIndicator.style.background = "#f59e0b";
      if (el.dbStatusText) el.dbStatusText.textContent = "Chưa kết nối máy chủ";
    }
  }
    return {openDbDiagnosticsModal, runDbDiagnosticsPing, checkConnection, el, applyTheme, renderView, updateUserDisplays, mount, dispose: () => dispose()};
  }
  window.ShellView = {create};
})();
