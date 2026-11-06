/** Authentication form lifecycle: explicit submissions, inline errors, no automatic replay. */
(function () {
  let mounted = false;
  function mount({el, state, closeModals, clearChatSessionState, updateUserDisplays, checkConnection, loadDatabaseData, renderView}) {
    if (mounted) return;
    mounted = true;
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
          window.WorkspaceView.formError(el.formLogin, "Đăng nhập thất bại: " + (err.message || "Tài khoản hoặc mật khẩu không chính xác"));
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
          window.WorkspaceView.formError(el.formRegister, "Đăng ký thất bại: " + (err.message || "Kiểm tra lại thông tin đăng ký"));
        } finally {
          if (submitBtn) {
            submitBtn.disabled = false;
            submitBtn.textContent = originalText;
          }
        }
      });
    }

  }
  function create({state, el, openModal, closeModals, updateUserDisplays, renderSidebar, renderView, loadDatabaseData, checkConnection, invalidate}) {
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
    el.formLogin?.querySelector(".form-error")?.remove();
    el.formRegister?.querySelector(".form-error")?.remove();
    resetPasswordEyeIcons();

    if (isRegister) {
      if (el.tabAuthRegister) el.tabAuthRegister.click();
    } else {
      if (el.tabAuthLogin) el.tabAuthLogin.click();
    }
    openModal(el.modalAuth);
  }

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
        el.accProfileEmail.textContent = state.currentUser.email || "Chưa có email";
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

  function clearChatSessionState() {
    invalidate();
    window.ChatView?.resetReviews?.(state);
    state.isChatSending = false;
    if (el.btnSendCentralChat) el.btnSendCentralChat.disabled = false;
    if (el.chatTypingIndicator) el.chatTypingIndicator.style.display = 'none';
    state.activeConversationId = null;
    state.activeConversation = null;
    state.activeTask = null;
    document.querySelectorAll('input[name="taskSkills"]').forEach(input => input.checked = false);
    const extraPrompt = document.getElementById("inputTaskPrompt");
    if (extraPrompt) extraPrompt.value = "";
    const reportContent = document.getElementById("taskReportContent");
    if (reportContent) reportContent.innerHTML = "";
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
    if (el.emptyStateTitle) el.emptyStateTitle.textContent = "HUS AI Agent";
    if (el.emptyStateDesc) el.emptyStateDesc.textContent = "Không gian làm việc trực tiếp trên dự án. Chọn hoặc mở dự án để AI đọc mã nguồn và thực thi các tính năng chuyên sâu.";
  }

  function performLogout() {
    const confirmed = confirm("Bạn có chắc chắn muốn đăng xuất khỏi tài khoản HUS AI Agent không?");
    if (!confirmed) return;

    window.apiClient.logout();
    state.currentUser = null;
    state.projects = [];
    state.tasks = [];

    clearChatSessionState();
    state.view = "landing";
    closeModals();
    updateUserDisplays();
    renderSidebar();
  }

  function setupPasswordToggle(btn, input, signal) {
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
    }, {signal});
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

  async function openAdminLogin(event) {
    const button = event && event.currentTarget;
    if (button) button.disabled = true;
    try {
      await window.apiClient.loginLocalAdmin();
      closeModals();
      clearChatSessionState();
      state.currentUser = window.apiClient.getCurrentUser();
      state.view = "welcome";
      updateUserDisplays();
      await checkConnection();
      await loadDatabaseData();
      renderView();
    } catch (error) {
      window.WorkspaceView.notify("Đăng nhập local thất bại: " + error.message, true);
    } finally {
      if (button) button.disabled = false;
    }
  }
    return {openLoginModal, openAccountOrAuthModal, clearChatSessionState, performLogout, setupPasswordToggle, resetPasswordEyeIcons, openAdminLogin};
  }
  window.AuthView = {mount, create};
})();
