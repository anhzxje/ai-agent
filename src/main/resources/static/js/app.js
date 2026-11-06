/** Bootstrap and application coordination. Feature modules own data, events and presentation. */
(function() {
  // App State
  const state = window.AppState.state;
  state.currentUser = window.apiClient.getCurrentUser();

  const shell = window.ShellView.create(state, window.apiClient);
  const {el, applyTheme, renderView, updateUserDisplays, openDbDiagnosticsModal, runDbDiagnosticsPing, checkConnection} = shell;
  window.renderView = renderView;

  const {openLoginModal, openAccountOrAuthModal, clearChatSessionState, performLogout, setupPasswordToggle, resetPasswordEyeIcons, openAdminLogin} = window.AuthView.create({state, el, openModal, closeModals, updateUserDisplays, renderSidebar, renderView, loadDatabaseData, checkConnection, invalidate: () => workspaceSession.invalidate()});
  const {renderWelcomeTasks, selectTask} = window.TaskView.create({state, el, openModal, openLoginModal, startNewChatConversation, renderSidebar});

  // Modal Management
  function openModal(modalEl) {
    if (modalEl) modalEl.classList.add("active");
  }

  function closeModals() {
    document.querySelectorAll(".modal-backdrop").forEach(m => m.classList.remove("active"));
  }

  // Admin Dashboard Modal Handlers (Delegated to AdminView module)
  async function openAdminPanelModal() {
    return window.AdminView.openAdminPanelModal(el, state, window.apiClient, openModal);
  }

  async function loadAdminDashboardData() {
    return window.AdminView.loadAdminDashboardData(el, state, window.apiClient, openModal);
  }

  function setupAdminTabs() {
    return window.AdminView.setupAdminTabs(window.apiClient);
  }

  // ==========================================================================
  // AI CODING ASSISTANT CHATBOT PLATFORM CONTROLLER

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

  async function selectChatConversation(id) {
    return window.ChatView.selectChatConversation(id, state, el, window.apiClient, renderSidebar);
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

  function loadDatabaseData() {
    updateUserDisplays();
    return workspaceSession.refresh();
  }

  // Load Available Models for Chat
  async function loadAvailableModels() {
    try {
      await window.ReviewForm.loadModels(window.apiClient, [el.selectChatModel, document.getElementById('selectTaskModel'), document.getElementById('prefModel')]);
      await window.ReviewForm.loadTools(window.apiClient, document.getElementById('taskToolsContainer'));
      await window.ReviewForm.loadTools(window.apiClient, document.getElementById('chatReviewTools'));
    } catch (error) {
      window.WorkspaceView.notify('Không tải được cấu hình model/tools từ server.', true);
    }
  }

  const workspaceSession = window.WorkspaceSession.create({
    api: window.apiClient, state, isVisible: () => !document.hidden,
    onStatus: status => { window.WorkspaceView.setDataState(status); window.WorkspaceView.refresh(); },
    onReset: () => { clearChatSessionState(); updateUserDisplays(); renderWorkspaceData(); },
    onCommit: renderWorkspaceData,
    onTasks: () => {
      renderSidebar(); renderWelcomeTasks();
      if (state.activeTask) {
        const content = document.getElementById('taskReportContent');
        if (content) content.innerHTML = window.SkillView.renderTaskReport(state.activeTask);
      }
      window.WorkspaceView.refresh();
    },
    afterLoad: async isCurrent => {
      await loadChatConversations(false);
      if (!isCurrent()) return;
      await loadAvailableModels();
      if (!isCurrent()) return;
      await window.SkillView.loadSkills(window.apiClient, document.getElementById('taskSkillsContainer'));
      await window.SkillView.loadSkills(window.apiClient, document.getElementById('chatReviewSkills'));
    },
    onError: error => window.WorkspaceView.setDataState('error', 'Không tải được dữ liệu: ' + error.message)
  });

  function renderWorkspaceData() {
    renderSidebar(); renderWelcomeTasks(); renderWelcomeProjects();
    populateProjectSelect(); populateChatProjectDropdown();
  }

  // Project & Workspace Management (Delegated to ProjectView module)
  function renderWelcomeProjects() {
    window.ProjectView.renderWelcomeProjects(state, el, openModal, selectProjectWorkspace);
    window.WorkspaceView.refresh();
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
    window.WorkspaceView.enhanceControls();
  }

  function selectProjectWorkspace(project) {
    window.ProjectView.selectProjectWorkspace(project, state, el, {
      selectChatConversationFn: selectChatConversation,
      startNewChatConversationFn: startNewChatConversation,
      renderSidebarFn: renderSidebar,
      renderViewFn: renderView
    });
  }

  // App Initialization
  async function init() {
    window.WorkspaceView.mount({ state, renderView, applyTheme, api: window.apiClient, reload: loadDatabaseData,
      openTask: task => selectTask(task, state.projects.find(p => p.id === task.projectId)),
      openReview: projectId => {
        populateProjectSelect();
        el.selectTaskProject.value = String(projectId);
        openModal(el.modalCreateTask);
      }
    });
    window.WorkspaceDialogs.init();
    document.addEventListener('visibilitychange', () => { if (document.hidden) workspaceSession.stop(); else workspaceSession.start(); });
    window.addEventListener('pagehide', () => workspaceSession.stop());
    applyTheme(state.theme);
    shell.mount({state, openModal, closeModals, openLoginModal, openAccountOrAuthModal, performLogout, openDbDiagnosticsModal, runDbDiagnosticsPing, openAdminPanelModal, loadAdminDashboardData, setupAdminTabs, setupPasswordToggle, resetPasswordEyeIcons, loadDatabaseData, checkConnection, clearChatSessionState, selectProjectWorkspace, startNewChatConversation, renderSidebar, renderWelcomeTasks, selectTask, openAdminLogin, loadChatConversations, populateProjectSelect, deleteChatConversationById, handleDeleteProject, handleSendCentralChatMessage, updateChatProjectHint});
    state.view = state.currentUser ? "welcome" : "landing";
    renderView();
    await checkConnection();
    await loadDatabaseData();
    renderView();
  }

  document.addEventListener("DOMContentLoaded", init);
})();
