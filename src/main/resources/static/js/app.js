/**
 * ============================================================================
 * MAIN APPLICATION CONTROLLER (SINGLE PAGE APP - WEB & ELECTRON)
 * ============================================================================
 * Mục đích:
 * 1. Điều khiển trạng thái ứng dụng (State Management: view, projects, tasks, user, theme).
 * 2. Phân hệ giao diện chính:
 *    - Landing View: Màn hình giới thiệu chào mừng khi chưa đăng nhập.
 *    - Welcome View: Bảng điều khiển trung tâm (Command Center), thống kê nhanh, danh sách task đã ghim.
 *    - Workspace View: Không gian làm việc chi tiết cho từng tác vụ (Kết quả, Tool Trace, Lịch sử, AI Chat).
 * 3. Tương tác đa nền tảng:
 *    - Tích hợp Native Windows Directory Picker qua Electron IPC (`window.electronAPI.openDirectory()`).
 *    - Hỗ trợ đầy đủ phím tắt (Enter mở dự án, Ctrl+K tạo tác vụ mới).
 *    - Hỗ trợ đổi giao diện Sáng / Tối (Theme Light/Dark) và lưu cấu hình cá nhân.
 * 4. Phân hệ quản trị (Role ADMIN):
 *    - Modal Admin Panel 5 tabs: Quản lý người dùng, Bật/tắt Tools, Cấu hình Model/API Key, Giám sát lỗi, Audit Log.
 * ============================================================================
 */
(function() {
  // App State
  const state = {
    view: "welcome", // "welcome" | "chat" | "workspace"
    projects: [],
    tasks: [],
    pinnedTasks: [],
    conversations: [],
    activeConversationId: null,
    activeConversation: null,
    isChatSending: false,
    activeProject: null,
    activeTask: null,
    activeTab: "findings",
    currentUser: window.apiClient.getCurrentUser(),
    theme: localStorage.getItem("ai_agent_theme") || "light",
    expandedProjectIds: new Set()
  };

  // DOM Elements
  const el = {
    appSidebar: document.getElementById("appSidebar"),
    landingView: document.getElementById("landingView"),
    welcomeView: document.getElementById("welcomeView"),
    workspaceView: document.getElementById("workspaceView"),
    chatAssistantView: document.getElementById("chatAssistantView"),
    navModePills: document.getElementById("navModePills"),
    btnModeChat: document.getElementById("btnModeChat"),
    btnModeReview: document.getElementById("btnModeReview"),
    sectionSidebarConversations: document.getElementById("sectionSidebarConversations"),
    btnSidebarNewChat: document.getElementById("btnSidebarNewChat"),
    sidebarConversationsContainer: document.getElementById("sidebarConversationsContainer"),
    activeChatTitle: document.getElementById("activeChatTitle"),
    chatWorkspaceName: document.getElementById("chatWorkspaceName"),
    chatWorkspacePath: document.getElementById("chatWorkspacePath"),
    btnSwitchWorkspace: document.getElementById("btnSwitchWorkspace"),
    btnDeleteActiveProject: document.getElementById("btnDeleteActiveProject"),
    btnChatBackHome: document.getElementById("btnChatBackHome"),
    selectChatProject: document.getElementById("selectChatProject"),
    chatModelBadge: document.getElementById("chatModelBadge"),
    selectChatModel: document.getElementById("selectChatModel"),
    btnHeaderNewChat: document.getElementById("btnHeaderNewChat"),
    btnDeleteActiveChat: document.getElementById("btnDeleteActiveChat"),
    chatStreamArea: document.getElementById("chatStreamArea"),
    chatEmptyState: document.getElementById("chatEmptyState"),
    emptyStateTitle: document.getElementById("emptyStateTitle"),
    emptyStateDesc: document.getElementById("emptyStateDesc"),
    chatMessagesList: document.getElementById("chatMessagesList"),
    chatTypingIndicator: document.getElementById("chatTypingIndicator"),
    chatTypingText: document.getElementById("chatTypingText"),
    centralChatTextarea: document.getElementById("centralChatTextarea"),
    btnSendCentralChat: document.getElementById("btnSendCentralChat"),
    chatProjectHintText: document.getElementById("chatProjectHintText"),
    welcomeProjectsGrid: document.getElementById("welcomeProjectsGrid"),
    welcomeProjectsCount: document.getElementById("welcomeProjectsCount"),
    btnLandingLogin: document.getElementById("btnLandingLogin"),
    btnLandingRegister: document.getElementById("btnLandingRegister"),
    sectionPinnedTasks: document.getElementById("sectionPinnedTasks"),
    pinnedTasksContainer: document.getElementById("pinnedTasksContainer"),
    projectsTreeContainer: document.getElementById("projectsTreeContainer"),
    pinnedCountBadge: document.getElementById("pinnedCountBadge"),
    navActiveProjectText: document.getElementById("navActiveProjectText"),
    dbStatusPill: document.getElementById("dbStatusPill"),
    dbIndicator: document.getElementById("dbIndicator"),
    dbStatusText: document.getElementById("dbStatusText"),
    btnThemeToggle: document.getElementById("btnThemeToggle"),
    themeIcon: document.getElementById("themeIcon"),
    btnToggleSidebar: document.getElementById("btnToggleSidebar"),
    brandLink: document.getElementById("brandLink"),
    btnBackToHome: document.getElementById("btnBackToHome"),
    // User info
    userAvatarSm: document.getElementById("userAvatarSm"),
    userNameSm: document.getElementById("userNameSm"),
    userAvatarSidebar: document.getElementById("userAvatarSidebar"),
    userNameSidebar: document.getElementById("userNameSidebar"),
    userEmailSidebar: document.getElementById("userEmailSidebar"),
    userMenuDropdown: document.getElementById("userMenuDropdown"),
    btnLogoutBtn: document.getElementById("btnLogoutBtn"),
    userCardBtn: document.getElementById("userCardBtn"),
    btnTopLogin: document.getElementById("btnTopLogin"),
    // Top Nav buttons
    btnTopOpenProject: document.getElementById("btnTopOpenProject"),
    btnTopNewTask: document.getElementById("btnTopNewTask"),
    btnQuickAddProject: document.getElementById("btnQuickAddProject"),
    btnWorkspaceRunReview: document.getElementById("btnWorkspaceRunReview"),
    btnPinActiveTask: document.getElementById("btnPinActiveTask"),
    pinActiveTaskIcon: document.getElementById("pinActiveTaskIcon"),
    pinActiveTaskLabel: document.getElementById("pinActiveTaskLabel"),
    // Action Cards
    cardActionOpenProject: document.getElementById("cardActionOpenProject"),
    cardActionNewReview: document.getElementById("cardActionNewReview"),
    // Folder Picker
    btnBrowseFolder: document.getElementById("btnBrowseFolder"),
    folderPickerInput: document.getElementById("folderPickerInput"),
    inputProjectPath: document.getElementById("inputProjectPath"),
    inputProjectName: document.getElementById("inputProjectName"),
    // Workspace Details
    viewProjectTitle: document.getElementById("viewProjectTitle"),
    viewProjectPath: document.getElementById("viewProjectPath"),
    viewProjectLang: document.getElementById("viewProjectLang"),
    viewTaskStatusPill: document.getElementById("viewTaskStatusPill"),
    viewTaskTimestamp: document.getElementById("viewTaskTimestamp"),
    metricCritical: document.getElementById("metricCritical"),
    metricHigh: document.getElementById("metricHigh"),
    metricMedium: document.getElementById("metricMedium"),
    metricLow: document.getElementById("metricLow"),
    totalFindingsNum: document.getElementById("totalFindingsNum"),
    totalTasksNum: document.getElementById("totalTasksNum"),
    findingsListWrapper: document.getElementById("findingsListWrapper"),
    toolTracesContainer: document.getElementById("toolTracesContainer"),
    tasksHistoryWrapper: document.getElementById("tasksHistoryWrapper"),
    viewTabs: document.querySelectorAll(".view-tab-btn"),
    // Modals
    modalOpenProject: document.getElementById("modalOpenProject"),
    modalCreateTask: document.getElementById("modalCreateTask"),
    modalAuth: document.getElementById("modalAuth"),
    modalAccountManager: document.getElementById("modalAccountManager"),
    accProfileAvatar: document.getElementById("accProfileAvatar"),
    accProfileUsername: document.getElementById("accProfileUsername"),
    accProfileRole: document.getElementById("accProfileRole"),
    accProfileEmail: document.getElementById("accProfileEmail"),
    accStatProjects: document.getElementById("accStatProjects"),
    accStatTasks: document.getElementById("accStatTasks"),
    accStatPinned: document.getElementById("accStatPinned"),
    btnAccSwitchAccount: document.getElementById("btnAccSwitchAccount"),
    btnAccLogout: document.getElementById("btnAccLogout"),
    btnToggleLoginPassword: document.getElementById("btnToggleLoginPassword"),
    btnToggleRegPassword: document.getElementById("btnToggleRegPassword"),
    selectTaskProject: document.getElementById("selectTaskProject"),
    formOpenProject: document.getElementById("formOpenProject"),
    formCreateTask: document.getElementById("formCreateTask"),
    formLogin: document.getElementById("formLogin"),
    formRegister: document.getElementById("formRegister"),
    tabAuthLogin: document.getElementById("tabAuthLogin"),
    tabAuthRegister: document.getElementById("tabAuthRegister"),
    btnTopAdmin: document.getElementById("btnTopAdmin"),
    btnSelectWorkspace: document.getElementById("btnSelectWorkspace"),
    navDivider: document.getElementById("navDivider"),
    btnAccAdminPanel: document.getElementById("btnAccAdminPanel"),
    modalDbDiagnostics: document.getElementById("modalDbDiagnostics"),
    diagStatusDot: document.getElementById("diagStatusDot"),
    diagStatusTitle: document.getElementById("diagStatusTitle"),
    diagStatusSub: document.getElementById("diagStatusSub"),
    diagDbHost: document.getElementById("diagDbHost"),
    diagDbName: document.getElementById("diagDbName"),
    diagBackendUrl: document.getElementById("diagBackendUrl"),
    diagLatencyVal: document.getElementById("diagLatencyVal"),
    btnRefreshDbDiag: document.getElementById("btnRefreshDbDiag"),
    modalAdminPanel: document.getElementById("modalAdminPanel"),
    adminStatUsers: document.getElementById("adminStatUsers"),
    adminStatRoles: document.getElementById("adminStatRoles"),
    adminStatProjects: document.getElementById("adminStatProjects"),
    adminStatTasks: document.getElementById("adminStatTasks"),
    adminStatCompleted: document.getElementById("adminStatCompleted"),
    adminStatPending: document.getElementById("adminStatPending"),
    adminStatFailed: document.getElementById("adminStatFailed"),
    adminStatTypes: document.getElementById("adminStatTypes"),
    adminUsersTableBody: document.getElementById("adminUsersTableBody"),
    btnRefreshAdminData: document.getElementById("btnRefreshAdminData"),
    // Admin Command Center on Welcome
    adminCommandCenter: document.getElementById("adminCommandCenter"),
    btnAdminQuickOpenPanel: document.getElementById("btnAdminQuickOpenPanel"),
    quickStatUsersBadge: document.getElementById("quickStatUsersBadge"),
    quickStatProjectsBadge: document.getElementById("quickStatProjectsBadge"),
    quickStatTasksBadge: document.getElementById("quickStatTasksBadge"),
    quickStatDbBadge: document.getElementById("quickStatDbBadge"),
    // User Inspection Modal
    modalAdminInspectUser: document.getElementById("modalAdminInspectUser"),
    inspectUserAvatar: document.getElementById("inspectUserAvatar"),
    inspectUsername: document.getElementById("inspectUsername"),
    inspectUserRole: document.getElementById("inspectUserRole"),
    inspectUserId: document.getElementById("inspectUserId"),
    inspectUserEmail: document.getElementById("inspectUserEmail"),
    tabInspectTasks: document.getElementById("tabInspectTasks"),
    tabInspectProjects: document.getElementById("tabInspectProjects"),
    inspectTasksCount: document.getElementById("inspectTasksCount"),
    inspectProjectsCount: document.getElementById("inspectProjectsCount"),
    inspectTasksContainer: document.getElementById("inspectTasksContainer"),
    inspectProjectsContainer: document.getElementById("inspectProjectsContainer")
  };

  // Helper: Format real relative time (Delegated to ProjectView)
  function formatRelativeTime(dateString) {
    return window.ProjectView ? window.ProjectView.formatRelativeTime(dateString) : "vừa xong";
  }

  // Theme Management
  function applyTheme(theme) {
    state.theme = theme;
    document.documentElement.setAttribute("data-theme", theme);
    localStorage.setItem("ai_agent_theme", theme);
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

  // Modal Management
  function openModal(modalEl) {
    if (modalEl) modalEl.classList.add("active");
  }

  function closeModals() {
    document.querySelectorAll(".modal-backdrop").forEach(m => m.classList.remove("active"));
  }

  // Helper: Reset and open login/register modal securely
  function openLoginModal(isRegister = false) {
    const loginU = document.getElementById("loginUsername");
    const loginP = document.getElementById("loginPassword");
    const regU = document.getElementById("regUsername");
    const regE = document.getElementById("regEmail");
    const regP = document.getElementById("regPassword");
    if (loginU) loginU.value = "";
    if (loginP) {
      loginP.value = "";
      loginP.type = "password";
    }
    if (regU) regU.value = "";
    if (regE) regE.value = "";
    if (regP) {
      regP.value = "";
      regP.type = "password";
    }
    resetPasswordEyeIcons();

    if (isRegister) {
      if (el.tabAuthRegister) el.tabAuthRegister.click();
    } else {
      if (el.tabAuthLogin) el.tabAuthLogin.click();
    }
    openModal(el.modalAuth);
  }

  // Helper: Open Account Management if logged in, else open Login modal
  function openAccountOrAuthModal() {
    if (state.currentUser) {
      if (el.accProfileAvatar) {
        el.accProfileAvatar.textContent = (state.currentUser.username || '?').charAt(0).toUpperCase();
      }
      if (el.accProfileUsername) {
        el.accProfileUsername.textContent = state.currentUser.username || "Tài khoản";
      }
      if (el.accProfileRole) {
        el.accProfileRole.textContent = state.currentUser.role === 'ADMIN' ? 'Quản trị viên (ADMIN)' : (state.currentUser.role || 'Thành viên');
      }
      if (el.btnAccAdminPanel) {
        el.btnAccAdminPanel.style.display = state.currentUser.role === 'ADMIN' ? 'flex' : 'none';
      }
      if (el.accProfileEmail) {
        el.accProfileEmail.textContent = state.currentUser.email || `${state.currentUser.username}@test.com`;
      }
      if (el.accStatProjects) {
        el.accStatProjects.textContent = state.projects ? state.projects.length : 0;
      }
      if (el.accStatTasks) {
        el.accStatTasks.textContent = state.tasks ? state.tasks.length : 0;
      }
      if (el.accStatPinned) {
        el.accStatPinned.textContent = state.pinnedTasks ? state.pinnedTasks.length : 0;
      }
      openModal(el.modalAccountManager);
    } else {
      openLoginModal(false);
    }
  }

  // Helper: Clear Chat Session State on Logout / Account Switch
  function clearChatSessionState() {
    state.activeConversationId = null;
    state.activeConversation = null;
    state.conversations = [];
    state.activeProject = null;
    state.expandedProjectIds.clear();

    if (el.chatMessagesList) el.chatMessagesList.innerHTML = "";
    if (el.chatEmptyState) el.chatEmptyState.style.display = "flex";
    if (el.activeChatTitle) el.activeChatTitle.textContent = state.activeProject ? state.activeProject.name : "Hỏi đáp chung";
    if (el.chatWorkspaceName) el.chatWorkspaceName.textContent = state.activeProject ? state.activeProject.name : "Hỏi đáp chung";
    if (el.chatWorkspaceBadge) el.chatWorkspaceBadge.title = state.activeProject ? `Thư mục: ${state.activeProject.path || state.activeProject.name}` : "Hỏi đáp chung (Không gắn dự án)";
    if (el.chatWorkspacePath) {
      el.chatWorkspacePath.textContent = "";
      el.chatWorkspacePath.style.display = "none";
    }
    if (el.btnDeleteActiveProject) el.btnDeleteActiveProject.style.display = "none";
    if (el.btnSelectWorkspace) el.btnSelectWorkspace.style.display = "none";
    if (el.centralChatTextarea) el.centralChatTextarea.value = "";
    if (el.chatProjectHintText) el.chatProjectHintText.textContent = "Enter để gửi • Shift+Enter xuống dòng";
    if (el.emptyStateTitle) el.emptyStateTitle.textContent = "Soopi AI Coding Assistant";
    if (el.emptyStateDesc) el.emptyStateDesc.textContent = "Không gian làm việc trực tiếp trên dự án. Chọn hoặc mở dự án để AI đọc mã nguồn và thực thi các tính năng chuyên sâu.";
  }

  // Helper: Perform safe logout
  function performLogout() {
    const confirmed = confirm("Bạn có chắc chắn muốn đăng xuất khỏi tài khoản Soopi không?");
    if (!confirmed) return;

    window.apiClient.logout();
    state.currentUser = null;
    state.projects = [];
    state.tasks = [];
    state.pinnedTasks = [];
    clearChatSessionState();
    state.view = "landing";
    closeModals();
    updateUserDisplays();
    renderSidebar();
  }

  // Database Diagnostics Modal Handlers (MySQL status & ping)
  async function openDbDiagnosticsModal() {
    openModal(el.modalDbDiagnostics);
    await runDbDiagnosticsPing();
  }

  async function runDbDiagnosticsPing() {
    if (el.diagLatencyVal) el.diagLatencyVal.textContent = "Đang kiểm tra...";
    if (el.diagStatusTitle) el.diagStatusTitle.textContent = "Đang ping CSDL MySQL...";

    const pingResult = await window.apiClient.pingDatabase();
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

  // Admin Dashboard Modal Handlers (Delegated to AdminView module)
  async function openAdminPanelModal() {
    return window.AdminView.openAdminPanelModal(el, state, window.apiClient, openModal);
  }

  async function loadAdminDashboardData() {
    return window.AdminView.loadAdminDashboardData(el, state, window.apiClient);
  }

  async function openInspectUserModal(user) {
    return window.AdminView.openInspectUserModal(user, el, window.apiClient, openModal);
  }

  function setupAdminTabs() {
    return window.AdminView.setupAdminTabs(window.apiClient);
  }

  async function loadAdminTools() {
    return window.AdminView.loadAdminTools(el, window.apiClient);
  }

  async function loadAdminSettings() {
    return window.AdminView.loadAdminSettings(el, window.apiClient);
  }

  async function loadAdminSystemTasks() {
    return window.AdminView.loadAdminSystemTasks(el, window.apiClient);
  }

  async function loadAdminAuditLogs() {
    return window.AdminView.loadAdminAuditLogs(window.apiClient);
  }
  // ==========================================================================
  // AI CODING ASSISTANT CHATBOT PLATFORM CONTROLLER
  // ==========================================================================

  // Markdown Formatter (Delegated to MarkdownUtils module)
  function formatMarkdown(content) {
    return window.MarkdownUtils.formatMarkdown(content);
  }
  // Populate Chat Project Context Dropdown
  function populateChatProjectDropdown() {
    if (!el.selectChatProject) return;
    const currentVal = el.selectChatProject.value;
    let optionsHtml = `<option value="">Không gắn dự án (Hỏi đáp chung)</option>`;
    if (state.projects && state.projects.length > 0) {
      optionsHtml += state.projects.map(p => `
        <option value="${p.id}" ${currentVal == p.id ? 'selected' : ''}>
          ${p.name} (${p.language || 'Code'})
        </option>
      `).join("");
    }
    el.selectChatProject.innerHTML = optionsHtml;
    updateChatProjectHint();
  }

  function updateChatProjectHint() {
    if (!el.chatProjectHintText) return;
    el.chatProjectHintText.textContent = "Enter để gửi • Shift+Enter xuống dòng";
  }

  // Chat Conversations & Messaging (Delegated to ChatView module)
  async function loadChatConversations(shouldAutoSelect = false) {
    return window.ChatView.loadChatConversations(state, el, window.apiClient, shouldAutoSelect, renderSidebar);
  }

  function renderSidebarConversations() {
    renderSidebar();
  }

  async function selectChatConversation(id) {
    return window.ChatView.selectChatConversation(id, state, el, window.apiClient, renderSidebar);
  }

  function renderChatMessagesStream(messages) {
    return window.ChatView.renderChatMessagesStream(messages, el);
  }

  function startNewChatConversation() {
    return window.ChatView.startNewChatConversation(state, el, renderSidebar);
  }

  async function deleteChatConversationById(convId) {
    return window.ChatView.deleteChatConversationById(convId, state, el, window.apiClient, renderSidebar);
  }

  async function handleSendCentralChatMessage(customPrompt) {
    return window.ChatView.handleSendCentralChatMessage(customPrompt, state, el, window.apiClient, renderSidebar);
  }
  // Password visibility toggle helpers
  function setupPasswordToggle(btn, input) {
    if (!btn || !input) return;
    btn.addEventListener("click", (e) => {
      e.preventDefault();
      e.stopPropagation();
      const isPassword = input.type === "password";
      input.type = isPassword ? "text" : "password";
      const eyeOpen = btn.querySelector(".eye-open-icon");
      const eyeClosed = btn.querySelector(".eye-closed-icon");
      if (eyeOpen) eyeOpen.style.display = isPassword ? "none" : "block";
      if (eyeClosed) eyeClosed.style.display = isPassword ? "block" : "none";
    });
  }

  function resetPasswordEyeIcons() {
    [el.btnToggleLoginPassword, el.btnToggleRegPassword].forEach(btn => {
      if (btn) {
        const eyeOpen = btn.querySelector(".eye-open-icon");
        const eyeClosed = btn.querySelector(".eye-closed-icon");
        if (eyeOpen) eyeOpen.style.display = "block";
        if (eyeClosed) eyeClosed.style.display = "none";
      }
    });
  }

  // View Switcher: Unauthenticated Landing vs Chatbot vs Welcome vs Workspace
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
  }
  window.renderView = renderView;

  // Update User Profile Displays & Navbar Controls
  function updateUserDisplays() {
    state.currentUser = window.apiClient.getCurrentUser();
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
      el.userEmailSidebar.textContent = state.currentUser.email || `${u}@test.com`;
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

  // Load Data from Database
  async function loadDatabaseData() {
    updateUserDisplays();
    if (!state.currentUser) {
      state.projects = [];
      state.tasks = [];
      state.pinnedTasks = [];
      renderSidebar();
      renderWelcomeTasks();
      populateProjectSelect();
      return;
    }

    try {
      const [projects, tasks] = await Promise.all([
        window.apiClient.getProjects(),
        window.apiClient.getTasks()
      ]);
      state.projects = projects || [];
      state.tasks = tasks || [];

      // Pinned tasks: only tasks with isPinned === true (do not default to recent tasks)
      state.pinnedTasks = state.tasks.filter(t => t.isPinned === true);

      renderSidebar();
      renderWelcomeTasks();
      populateProjectSelect();
      populateChatProjectDropdown();
      await loadChatConversations(false);
      await loadAvailableModels();
      startTaskPollingIfNeeded();

      if (state.currentUser && state.currentUser.role === 'ADMIN') {
        loadAdminDashboardData();
      }
    } catch (err) {
      console.error("Lỗi khi tải dữ liệu từ CSDL:", err);
    }
  }

  // Load Available Models for Chat
  async function loadAvailableModels() {
    try {
      const data = await window.apiClient.getSystemModels();
      if (data && el.selectChatModel) {
        const allowed = data.allowedModels || ["gemini-3.5-flash-lite", "gemini-2.5-flash", "gemini-2.5-pro", "gemini-1.5-flash", "gemini-1.5-pro"];
        const currentActive = (state.activeConversation && state.activeConversation.model) || el.selectChatModel.value || data.activeModel || "gemini-3.5-flash-lite";
        el.selectChatModel.innerHTML = allowed.map(m => `
          <option value="${m}" ${m === currentActive ? 'selected' : ''}>${m}${m === data.activeModel ? ' (Mặc định)' : ''}</option>
        `).join("");
      }
    } catch (e) {
      console.warn("Could not load system models:", e);
    }
  }

  // Auto-polling for asynchronous AI execution
  let taskPollInterval = null;
  function startTaskPollingIfNeeded() {
    const hasUnfinishedTasks = state.tasks.some(t => t.status === "PENDING" || t.status === "PROCESSING");
    if (hasUnfinishedTasks && !taskPollInterval) {
      console.log("Đang tự động theo dõi tiến trình AI Agent...");
      taskPollInterval = setInterval(async () => {
        const prevActiveTaskId = state.activeTask ? state.activeTask.id : null;
        const tasks = await window.apiClient.getTasks();
        if (Array.isArray(tasks)) {
          state.tasks = tasks;
          state.pinnedTasks = state.tasks.filter(t => t.isPinned === true);
          renderSidebar();
          renderWelcomeTasks();

          if (prevActiveTaskId) {
            const updatedActive = state.tasks.find(t => t.id === prevActiveTaskId);
            if (updatedActive && state.activeTask && (updatedActive.status !== state.activeTask.status || updatedActive.output !== state.activeTask.output)) {
              const project = state.projects.find(p => p.id === updatedActive.projectId) || state.activeProject;
              selectTask(updatedActive, project);
            }
          }
        }

        const stillUnfinished = state.tasks.some(t => t.status === "PENDING" || t.status === "PROCESSING");
        if (!stillUnfinished) {
          clearInterval(taskPollInterval);
          taskPollInterval = null;
          console.log("AI Agent đã hoàn thành xử lý!");
        }
      }, 2500);
    } else if (!hasUnfinishedTasks && taskPollInterval) {
      clearInterval(taskPollInterval);
      taskPollInterval = null;
    }
  }

  // Project & Workspace Management (Delegated to ProjectView module)
  function renderWelcomeProjects() {
    window.ProjectView.renderWelcomeProjects(state, el, openModal, selectProjectWorkspace);
  }

  function populateProjectSelect() {
    window.ProjectView.populateProjectSelect(state, el);
  }

  async function handleDeleteProject(projId, projName) {
    return window.ProjectView.handleDeleteProject(projId, projName, state, el, window.apiClient, {
      startNewChatConversationFn: startNewChatConversation,
      renderSidebarFn: renderSidebar,
      renderWelcomeProjectsFn: renderWelcomeProjects
    });
  }

  function renderSidebar() {
    window.ProjectView.renderSidebar(state, el, {
      selectProjectWorkspaceFn: selectProjectWorkspace,
      startNewChatConversationFn: startNewChatConversation,
      selectChatConversationFn: selectChatConversation,
      deleteChatConversationFn: deleteChatConversationById,
      handleDeleteProjectFn: handleDeleteProject,
      openModalFn: openModal,
      openLoginModalFn: openLoginModal
    });
  }

  function selectProjectWorkspace(project) {
    window.ProjectView.selectProjectWorkspace(project, state, el, {
      selectChatConversationFn: selectChatConversation,
      startNewChatConversationFn: startNewChatConversation,
      renderSidebarFn: renderSidebar,
      renderViewFn: renderView
    });
  }

  // Render Tasks on Welcome View
  function renderWelcomeTasks() {
    const container = document.getElementById("recentTasksGrid");
    const countBadge = document.getElementById("welcomeTasksCount");
    if (!container) return;

    if (countBadge) {
      countBadge.textContent = state.tasks.length;
    }

    if (!state.currentUser) {
      container.innerHTML = `
        <div class="empty-tasks-card">
          <p style="font-weight: 600; margin-bottom: 6px;">Vui lòng đăng nhập để xem danh sách tác vụ AI của bạn từ CSDL MySQL.</p>
          <button class="btn-primary-sm" id="btnWelcomeLogin">Đăng nhập ngay</button>
        </div>
      `;
      const btn = document.getElementById("btnWelcomeLogin");
      if (btn) btn.addEventListener("click", () => openLoginModal(false));
      return;
    }

    if (state.tasks.length === 0) {
      container.innerHTML = `
        <div class="empty-tasks-card">
          <p style="font-weight: 600; margin-bottom: 4px;">Chưa có tác vụ AI nào thuộc tài khoản <strong>${state.currentUser.username}</strong> trong CSDL.</p>
          <p style="font-size: 12px; color: var(--text-muted); margin-bottom: 12px;">
            Mở dự án hoặc tạo phiên chat mới để bắt đầu làm việc cùng Antigravity AI Assistant.
          </p>
          <div style="display: flex; gap: 8px; justify-content: center;">
            <button class="btn-primary-sm" id="btnWelcomeNewChat">+ Tạo phiên Chat mới</button>
          </div>
        </div>
      `;
      const btnNew = document.getElementById("btnWelcomeNewChat");
      if (btnNew) btnNew.addEventListener("click", () => startNewChatConversation());
      return;
    }

    container.innerHTML = state.tasks.map(t => {
      const proj = state.projects.find(p => p.id === t.projectId) || { name: t.projectName || "Dự án", path: "Local" };
      const timeAgo = formatRelativeTime(t.createdAt);
      const isCompleted = t.status === "COMPLETED";
      const isProcessing = t.status === "PROCESSING" || t.status === "PENDING";

      let findingsCount = 0;
      if (t.output) {
        try {
          const parsed = typeof t.output === "string" ? JSON.parse(t.output) : t.output;
          const list = parsed.issues || parsed.findings || [];
          findingsCount = Array.isArray(list) ? list.length : 0;
        } catch (_) {}
      }

      return `
        <div class="welcome-task-card" data-welcome-task-id="${t.id}">
          <div class="welcome-task-top">
            <div class="welcome-task-id-badge">
              <span class="task-hash">Task #${t.id}</span>
              <span class="task-type-tag">${t.taskType || t.type || 'TASK'}</span>
            </div>
            <div style="display: flex; align-items: center; gap: 8px;">
              <button class="btn-pin-task-welcome ${t.isPinned ? 'pinned' : ''}" data-pin-task="${t.id}" title="${t.isPinned ? 'Bỏ ghim tác vụ' : 'Ghim tác vụ lên thanh bên'}">
                ${t.isPinned ? 'Bỏ ghim' : 'Ghim'}
              </button>
              <span class="status-indicator-pill ${t.status ? t.status.toLowerCase() : 'completed'}">
                ${isProcessing ? '<span class="spin-dot"></span>' : ''}
                ${t.status || 'COMPLETED'}
              </span>
            </div>
          </div>

          <div class="welcome-task-project-name" title="${proj.name}">
            ${proj.name}
          </div>

          <div class="welcome-task-meta">
            <span>${isCompleted ? `${findingsCount} vấn đề phát hiện` : (isProcessing ? 'AI đang phân tích...' : 'Chờ xử lý')}</span>
            <span>•</span>
            <span>${timeAgo}</span>
          </div>

          <button class="btn-view-task-details">
            Xem kết quả chi tiết →
          </button>
        </div>
      `;
    }).join("");

    container.querySelectorAll("[data-welcome-task-id]").forEach(card => {
      card.addEventListener("click", (e) => {
        if (e.target.closest("[data-pin-task]")) return;
        const taskId = Number(card.getAttribute("data-welcome-task-id"));
        const task = state.tasks.find(t => t.id === taskId);
        if (task) {
          const project = state.projects.find(p => p.id === task.projectId) || { name: task.projectName || "Dự án", path: "Local", language: "Java" };
          selectTask(task, project);
        }
      });
    });

    // Pin button in welcome cards
    container.querySelectorAll("[data-pin-task]").forEach(btn => {
      btn.addEventListener("click", async (e) => {
        e.stopPropagation();
        const taskId = Number(btn.getAttribute("data-pin-task"));
        const updated = await window.apiClient.togglePin(taskId);
        if (updated) {
          const idx = state.tasks.findIndex(t => t.id === taskId);
          if (idx >= 0) state.tasks[idx].isPinned = updated.isPinned;
          state.pinnedTasks = state.tasks.filter(t => t.isPinned === true);
          renderSidebar();
          renderWelcomeTasks();
        }
      });
    });
  }

  // Check Backend and MySQL Status
  async function checkConnection() {
    if (el.dbStatusText) el.dbStatusText.textContent = "Kiểm tra CSDL...";
    try {
      const isOnline = await window.apiClient.checkHealth();
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

  // Helper: Perform quick demo login
  async function performQuickLogin(username = "admin", password = "admin") {
    try {
      const res = await window.apiClient.login(username, password);
      if (res && res.token) {
        state.currentUser = res.user || { username, role: "ADMIN" };
        closeModals();
        updateUserDisplays();
        await loadDatabaseData();
      }
    } catch (e) {
      alert("Đăng nhập thất bại: " + (e.message || "Vui lòng kiểm tra lại kết nối đến máy chủ"));
    }
  }

  // Backward compatibility alias for selectTask
  function selectTask(task, project) {
    const proj = project || (task && task.projectId ? state.projects.find(p => p.id === task.projectId) : null) || state.activeProject;
    if (proj) {
      selectProjectWorkspace(proj);
    } else {
      startNewChatConversation();
    }
  }

  // Event Listeners Setup
  function initEventListeners() {
    // Antigravity Quick Commands (/review, /fix, /explain, /test)
    document.querySelectorAll("[data-cmd]").forEach(btn => {
      btn.addEventListener("click", () => {
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
      el.btnSwitchWorkspace.addEventListener("click", () => {
        state.view = "welcome";
        renderView();
      });
    }
    if (el.btnChatBackHome) {
      el.btnChatBackHome.addEventListener("click", () => {
        state.view = "welcome";
        renderView();
      });
    }
    if (el.btnSelectWorkspace) {
      el.btnSelectWorkspace.addEventListener("click", () => {
        state.view = "welcome";
        renderView();
      });
    }

    // Theme toggle
    if (el.btnThemeToggle) {
      el.btnThemeToggle.addEventListener("click", () => {
        applyTheme(state.theme === "dark" ? "light" : "dark");
      });
    }

    // Sidebar toggle
    if (el.btnToggleSidebar) {
      el.btnToggleSidebar.addEventListener("click", () => {
        if (el.appSidebar) el.appSidebar.classList.toggle("collapsed");
      });
    }

    // Brand link / Back to Home
    if (el.brandLink) {
      el.brandLink.addEventListener("click", () => {
        if (!state.currentUser) {
          state.view = "landing";
          renderView();
          return;
        }
        state.view = "welcome";
        renderView();
        if (el.navActiveProjectText) el.navActiveProjectText.textContent = "soopi";
        document.querySelectorAll(".task-item-card").forEach(i => i.classList.remove("active"));
      });
    }

    if (el.btnBackToHome) {
      el.btnBackToHome.addEventListener("click", () => {
        if (el.brandLink) el.brandLink.click();
      });
    }

    // Tab buttons in workspace (if present)
    if (el.viewTabs) {
      el.viewTabs.forEach(btn => {
        btn.addEventListener("click", () => {
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

    setupPasswordToggle(el.btnToggleLoginPassword, document.getElementById("loginPassword"));
    setupPasswordToggle(el.btnToggleRegPassword, document.getElementById("regPassword"));

    // Modal Triggers
    const requireAuthOrOpen = (modal) => {
      if (!state.currentUser) {
        openLoginModal(false);
      } else {
        openModal(modal);
      }
    };

    if (el.btnTopOpenProject) el.btnTopOpenProject.addEventListener("click", () => requireAuthOrOpen(el.modalOpenProject));
    if (el.btnQuickAddProject) el.btnQuickAddProject.addEventListener("click", () => requireAuthOrOpen(el.modalOpenProject));
    if (el.cardActionOpenProject) el.cardActionOpenProject.addEventListener("click", () => requireAuthOrOpen(el.modalOpenProject));

    if (el.btnTopNewTask) {
      el.btnTopNewTask.addEventListener("click", () => {
        if (!state.currentUser) {
          openLoginModal(false);
          return;
        }
        populateProjectSelect();
        openModal(el.modalCreateTask);
      });
    }

    if (el.btnWorkspaceRunReview) {
      el.btnWorkspaceRunReview.addEventListener("click", () => {
        if (!state.currentUser) {
          openLoginModal(false);
          return;
        }
        populateProjectSelect();
        openModal(el.modalCreateTask);
      });
    }

    if (el.cardActionNewReview) {
      el.cardActionNewReview.addEventListener("click", () => {
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
      el.btnTopLogin.addEventListener("click", () => openLoginModal(false));
    }

    // Folder Picker: Electron Native or Browser directory input
    if (el.btnBrowseFolder) {
      el.btnBrowseFolder.addEventListener("click", async () => {
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
      el.folderPickerInput.addEventListener("change", (e) => {
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
      el.btnPinActiveTask.addEventListener("click", async () => {
        if (!state.activeTask) return;
        const updated = await window.apiClient.togglePin(state.activeTask.id);
        if (updated) {
          state.activeTask.isPinned = updated.isPinned;
          const idx = state.tasks.findIndex(t => t.id === state.activeTask.id);
          if (idx >= 0) state.tasks[idx].isPinned = updated.isPinned;
          state.pinnedTasks = state.tasks.filter(t => t.isPinned === true);
          if (el.pinActiveTaskIcon) el.pinActiveTaskIcon.style.opacity = updated.isPinned ? "1" : "0.6";
          if (el.pinActiveTaskLabel) el.pinActiveTaskLabel.textContent = updated.isPinned ? "Đã ghim" : "Ghim tác vụ";
          renderSidebar();
          renderWelcomeTasks();
        }
      });
    }

    // User Profile & Account Manager Modal Triggers
    if (el.userMenuDropdown) el.userMenuDropdown.addEventListener("click", openAccountOrAuthModal);
    if (el.userCardBtn) el.userCardBtn.addEventListener("click", openAccountOrAuthModal);

    // Account Manager Modal Actions
    const btnAccChangePassword = document.getElementById("btnAccChangePassword");
    if (btnAccChangePassword) {
      btnAccChangePassword.addEventListener("click", () => {
        closeModals();
        openModal(document.getElementById("modalChangePassword"));
      });
    }

    const btnAccPreferences = document.getElementById("btnAccPreferences");
    if (btnAccPreferences) {
      btnAccPreferences.addEventListener("click", async () => {
        closeModals();
        openModal(document.getElementById("modalUserPreferences"));
        try {
          const prefs = await window.apiClient.getUserPreferences();
          if (prefs) {
            const pTheme = document.getElementById("prefTheme");
            const pLang = document.getElementById("prefLanguage");
            const pModel = document.getElementById("prefModel");
            if (pTheme && prefs.theme) pTheme.value = prefs.theme;
            if (pLang && prefs.language) pLang.value = prefs.language;
            if (pModel && prefs.preferredModel) pModel.value = prefs.preferredModel;
          }
        } catch (e) {
          console.warn("Could not load user preferences", e);
        }
      });
    }

    // Submit: Change Password
    const formChangePassword = document.getElementById("formChangePassword");
    if (formChangePassword) {
      formChangePassword.addEventListener("submit", async (e) => {
        e.preventDefault();
        const oldPass = document.getElementById("changePassOld").value;
        const newPass = document.getElementById("changePassNew").value;
        const confirmPass = document.getElementById("changePassConfirm").value;

        if (newPass !== confirmPass) {
          alert("Xác nhận mật khẩu mới không khớp!");
          return;
        }

        try {
          await window.apiClient.changePassword(oldPass, newPass);
          alert("Đổi mật khẩu thành công!");
          closeModals();
          document.getElementById("changePassOld").value = "";
          document.getElementById("changePassNew").value = "";
          document.getElementById("changePassConfirm").value = "";
        } catch (err) {
          alert("Lỗi khi đổi mật khẩu: " + err.message);
        }
      });
    }

    // Submit: User Preferences
    const formUserPreferences = document.getElementById("formUserPreferences");
    if (formUserPreferences) {
      formUserPreferences.addEventListener("submit", async (e) => {
        e.preventDefault();
        const theme = document.getElementById("prefTheme").value;
        const language = document.getElementById("prefLanguage").value;
        const preferredModel = document.getElementById("prefModel").value;

        try {
          await window.apiClient.updateUserPreferences({ theme, language, preferredModel });
          applyTheme(theme);
          alert("Đã lưu tùy chọn cá nhân thành công!");
          closeModals();
        } catch (err) {
          alert("Lỗi lưu tùy chọn: " + err.message);
        }
      });
    }

    // Mode Switcher Pills (Chatbot vs Code Review)
    if (el.btnModeChat) {
      el.btnModeChat.addEventListener("click", () => {
        state.view = "chat";
        renderView();
      });
    }

    if (el.btnModeReview) {
      el.btnModeReview.addEventListener("click", () => {
        state.view = "welcome";
        renderView();
      });
    }

    // Sidebar & Header New Chat buttons
    if (el.btnSidebarNewChat) {
      el.btnSidebarNewChat.addEventListener("click", startNewChatConversation);
    }

    if (el.btnHeaderNewChat) {
      el.btnHeaderNewChat.addEventListener("click", startNewChatConversation);
    }

    // Header Delete Conversation button
    if (el.btnDeleteActiveChat) {
      el.btnDeleteActiveChat.addEventListener("click", () => {
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
      el.btnDeleteActiveProject.addEventListener("click", () => {
        if (state.activeProject) {
          handleDeleteProject(state.activeProject.id, state.activeProject.name);
        } else {
          alert("Chưa chọn dự án nào để xóa.");
        }
      });
    }

    // Chat Model Selector change
    if (el.selectChatModel) {
      el.selectChatModel.addEventListener("change", async () => {
        const newModel = el.selectChatModel.value;
        if (state.activeConversationId) {
          try {
            await window.apiClient.updateChatConversationModel(state.activeConversationId, newModel);
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
      el.btnSendCentralChat.addEventListener("click", () => handleSendCentralChatMessage());
    }

    if (el.centralChatTextarea) {
      el.centralChatTextarea.addEventListener("keydown", (e) => {
        if (e.key === "Enter" && !e.shiftKey) {
          e.preventDefault();
          handleSendCentralChatMessage();
        }
      });
      el.centralChatTextarea.addEventListener("input", () => {
        el.centralChatTextarea.style.height = "auto";
        el.centralChatTextarea.style.height = Math.min(el.centralChatTextarea.scrollHeight, 120) + "px";
      });
    }

    // Chat Project Context Selector
    if (el.selectChatProject) {
      el.selectChatProject.addEventListener("change", () => {
        updateChatProjectHint();
      });
    }

    // Suggestion Chips
    document.querySelectorAll(".suggestion-chip[data-prompt]").forEach(chip => {
      chip.addEventListener("click", () => {
        const prompt = chip.getAttribute("data-prompt");
        if (prompt) {
          handleSendCentralChatMessage(prompt);
        }
      });
    });

    // Legacy Workspace Chat Tab Controls
    const btnNewChat = document.getElementById("btnNewChatConversation");
    if (btnNewChat) {
      btnNewChat.addEventListener("click", startNewChatConversation);
    }

    const btnSendChat = document.getElementById("btnSendChatMessage");
    if (btnSendChat) {
      btnSendChat.addEventListener("click", () => {
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
      inputChat.addEventListener("keydown", (e) => {
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

    if (el.btnAccSwitchAccount) {
      el.btnAccSwitchAccount.addEventListener("click", () => {
        closeModals();
        openLoginModal(false);
      });
    }

    if (el.btnAccAdminPanel) {
      el.btnAccAdminPanel.addEventListener("click", () => {
        closeModals();
        openAdminPanelModal();
      });
    }

    if (el.btnAccLogout) {
      el.btnAccLogout.addEventListener("click", performLogout);
    }

    // Top Admin Button Action
    if (el.btnTopAdmin) {
      el.btnTopAdmin.addEventListener("click", openAdminPanelModal);
    }

    // Admin Panel Refresh Data Button & Tabs
    setupAdminTabs();
    if (el.btnRefreshAdminData) {
      el.btnRefreshAdminData.addEventListener("click", loadAdminDashboardData);
    }

    // Admin Command Center Quick Open Button on Welcome Screen
    if (el.btnAdminQuickOpenPanel) {
      el.btnAdminQuickOpenPanel.addEventListener("click", openAdminPanelModal);
    }

    // Inspect Modal View Tabs Switcher
    if (el.tabInspectTasks) {
      el.tabInspectTasks.addEventListener("click", () => {
        el.tabInspectTasks.classList.add("active");
        if (el.tabInspectProjects) el.tabInspectProjects.classList.remove("active");
        if (el.inspectTasksContainer) el.inspectTasksContainer.style.display = "flex";
        if (el.inspectProjectsContainer) el.inspectProjectsContainer.style.display = "none";
      });
    }

    if (el.tabInspectProjects) {
      el.tabInspectProjects.addEventListener("click", () => {
        el.tabInspectProjects.classList.add("active");
        if (el.tabInspectTasks) el.tabInspectTasks.classList.remove("active");
        if (el.inspectTasksContainer) el.inspectTasksContainer.style.display = "none";
        if (el.inspectProjectsContainer) el.inspectProjectsContainer.style.display = "flex";
      });
    }

    // Database Diagnostics Modal Triggers & Refresh
    if (el.dbStatusPill) {
      el.dbStatusPill.addEventListener("click", openDbDiagnosticsModal);
    }

    if (el.btnRefreshDbDiag) {
      el.btnRefreshDbDiag.addEventListener("click", runDbDiagnosticsPing);
    }

    // Landing View CTA buttons
    if (el.btnLandingLogin) {
      el.btnLandingLogin.addEventListener("click", () => openLoginModal(false));
    }

    if (el.btnLandingRegister) {
      el.btnLandingRegister.addEventListener("click", () => openLoginModal(true));
    }

    const btnLandingQuick = document.getElementById("btnLandingQuickLogin");
    if (btnLandingQuick) {
      btnLandingQuick.addEventListener("click", () => performQuickLogin("admin", "admin"));
    }

    const btnQuickAdmin = document.getElementById("btnQuickLoginAdmin");
    if (btnQuickAdmin) {
      btnQuickAdmin.addEventListener("click", () => performQuickLogin("admin", "admin"));
    }

    // Sidebar LOGOUT Button
    if (el.btnLogoutBtn) {
      el.btnLogoutBtn.addEventListener("click", (e) => {
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
      b.addEventListener("click", () => {
        closeModals();
      });
    });

    // Path suggestions pills in Open Project modal
    document.querySelectorAll(".path-pill-btn[data-fill]").forEach(pill => {
      pill.addEventListener("click", () => {
        const inp = document.getElementById("inputProjectPath");
        if (inp) inp.value = pill.getAttribute("data-fill");
      });
    });

    // Submit: Open Project (Lưu vào CSDL)
    if (el.formOpenProject) {
      el.formOpenProject.addEventListener("submit", async (e) => {
        e.preventDefault();
        const path = document.getElementById("inputProjectPath").value.trim();
        const name = document.getElementById("inputProjectName").value.trim();
        const language = document.getElementById("inputProjectLang") ? document.getElementById("inputProjectLang").value.trim() : "Java";
        const description = document.getElementById("inputProjectDesc") ? document.getElementById("inputProjectDesc").value.trim() : "";

        if (state.projects.some(p => p.name.toLowerCase() === name.toLowerCase())) {
          alert(`Dự án với tên "${name}" đã tồn tại trong tài khoản của bạn. Vui lòng chọn tên khác để tránh nhầm lẫn!`);
          return;
        }

        try {
          const newProj = await window.apiClient.createProject({ name, path, language, description });
          closeModals();
          await loadDatabaseData();
          selectProjectWorkspace(newProj);
        } catch (err) {
          alert("Lỗi khi tạo dự án vào CSDL: " + err.message);
        }
      });
    }

    // Submit: Create AI Review Task (Lưu vào CSDL & chạy ngầm)
    if (el.formCreateTask) {
      el.formCreateTask.addEventListener("submit", async (e) => {
        e.preventDefault();
        const selTaskProj = document.getElementById("selectTaskProject");
        const projectId = selTaskProj ? Number(selTaskProj.value) : null;
        if (!projectId) {
          alert("Vui lòng chọn dự án trước!");
          return;
        }
        const type = document.getElementById("selectTaskType") ? document.getElementById("selectTaskType").value : "CODE_REVIEW";
        const focus = document.getElementById("selectTaskFocus") ? document.getElementById("selectTaskFocus").value : "";
        const model = document.getElementById("selectTaskModel") ? document.getElementById("selectTaskModel").value : "";
        const prompt = document.getElementById("inputTaskPrompt") ? document.getElementById("inputTaskPrompt").value.trim() : "";
        const selectedTools = Array.from(document.querySelectorAll('input[name="taskTools"]:checked')).map(cb => cb.value);

        const project = state.projects.find(p => p.id === projectId);
        closeModals();

        try {
          const newTask = await window.apiClient.createTask({
            projectId: projectId,
            projectName: project ? project.name : "Dự án",
            type: type,
            input: { projectPath: project ? project.path : "D:/AI Agent/aiagent/aiagent", focus: focus },
            selectedTools: selectedTools,
            model: model || undefined,
            prompt: prompt || undefined
          });

          await loadDatabaseData();
          if (project) {
            selectTask(newTask, project);
          }
        } catch (err) {
          alert("Lỗi khi tạo tác vụ AI: " + err.message);
        }
      });
    }

    // Auth Tabs (Login vs Register)
    if (el.tabAuthLogin) {
      el.tabAuthLogin.addEventListener("click", () => {
        el.tabAuthLogin.classList.add("active");
        if (el.tabAuthRegister) el.tabAuthRegister.classList.remove("active");
        if (el.formLogin) el.formLogin.style.display = "block";
        if (el.formRegister) el.formRegister.style.display = "none";
      });
    }

    if (el.tabAuthRegister) {
      el.tabAuthRegister.addEventListener("click", () => {
        el.tabAuthRegister.classList.add("active");
        if (el.tabAuthLogin) el.tabAuthLogin.classList.remove("active");
        if (el.formRegister) el.formRegister.style.display = "block";
        if (el.formLogin) el.formLogin.style.display = "none";
      });
    }

    // Submit: Login Form
    if (el.formLogin) {
      el.formLogin.addEventListener("submit", async (e) => {
        e.preventDefault();
        const u = document.getElementById("loginUsername").value.trim();
        const p = document.getElementById("loginPassword").value;
        const submitBtn = el.formLogin.querySelector("button[type='submit']");
        const originalText = submitBtn ? submitBtn.textContent : "";

        try {
          if (submitBtn) {
            submitBtn.disabled = true;
            submitBtn.textContent = "Đang kiểm tra...";
          }
          await window.apiClient.login(u, p);
          closeModals();
          clearChatSessionState();
          state.currentUser = window.apiClient.getCurrentUser();
          state.view = "welcome";
          updateUserDisplays();
          await checkConnection();
          await loadDatabaseData();
          renderView();
        } catch (err) {
          alert("Đăng nhập thất bại: " + (err.message || "Tài khoản hoặc mật khẩu không chính xác"));
        } finally {
          if (submitBtn) {
            submitBtn.disabled = false;
            submitBtn.textContent = originalText;
          }
        }
      });
    }

    // Submit: Register Form
    if (el.formRegister) {
      el.formRegister.addEventListener("submit", async (e) => {
        e.preventDefault();
        const u = document.getElementById("regUsername").value.trim();
        const email = document.getElementById("regEmail").value.trim();
        const p = document.getElementById("regPassword").value;
        const submitBtn = el.formRegister.querySelector("button[type='submit']");
        const originalText = submitBtn ? submitBtn.textContent : "";

        try {
          if (submitBtn) {
            submitBtn.disabled = true;
            submitBtn.textContent = "Đang đăng ký...";
          }
          await window.apiClient.register(u, email, p);
          closeModals();
          clearChatSessionState();
          state.currentUser = window.apiClient.getCurrentUser();
          state.view = "welcome";
          updateUserDisplays();
          await checkConnection();
          await loadDatabaseData();
          renderView();
        } catch (err) {
          alert("Đăng ký thất bại: " + (err.message || "Kiểm tra lại thông tin đăng ký"));
        } finally {
          if (submitBtn) {
            submitBtn.disabled = false;
            submitBtn.textContent = originalText;
          }
        }
      });
    }

    // Keyboard Shortcuts (Enter on Welcome -> Open Project; Ctrl+K -> Search)
    document.addEventListener("keydown", (e) => {
      if (e.key === "Enter" && state.view === "welcome" && !document.querySelector(".modal-backdrop.active")) {
        requireAuthOrOpen(el.modalOpenProject);
      }
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        requireAuthOrOpen(el.modalCreateTask);
      }
    });

    // Refresh Database Tasks button
    const btnRefresh = document.getElementById("btnRefreshTasks");
    if (btnRefresh) {
      btnRefresh.addEventListener("click", async () => {
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
    window.addEventListener("agent-task-updated", (e) => {
      loadDatabaseData();
    });
  }

  // App Initialization
  async function init() {
    applyTheme(state.theme);
    initEventListeners();
    state.view = state.currentUser ? "welcome" : "landing";
    renderView();
    await checkConnection();
    await loadDatabaseData();
    renderView();
  }

  document.addEventListener("DOMContentLoaded", init);
})();
