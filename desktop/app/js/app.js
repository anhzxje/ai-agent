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
    view: "welcome", // "welcome" | "workspace"
    projects: [],
    tasks: [],
    pinnedTasks: [],
    activeProject: null,
    activeTask: null,
    activeTab: "findings",
    currentUser: window.apiClient.getCurrentUser(),
    theme: localStorage.getItem("ai_agent_theme") || "light"
  };

  // DOM Elements
  const el = {
    appSidebar: document.getElementById("appSidebar"),
    landingView: document.getElementById("landingView"),
    welcomeView: document.getElementById("welcomeView"),
    workspaceView: document.getElementById("workspaceView"),
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

  // Helper: Format real relative time from database ISO timestamp
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

  // Theme Management
  function applyTheme(theme) {
    state.theme = theme;
    document.documentElement.setAttribute("data-theme", theme);
    localStorage.setItem("ai_agent_theme", theme);
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

  // Helper: Perform safe logout
  function performLogout() {
    const confirmed = confirm("Bạn có chắc chắn muốn đăng xuất khỏi tài khoản Soopi không?");
    if (!confirmed) return;

    window.apiClient.logout();
    state.currentUser = null;
    state.projects = [];
    state.tasks = [];
    state.pinnedTasks = [];
    state.activeProject = null;
    state.activeTask = null;
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
      if (el.diagBackendUrl) el.diagBackendUrl.textContent = pingResult.url || "http://localhost:8081";
      if (el.diagDbHost) el.diagDbHost.textContent = "localhost:3306";
      if (el.diagDbName) el.diagDbName.textContent = "ai_agent (MySQL 8.0+)";
    } else {
      if (el.diagStatusDot) el.diagStatusDot.className = "db-diag-dot offline";
      if (el.diagStatusTitle) el.diagStatusTitle.textContent = "Chưa kết nối CSDL MySQL hoặc Backend";
      if (el.diagStatusSub) el.diagStatusSub.textContent = pingResult.error || "Không thể gửi yêu cầu đến Spring Boot backend";
      if (el.diagLatencyVal) el.diagLatencyVal.textContent = "Mất kết nối";
    }
  }

  // Admin Dashboard Modal Handlers (Role ADMIN: Stats & User Management)
  async function openAdminPanelModal() {
    if (!state.currentUser || state.currentUser.role !== 'ADMIN') {
      alert("Bạn không có quyền quản trị viên (ADMIN) để truy cập tính năng này.");
      return;
    }
    openModal(el.modalAdminPanel);
    await loadAdminDashboardData();
  }

  async function loadAdminDashboardData() {
    if (el.adminUsersTableBody) {
      el.adminUsersTableBody.innerHTML = `<tr><td colspan="7" style="text-align: center; color: var(--text-muted); padding: 16px;">Đang tải danh sách tài khoản từ CSDL...</td></tr>`;
    }

    try {
      const [stats, users] = await Promise.all([
        window.apiClient.getAdminStats(),
        window.apiClient.getAdminUsers()
      ]);

      if (stats) {
        if (el.adminStatUsers) el.adminStatUsers.textContent = stats.totalUsers ?? 0;
        if (el.adminStatRoles) el.adminStatRoles.textContent = `${stats.totalAdmins ?? 1} / ${stats.totalRegularUsers ?? 0}`;
        if (el.adminStatProjects) el.adminStatProjects.textContent = stats.totalProjects ?? 0;
        if (el.adminStatTasks) el.adminStatTasks.textContent = stats.totalTasks ?? 0;
        if (el.adminStatCompleted) el.adminStatCompleted.textContent = stats.completedTasks ?? 0;
        if (el.adminStatPending) el.adminStatPending.textContent = stats.pendingOrProcessingTasks ?? 0;
        if (el.adminStatFailed) el.adminStatFailed.textContent = stats.failedTasks ?? 0;
        if (el.adminStatTypes) el.adminStatTypes.textContent = `${stats.reviewTasksCount ?? 0} Rev / ${stats.fixTasksCount ?? 0} Fix / ${stats.testGenTasksCount ?? 0} Test`;

        // Update Command Center Badges on Welcome View
        if (el.quickStatUsersBadge) el.quickStatUsersBadge.textContent = `${stats.totalUsers ?? 0} Người dùng (${stats.totalAdmins ?? 1} Admin)`;
        if (el.quickStatProjectsBadge) el.quickStatProjectsBadge.textContent = `${stats.totalProjects ?? 0} Dự án toàn CSDL`;
        if (el.quickStatTasksBadge) el.quickStatTasksBadge.textContent = `${stats.totalTasks ?? 0} Tác vụ AI (${stats.completedTasks ?? 0} hoàn thành)`;
      }

      if (users && users.length > 0) {
        if (el.adminUsersTableBody) {
          el.adminUsersTableBody.innerHTML = users.map(u => {
            const roleClass = u.role === 'ADMIN' ? 'badge-role-admin' : 'badge-role-user';
            const dateStr = u.createdAt ? new Date(u.createdAt).toLocaleString('vi-VN') : 'Mặc định';
            const isSelf = state.currentUser && state.currentUser.username === u.username;
            const lockStatusHtml = u.isLocked 
              ? `<span style="background: rgba(239, 68, 68, 0.15); color: #ef4444; padding: 2px 6px; border-radius: 4px; font-size: 11px; font-weight: 600;">Đã khóa</span>` 
              : `<span style="background: rgba(16, 185, 129, 0.15); color: var(--diff-add); padding: 2px 6px; border-radius: 4px; font-size: 11px; font-weight: 600;">Hoạt động</span>`;

            return `
              <tr>
                <td style="font-family: var(--font-mono); font-weight: 600;">#${u.id}</td>
                <td>
                  <strong>${u.username}</strong>
                  ${isSelf ? '<span style="font-size: 10px; margin-left: 4px; opacity: 0.6;">(Bạn)</span>' : ''}
                </td>
                <td style="color: var(--text-secondary);">${u.email || '-'}</td>
                <td><span class="${roleClass}">${u.role}</span></td>
                <td>${lockStatusHtml}</td>
                <td>
                  <span style="font-family: var(--font-mono); font-size: 11.5px; color: var(--text-secondary);">
                    ${u.projectCount || 0} dự án • ${u.taskCount || 0} tác vụ
                  </span>
                </td>
                <td style="color: var(--text-muted); font-size: 11.5px;">${dateStr}</td>
                <td style="text-align: right; white-space: nowrap;">
                  <button type="button" class="btn-table-action primary" data-action="inspect-user" data-user-id="${u.id}" data-username="${u.username}" data-email="${u.email || ''}" data-role="${u.role}" title="Thanh tra nhật ký chat & tác vụ của tài khoản này">
                    Kiểm duyệt
                  </button>
                  ${isSelf ? '' : `
                    <button type="button" class="btn-table-action ${u.isLocked ? 'primary' : 'danger'}" data-action="toggle-lock" data-user-id="${u.id}" data-username="${u.username}" data-locked="${u.isLocked}">
                      ${u.isLocked ? 'Mở khóa' : 'Khóa'}
                    </button>
                  `}
                  <button type="button" class="btn-table-action" data-action="toggle-role" data-user-id="${u.id}" data-username="${u.username}" data-role="${u.role}" title="Chuyển đổi vai trò USER / ADMIN" ${isSelf ? 'disabled style="opacity: 0.4; cursor: not-allowed;"' : ''}>
                    ${u.role === 'ADMIN' ? 'Hạ USER' : 'Lên ADMIN'}
                  </button>
                  ${isSelf ? '' : `
                    <button type="button" class="btn-table-action danger" data-action="delete-user" data-user-id="${u.id}" data-username="${u.username}" title="Xóa tài khoản này">
                      Xóa
                    </button>
                  `}
                </td>
              </tr>
            `;
          }).join("");

          // Event Listeners for User Table Actions
          el.adminUsersTableBody.querySelectorAll("[data-action='inspect-user']").forEach(btn => {
            btn.addEventListener("click", () => {
              const uId = Number(btn.getAttribute("data-user-id"));
              const username = btn.getAttribute("data-username");
              const email = btn.getAttribute("data-email");
              const role = btn.getAttribute("data-role");
              openInspectUserModal({ id: uId, username, email, role });
            });
          });

          el.adminUsersTableBody.querySelectorAll("[data-action='toggle-lock']").forEach(btn => {
            btn.addEventListener("click", async () => {
              const uId = Number(btn.getAttribute("data-user-id"));
              const username = btn.getAttribute("data-username");
              const isLocked = btn.getAttribute("data-locked") === "true";
              const actionName = isLocked ? "mở khóa" : "khóa";
              if (confirm(`Bạn có chắc muốn ${actionName} tài khoản "${username}" không?`)) {
                try {
                  if (isLocked) {
                    await window.apiClient.unlockUser(uId);
                  } else {
                    await window.apiClient.lockUser(uId);
                  }
                  await loadAdminDashboardData();
                } catch (err) {
                  alert("Lỗi khi thay đổi trạng thái khóa: " + err.message);
                }
              }
            });
          });

          el.adminUsersTableBody.querySelectorAll("[data-action='toggle-role']").forEach(btn => {
            btn.addEventListener("click", async () => {
              const uId = Number(btn.getAttribute("data-user-id"));
              const username = btn.getAttribute("data-username");
              const currentRole = btn.getAttribute("data-role");
              const nextRole = currentRole === 'ADMIN' ? 'USER' : 'ADMIN';

              if (confirm(`Bạn có chắc muốn đổi vai trò của tài khoản "${username}" thành ${nextRole} không?`)) {
                try {
                  await window.apiClient.updateUserRole(uId, nextRole);
                  await loadAdminDashboardData();
                } catch (err) {
                  alert("Lỗi khi cập nhật vai trò: " + err.message);
                }
              }
            });
          });

          el.adminUsersTableBody.querySelectorAll("[data-action='delete-user']").forEach(btn => {
            btn.addEventListener("click", async () => {
              const uId = Number(btn.getAttribute("data-user-id"));
              const username = btn.getAttribute("data-username");

              if (confirm(`CẢNH BÁO QUẢN TRỊ:\nBạn có chắc chắn muốn xóa vĩnh viễn tài khoản "${username}" cùng toàn bộ dự án và tác vụ AI liên quan không?`)) {
                try {
                  await window.apiClient.deleteUser(uId);
                  await loadAdminDashboardData();
                } catch (err) {
                  alert("Lỗi khi xóa tài khoản: " + err.message);
                }
              }
            });
          });
        }
      } else {
        if (el.adminUsersTableBody) {
          el.adminUsersTableBody.innerHTML = `<tr><td colspan="8" style="text-align: center; color: var(--text-muted); padding: 16px;">Không tìm thấy tài khoản người dùng nào.</td></tr>`;
        }
      }
    } catch (e) {
      console.error("Lỗi khi tải dữ liệu quản trị:", e);
      if (el.adminUsersTableBody) {
        el.adminUsersTableBody.innerHTML = `<tr><td colspan="7" style="text-align: center; color: var(--diff-del); padding: 16px;">Lỗi kết nối máy chủ quản trị: ${e.message}</td></tr>`;
      }
    }
  }

  // Open and load User Inspection (Admin Audit) Modal
  async function openInspectUserModal(user) {
    if (el.inspectUserAvatar) el.inspectUserAvatar.textContent = (user.username || '?').charAt(0).toUpperCase();
    if (el.inspectUsername) el.inspectUsername.textContent = user.username || "Thành viên";
    if (el.inspectUserRole) el.inspectUserRole.textContent = user.role || "USER";
    if (el.inspectUserId) el.inspectUserId.textContent = `#${user.id}`;
    if (el.inspectUserEmail) el.inspectUserEmail.textContent = user.email || `${user.username}@test.com`;

    if (el.inspectTasksContainer) {
      el.inspectTasksContainer.innerHTML = `<div style="text-align: center; color: var(--text-muted); padding: 20px;">Đang tải nhật ký tác vụ và lịch sử chat từ CSDL...</div>`;
    }
    if (el.inspectProjectsContainer) {
      el.inspectProjectsContainer.innerHTML = `<div style="text-align: center; color: var(--text-muted); padding: 20px;">Đang tải danh sách dự án...</div>`;
    }

    if (el.tabInspectTasks) el.tabInspectTasks.click();
    openModal(el.modalAdminInspectUser);

    try {
      const [userTasks, userProjects] = await Promise.all([
        window.apiClient.getUserTasksForAdmin(user.id),
        window.apiClient.getUserProjectsForAdmin(user.id)
      ]);

      if (el.inspectTasksCount) el.inspectTasksCount.textContent = userTasks ? userTasks.length : 0;
      if (el.inspectProjectsCount) el.inspectProjectsCount.textContent = userProjects ? userProjects.length : 0;

      // Render tasks
      if (el.inspectTasksContainer) {
        if (!userTasks || userTasks.length === 0) {
          el.inspectTasksContainer.innerHTML = `<div style="text-align: center; color: var(--text-muted); padding: 24px;">Tài khoản này chưa tạo tác vụ AI hoặc phiên trò chuyện nào trong CSDL.</div>`;
        } else {
          el.inspectTasksContainer.innerHTML = userTasks.map(t => {
            const dateStr = t.createdAt ? new Date(t.createdAt).toLocaleString('vi-VN') : 'Vừa xong';
            let summaryText = "";
            if (t.output) {
              try {
                const parsed = typeof t.output === "string" ? JSON.parse(t.output) : t.output;
                const extracted = parsed.summary || parsed.description || parsed.message || (typeof parsed === 'string' ? parsed : JSON.stringify(parsed));
                summaryText = typeof extracted === 'string' ? extracted : JSON.stringify(extracted);
              } catch (_) {
                summaryText = typeof t.output === 'string' ? t.output : JSON.stringify(t.output);
              }
            } else if (t.errorMessage) {
              summaryText = `Lỗi: ${t.errorMessage}`;
            } else {
              summaryText = `(Trạng thái: ${t.status || 'Chưa có kết quả'})`;
            }

            if (typeof summaryText !== 'string') {
              summaryText = String(summaryText || '');
            }

            return `
              <div class="inspect-task-card">
                <div class="inspect-task-head">
                  <span class="task-type-pill" style="font-size: 10px;">${t.type || 'TASK'}</span>
                  <span class="severity-tag ${t.status === 'COMPLETED' ? 'LOW' : 'HIGH'}">${t.status}</span>
                </div>
                <div class="inspect-task-title">Tác vụ #${t.id}: ${t.projectName || 'Dự án'}</div>
                <div class="inspect-task-meta">Khởi tạo: ${dateStr} • Người sở hữu: ${user.username}</div>
                <div class="inspect-task-body">${summaryText.substring(0, 300)}${summaryText.length > 300 ? '...' : ''}</div>
              </div>
            `;
          }).join("");
        }
      }

      // Render projects
      if (el.inspectProjectsContainer) {
        if (!userProjects || userProjects.length === 0) {
          el.inspectProjectsContainer.innerHTML = `<div style="text-align: center; color: var(--text-muted); padding: 24px;">Tài khoản này chưa mở dự án nào trong CSDL.</div>`;
        } else {
          el.inspectProjectsContainer.innerHTML = userProjects.map(p => `
            <div class="inspect-task-card">
              <div class="inspect-task-head">
                <span class="task-type-pill" style="font-size: 10px;">${p.language || 'Code'}</span>
                <span style="font-size: 11px; color: var(--text-muted); font-family: var(--font-mono);">#${p.id}</span>
              </div>
              <div class="inspect-task-title">${p.name}</div>
              <div class="inspect-task-meta">Đường dẫn: <code>${p.path || 'Local'}</code> • Tạo lúc: ${p.createdAt ? new Date(p.createdAt).toLocaleString('vi-VN') : '-'}</div>
              <div style="font-size: 12px; color: var(--text-secondary);">${p.description || 'Không có mô tả'}</div>
            </div>
          `).join("");
        }
      }
    } catch (e) {
      console.error("Lỗi khi tải kiểm duyệt người dùng:", e);
      if (el.inspectTasksContainer) {
        el.inspectTasksContainer.innerHTML = `<div style="text-align: center; color: var(--diff-del); padding: 20px;">Lỗi: ${e.message}</div>`;
      }
    }
  }

  // ==========================================================================
  // Admin Tabs & Management Handlers
  // ==========================================================================
  let currentAdminTab = "users";

  function setupAdminTabs() {
    const tabs = [
      { btnId: "adminTabUsers", paneId: "adminPaneUsers", tabName: "users", loader: loadAdminDashboardData },
      { btnId: "adminTabTools", paneId: "adminPaneTools", tabName: "tools", loader: loadAdminTools },
      { btnId: "adminTabSettings", paneId: "adminPaneSettings", tabName: "settings", loader: loadAdminSettings },
      { btnId: "adminTabTasks", paneId: "adminPaneTasks", tabName: "tasks", loader: loadAdminSystemTasks },
      { btnId: "adminTabAudit", paneId: "adminPaneAudit", tabName: "audit", loader: loadAdminAuditLogs }
    ];

    tabs.forEach(t => {
      const btn = document.getElementById(t.btnId);
      if (btn) {
        btn.onclick = () => {
          tabs.forEach(o => {
            const b = document.getElementById(o.btnId);
            const p = document.getElementById(o.paneId);
            if (b) b.classList.toggle("active", o.btnId === t.btnId);
            if (p) p.style.display = o.btnId === t.btnId ? "block" : "none";
          });
          currentAdminTab = t.tabName;
          t.loader();
        };
      }
    });

    const btnRefreshTools = document.getElementById("btnRefreshAdminTools");
    if (btnRefreshTools) btnRefreshTools.onclick = loadAdminTools;

    const btnRefreshTasks = document.getElementById("btnRefreshAdminTasks");
    if (btnRefreshTasks) btnRefreshTasks.onclick = loadAdminSystemTasks;

    const btnRefreshAudit = document.getElementById("btnRefreshAdminAudit");
    if (btnRefreshAudit) btnRefreshAudit.onclick = loadAdminAuditLogs;

    // Form Model Config
    const formModel = document.getElementById("formAdminModelConfig");
    if (formModel) {
      formModel.onsubmit = async (e) => {
        e.preventDefault();
        const model = document.getElementById("inputAdminActiveModel").value.trim();
        try {
          await window.apiClient.updateAdminModel(model);
          alert(`Đã lưu mô hình hoạt động: ${model}`);
        } catch (err) {
          alert(`Lỗi cập nhật model: ${err.message}`);
        }
      };
    }

    // Form Key Config
    const formKey = document.getElementById("formAdminKeyConfig");
    if (formKey) {
      formKey.onsubmit = async (e) => {
        e.preventDefault();
        const key = document.getElementById("inputAdminGeminiKey").value.trim();
        try {
          await window.apiClient.updateAdminGeminiKey(key);
          alert("Đã cập nhật Gemini API Key thành công!");
          document.getElementById("inputAdminGeminiKey").value = "";
          await loadAdminSettings();
        } catch (err) {
          alert(`Lỗi cập nhật API Key: ${err.message}`);
        }
      };
    }
  }

  async function loadAdminTools() {
    const container = document.getElementById("adminToolsListContainer");
    if (!container) return;
    container.innerHTML = `<div style="text-align: center; color: var(--text-muted); padding: 16px;">Đang tải danh sách công cụ...</div>`;
    try {
      const tools = await window.apiClient.getAdminTools();
      if (!tools || tools.length === 0) {
        container.innerHTML = `<div style="color: var(--text-muted); padding: 16px;">Không có công cụ nào.</div>`;
        return;
      }
      container.innerHTML = tools.map(t => `
        <div class="tool-switch-card">
          <div class="tool-switch-info">
            <div class="tool-switch-name">${t.displayName || t.toolName} <code>(${t.toolName})</code></div>
            <div class="tool-switch-desc">${t.description || 'Công cụ hệ thống cho phép Agent tự động gọi'}</div>
          </div>
          <label class="toggle-switch">
            <input type="checkbox" data-tool-name="${t.toolName}" ${t.enabled ? 'checked' : ''}>
            <span class="toggle-slider"></span>
          </label>
        </div>
      `).join("");

      container.querySelectorAll("input[data-tool-name]").forEach(input => {
        input.onchange = async () => {
          const name = input.getAttribute("data-tool-name");
          try {
            await window.apiClient.toggleAdminTool(name);
          } catch (err) {
            alert(`Lỗi thay đổi trạng thái công cụ: ${err.message}`);
            input.checked = !input.checked;
          }
        };
      });
    } catch (err) {
      container.innerHTML = `<div style="color: var(--diff-del); padding: 16px;">Lỗi tải công cụ: ${err.message}</div>`;
    }
  }

  async function loadAdminSettings() {
    const inputModel = document.getElementById("inputAdminActiveModel");
    const hint = document.getElementById("adminCurrentKeyHint");
    try {
      const settings = await window.apiClient.getAdminSettings();
      if (settings) {
        if (inputModel) inputModel.value = settings.model || "gemini-3.5-flash-lite";
        if (hint) hint.textContent = `Trạng thái Key hiện tại: ${settings.geminiApiKeyMasked || 'Chưa thiết lập'}`;
      }
    } catch (err) {
      console.warn("Could not load admin settings", err);
    }
  }

  async function loadAdminSystemTasks() {
    const tbody = document.getElementById("adminSystemTasksTableBody");
    if (!tbody) return;
    tbody.innerHTML = `<tr><td colspan="7" style="text-align: center; color: var(--text-muted); padding: 16px;">Đang tải tác vụ...</td></tr>`;
    try {
      const tasks = await window.apiClient.getAdminAllTasks();
      if (!tasks || tasks.length === 0) {
        tbody.innerHTML = `<tr><td colspan="7" style="text-align: center; color: var(--text-muted); padding: 16px;">Chưa có tác vụ nào trong hệ thống.</td></tr>`;
        return;
      }
      tbody.innerHTML = tasks.map(t => {
        const timeStr = t.createdAt ? new Date(t.createdAt).toLocaleString('vi-VN') : '-';
        return `
          <tr>
            <td style="font-family: var(--font-mono); font-weight: 600;">#${t.id}</td>
            <td><strong>${t.ownerUsername || 'User'}</strong></td>
            <td>${t.projectName || 'Dự án'}</td>
            <td><span class="task-type-pill" style="font-size: 10px;">${t.type || 'TASK'}</span></td>
            <td><span class="severity-tag ${t.status === 'COMPLETED' ? 'LOW' : 'HIGH'}">${t.status}</span></td>
            <td style="font-size: 11px; color: var(--text-muted);">${timeStr}</td>
            <td style="font-size: 11.5px; color: ${t.errorMessage ? 'var(--diff-del)' : 'var(--text-secondary)'};">
              ${t.errorMessage ? t.errorMessage : 'Hoàn thành tốt'}
            </td>
          </tr>
        `;
      }).join("");
    } catch (err) {
      tbody.innerHTML = `<tr><td colspan="7" style="text-align: center; color: var(--diff-del); padding: 16px;">Lỗi: ${err.message}</td></tr>`;
    }
  }

  async function loadAdminAuditLogs() {
    const tbody = document.getElementById("adminAuditTableBody");
    if (!tbody) return;
    tbody.innerHTML = `<tr><td colspan="5" style="text-align: center; color: var(--text-muted); padding: 16px;">Đang tải nhật ký kiểm toán...</td></tr>`;
    try {
      const logs = await window.apiClient.getAdminAuditLogs();
      if (!logs || logs.length === 0) {
        tbody.innerHTML = `<tr><td colspan="5" style="text-align: center; color: var(--text-muted); padding: 16px;">Chưa có nhật ký kiểm toán nào.</td></tr>`;
        return;
      }
      tbody.innerHTML = logs.map(l => {
        const timeStr = l.createdAt ? new Date(l.createdAt).toLocaleString('vi-VN') : '-';
        return `
          <tr>
            <td style="font-family: var(--font-mono); font-size: 11px; color: var(--text-muted);">${timeStr}</td>
            <td><strong>${l.username || 'System'}</strong></td>
            <td><span style="font-family: var(--font-mono); font-size: 11px; background: var(--bg-subtle); padding: 2px 6px; border-radius: 4px;">${l.action}</span></td>
            <td style="font-size: 12px; color: var(--text-secondary);">${l.details || '-'}</td>
            <td style="font-family: var(--font-mono); font-size: 11px; color: var(--text-muted);">${l.ipAddress || '127.0.0.1'}</td>
          </tr>
        `;
      }).join("");
    } catch (err) {
      tbody.innerHTML = `<tr><td colspan="5" style="text-align: center; color: var(--diff-del); padding: 16px;">Lỗi: ${err.message}</td></tr>`;
    }
  }

  // ==========================================================================
  // Interactive AI Chat Handlers
  // ==========================================================================
  let activeChatConversationId = null;

  async function loadChatConversations() {
    const listEl = document.getElementById("chatConversationsList");
    if (!listEl) return;
    try {
      const convs = await window.apiClient.getChatConversations();
      if (!convs || convs.length === 0) {
        listEl.innerHTML = `<div style="font-size: 11px; color: var(--text-muted); padding: 8px;">Chưa có hội thoại nào</div>`;
        return;
      }
      listEl.innerHTML = convs.map(c => `
        <div class="chat-conv-item ${c.id === activeChatConversationId ? 'active' : ''}" data-conv-id="${c.id}" title="${c.title}">
          ${c.title || 'Hội thoại #' + c.id}
        </div>
      `).join("");

      listEl.querySelectorAll(".chat-conv-item").forEach(item => {
        item.onclick = () => {
          const id = Number(item.getAttribute("data-conv-id"));
          selectChatConversation(id);
        };
      });

      if (!activeChatConversationId && convs.length > 0) {
        selectChatConversation(convs[0].id);
      }
    } catch (e) {
      console.warn("Could not load chat conversations", e);
    }
  }

  async function selectChatConversation(id) {
    activeChatConversationId = id;
    const listEl = document.getElementById("chatConversationsList");
    if (listEl) {
      listEl.querySelectorAll(".chat-conv-item").forEach(item => {
        item.classList.toggle("active", Number(item.getAttribute("data-conv-id")) === id);
      });
    }
    const container = document.getElementById("chatMessagesContainer");
    if (container) {
      container.innerHTML = `<div style="text-align: center; color: var(--text-muted); padding: 20px;">Đang tải tin nhắn...</div>`;
      try {
        const detail = await window.apiClient.getChatConversationDetail(id);
        if (detail && detail.messages && detail.messages.length > 0) {
          container.innerHTML = detail.messages.map(m => `
            <div class="chat-msg-row ${m.sender === 'USER' ? 'user' : 'agent'}">
              <div class="chat-msg-bubble">${m.content}</div>
              <div class="chat-msg-meta">${m.sender === 'USER' ? 'Bạn' : 'Soopi Agent'} • ${new Date(m.createdAt).toLocaleTimeString('vi-VN')}</div>
            </div>
          `).join("");
          container.scrollTop = container.scrollHeight;
        } else {
          container.innerHTML = `<div style="text-align: center; color: var(--text-muted); margin-top: 40px;">Bắt đầu trò chuyện với AI Agent!</div>`;
        }
      } catch (e) {
        container.innerHTML = `<div style="color: var(--diff-del); text-align: center;">Lỗi tải tin nhắn: ${e.message}</div>`;
      }
    }
  }

  async function handleSendChatMessage() {
    const input = document.getElementById("inputChatMessage");
    if (!input) return;
    const msg = input.value.trim();
    if (!msg) return;

    input.value = "";
    const container = document.getElementById("chatMessagesContainer");
    if (container) {
      const userBubble = document.createElement("div");
      userBubble.className = "chat-msg-row user";
      userBubble.innerHTML = `
        <div class="chat-msg-bubble">${msg}</div>
        <div class="chat-msg-meta">Bạn • ${new Date().toLocaleTimeString('vi-VN')}</div>
      `;
      container.appendChild(userBubble);

      const typingBubble = document.createElement("div");
      typingBubble.className = "chat-msg-row agent";
      typingBubble.id = "agentTypingBubble";
      typingBubble.innerHTML = `
        <div class="chat-msg-bubble" style="opacity: 0.7;">Soopi Agent đang suy nghĩ...</div>
      `;
      container.appendChild(typingBubble);
      container.scrollTop = container.scrollHeight;

      try {
        const res = await window.apiClient.sendChatMessage(msg, activeChatConversationId);
        typingBubble.remove();
        if (res) {
          activeChatConversationId = res.conversationId;
          const agentBubble = document.createElement("div");
          agentBubble.className = "chat-msg-row agent";
          agentBubble.innerHTML = `
            <div class="chat-msg-bubble">${res.content}</div>
            <div class="chat-msg-meta">Soopi Agent • ${new Date(res.createdAt).toLocaleTimeString('vi-VN')}</div>
          `;
          container.appendChild(agentBubble);
          container.scrollTop = container.scrollHeight;
          loadChatConversations();
        }
      } catch (e) {
        typingBubble.remove();
        alert("Lỗi khi gửi tin nhắn: " + e.message);
      }
    }
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

  // View Switcher: Unauthenticated Landing vs Welcome vs Workspace
  function renderView() {
    if (!state.currentUser) {
      if (el.landingView) el.landingView.style.display = "flex";
      if (el.welcomeView) el.welcomeView.style.display = "none";
      if (el.workspaceView) el.workspaceView.style.display = "none";
      if (el.appSidebar) el.appSidebar.classList.add("collapsed");
    } else {
      if (el.landingView) el.landingView.style.display = "none";
      if (el.appSidebar) el.appSidebar.classList.remove("collapsed");
      if (state.view === "workspace" && state.activeTask) {
        if (el.welcomeView) el.welcomeView.style.display = "none";
        if (el.workspaceView) el.workspaceView.style.display = "flex";
      } else {
        if (el.welcomeView) el.welcomeView.style.display = "flex";
        if (el.workspaceView) el.workspaceView.style.display = "none";
      }
    }
  }

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
      startTaskPollingIfNeeded();

      if (state.currentUser && state.currentUser.role === 'ADMIN') {
        loadAdminDashboardData();
      }
    } catch (err) {
      console.error("Lỗi khi tải dữ liệu từ CSDL:", err);
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

  // Render Recent Tasks on Welcome View
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
            Nếu bạn đã tạo tác vụ từ Postman dưới tài khoản khác (ví dụ: <code>anh2</code>), hãy chuyển tài khoản để xem.
          </p>
          <div style="display: flex; gap: 8px; justify-content: center;">
            <button class="btn-primary-sm" id="btnWelcomeNewTask">+ Chạy AI Review Mới</button>
            <button class="btn-secondary-sm" id="btnWelcomeSwitchUser">Chuyển tài khoản</button>
          </div>
        </div>
      `;
      const btnNew = document.getElementById("btnWelcomeNewTask");
      if (btnNew) btnNew.addEventListener("click", () => { populateProjectSelect(); openModal(el.modalCreateTask); });
      const btnSwitch = document.getElementById("btnWelcomeSwitchUser");
      if (btnSwitch) btnSwitch.addEventListener("click", () => openAccountOrAuthModal());
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
              <span class="task-type-tag">${t.type}</span>
            </div>
            <div style="display: flex; align-items: center; gap: 8px;">
              <button class="btn-pin-task-welcome ${t.isPinned ? 'pinned' : ''}" data-pin-task="${t.id}" title="${t.isPinned ? 'Bỏ ghim tác vụ' : 'Ghim tác vụ lên thanh bên'}">
                ${t.isPinned ? 'Bỏ ghim' : 'Ghim'}
              </button>
              <span class="status-indicator-pill ${t.status}">
                ${isProcessing ? '<span class="spin-dot"></span>' : ''}
                ${t.status}
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
    el.dbStatusText.textContent = "Kiểm tra CSDL...";
    const isOnline = await window.apiClient.checkHealth();
    if (isOnline) {
      el.dbStatusPill.className = "db-status-pill";
      el.dbIndicator.style.background = "var(--diff-add)";
      el.dbStatusText.textContent = "CSDL MySQL: Đã kết nối";
    } else {
      el.dbStatusPill.className = "db-status-pill demo";
      el.dbIndicator.style.background = "#f59e0b";
      el.dbStatusText.textContent = "Chưa kết nối máy chủ";
    }
  }

  // Populate project select in modals
  function populateProjectSelect() {
    if (!el.selectTaskProject) return;
    if (state.projects.length === 0) {
      el.selectTaskProject.innerHTML = `<option value="">Chưa có dự án nào trong CSDL - hãy mở dự án trước</option>`;
      return;
    }
    el.selectTaskProject.innerHTML = state.projects.map(p => `
      <option value="${p.id}">${p.name} (${p.language || 'Java'})</option>
    `).join("");
  }

  // Render Sidebar (PINNED & PROJECTS from DB)
  function renderSidebar() {
    // 1. PINNED TASKS (Chỉ hiện khi có task được ghim)
    const hasPinned = state.pinnedTasks && state.pinnedTasks.length > 0;
    if (el.sectionPinnedTasks) {
      el.sectionPinnedTasks.style.display = hasPinned ? "block" : "none";
    }
    if (el.pinnedCountBadge) {
      el.pinnedCountBadge.textContent = state.pinnedTasks.length;
    }

    if (hasPinned) {
      el.pinnedTasksContainer.innerHTML = state.pinnedTasks.map(t => {
        const timeAgo = formatRelativeTime(t.createdAt);
        const isActive = state.activeTask && state.activeTask.id === t.id;
        const statusClass = t.status || "COMPLETED";
        const isPinned = t.isPinned === true;

        return `
          <div class="task-item-card ${isActive ? 'active' : ''}" data-task-id="${t.id}" title="Tạo lúc: ${new Date(t.createdAt || Date.now()).toLocaleString('vi-VN')}">
            <div class="task-left">
              <span class="task-type-pill">${t.type || 'REVIEW'}</span>
              <span class="task-title-text">${t.projectName || ('Task #' + t.id)}</span>
            </div>
            <div class="task-right">
              <span class="task-status-dot ${statusClass}" title="Trạng thái: ${statusClass}"></span>
              <span class="task-time-text">${timeAgo}</span>
              <button class="pin-toggle-btn ${isPinned ? 'pinned' : ''}" data-pin-task="${t.id}" title="${isPinned ? 'Bỏ ghim' : 'Ghim lên đầu'}">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="${isPinned ? 'currentColor' : 'none'}" stroke="currentColor" stroke-width="2">
                  <line x1="12" y1="17" x2="12" y2="22"></line>
                  <path d="M5 17h14v-2l-2-2V5a1 1 0 0 0-1-1h-8a1 1 0 0 0-1 1v8l-2 2v2z"></path>
                </svg>
              </button>
            </div>
          </div>
        `;
      }).join("");
    } else {
      el.pinnedTasksContainer.innerHTML = "";
    }

    // 2. PROJECTS
    if (state.projects.length === 0) {
      el.projectsTreeContainer.innerHTML = `
        <div style="padding: 12px; font-size: 12px; color: var(--text-muted); text-align: center; border: 1px dashed var(--border-main); border-radius: 6px;">
          ${state.currentUser ? 'Chưa có dự án nào trong CSDL.<br><button id="btnEmptyAddProject" style="margin-top: 6px; padding: 4px 10px; font-size: 11.5px; cursor: pointer; border-radius: 4px; background: var(--bg-subtle); border: 1px solid var(--border-main);">+ Thêm dự án đầu tiên</button>' : '<button id="btnEmptyLogin" style="margin-top: 6px; padding: 4px 10px; font-size: 11.5px; cursor: pointer; border-radius: 4px; background: var(--accent-primary); color: #fff; border: none;">Đăng nhập để xem dự án</button>'}
        </div>
      `;
      const btnEmpty = document.getElementById("btnEmptyAddProject");
      if (btnEmpty) btnEmpty.addEventListener("click", () => openModal(el.modalOpenProject));
      const btnEmptyLogin = document.getElementById("btnEmptyLogin");
      if (btnEmptyLogin) btnEmptyLogin.addEventListener("click", () => openLoginModal(false));
    } else {
      el.projectsTreeContainer.innerHTML = state.projects.map(proj => {
        const projTasks = state.tasks.filter(t => t.projectId === proj.id);
        const tasksHtml = projTasks.length > 0 ? projTasks.map(t => {
          const timeAgo = formatRelativeTime(t.createdAt);
          const statusClass = t.status || "COMPLETED";
          return `
            <li class="task-item-card" data-task-id="${t.id}" style="padding: 5px 8px; font-size: 12px;" title="${t.type} • Tạo lúc ${new Date(t.createdAt).toLocaleString('vi-VN')}">
              <div class="task-left">
                <span class="task-type-pill" style="font-size: 9px;">${t.type}</span>
                <span class="task-title-text">#${t.id}</span>
              </div>
              <div class="task-right">
                <span class="task-status-dot ${statusClass}"></span>
                <span class="task-time-text">${timeAgo}</span>
              </div>
            </li>
          `;
        }).join("") : `
          <div style="padding: 4px 8px; font-size: 11px; color: var(--text-muted);">Chưa có review nào</div>
        `;

        return `
          <div class="project-group-box">
            <div class="project-folder-row" data-proj-id="${proj.id}">
              <div class="project-folder-left">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"></path>
                </svg>
                <span class="project-folder-name">${proj.name}</span>
              </div>
              <span class="project-lang-pill">${proj.language || 'Java'}</span>
            </div>
            <ul class="project-subtasks-list">
              ${tasksHtml}
            </ul>
          </div>
        `;
      }).join("");
    }

    attachSidebarClickEvents();
  }

  function attachSidebarClickEvents() {
    // Task click in sidebar
    document.querySelectorAll("[data-task-id]").forEach(item => {
      item.addEventListener("click", (e) => {
        if (e.target.closest("[data-pin-task]")) return;
        const taskId = Number(item.getAttribute("data-task-id"));
        const task = state.tasks.find(t => t.id === taskId);
        if (task) {
          const project = state.projects.find(p => p.id === task.projectId) || { name: task.projectName || "Dự án", path: "Local", language: "Java" };
          selectTask(task, project);
        }
      });
    });

    // Toggle Pin button
    document.querySelectorAll("[data-pin-task]").forEach(btn => {
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
          if (state.activeTask && state.activeTask.id === taskId) {
            state.activeTask.isPinned = updated.isPinned;
            if (el.pinActiveTaskIcon) el.pinActiveTaskIcon.textContent = "";
            if (el.pinActiveTaskLabel) el.pinActiveTaskLabel.textContent = updated.isPinned ? "Đã ghim" : "Ghim tác vụ";
          }
        }
      });
    });

    // Project folder click
    document.querySelectorAll(".project-folder-row").forEach(row => {
      row.addEventListener("click", () => {
        const projId = Number(row.getAttribute("data-proj-id"));
        const project = state.projects.find(p => p.id === projId);
        if (project) {
          const projectTasks = state.tasks.filter(t => t.projectId === projId);
          const latestTask = projectTasks.length > 0 ? projectTasks[0] : null;
          selectTask(latestTask, project);
        }
      });
    });
  }

  // Switch to Workspace & Show Task/Project Details
  function selectTask(task, project) {
    state.activeTask = task;
    state.activeProject = project;
    state.view = "workspace";

    el.welcomeView.style.display = "none";
    el.workspaceView.style.display = "flex";

    // Tự động kích hoạt tab "Kết quả đánh giá" để hiển thị thông tin ngay lập tức khi click vào task
    state.activeTab = "findings";
    if (el.viewTabs) {
      el.viewTabs.forEach(btn => {
        if (btn.getAttribute("data-tab") === "findings") {
          btn.classList.add("active");
        } else {
          btn.classList.remove("active");
        }
      });
    }
    const tabFindingsBody = document.getElementById("tabFindingsBody");
    const tabToolTraceBody = document.getElementById("tabToolTraceBody");
    const tabTasksHistoryBody = document.getElementById("tabTasksHistoryBody");
    if (tabFindingsBody) tabFindingsBody.style.display = "block";
    if (tabToolTraceBody) tabToolTraceBody.style.display = "none";
    if (tabTasksHistoryBody) tabTasksHistoryBody.style.display = "none";

    el.navActiveProjectText.textContent = project ? project.name : "Dự án";
    el.viewProjectTitle.textContent = `${project ? project.name : 'Dự án'} ${task ? '• Task #' + task.id : ''}`;
    el.viewProjectPath.textContent = project ? (project.path || "D:/AI Agent/aiagent/aiagent") : "Local";
    el.viewProjectLang.textContent = project ? (project.language || "Java") : "Java";

    const status = task ? (task.status || "COMPLETED") : "CHƯA CÓ TÁC VỤ";
    el.viewTaskStatusPill.textContent = status;
    el.viewTaskStatusPill.className = `status-indicator-pill ${status}`;
    el.viewTaskTimestamp.textContent = task ? `Tạo lúc ${formatRelativeTime(task.createdAt)}` : "";

    // Update Workspace Pin Button
    if (el.btnPinActiveTask) {
      if (task) {
        el.btnPinActiveTask.style.display = "inline-flex";
        const isPinned = task.isPinned === true;
        if (el.pinActiveTaskIcon) el.pinActiveTaskIcon.textContent = "";
        if (el.pinActiveTaskLabel) el.pinActiveTaskLabel.textContent = isPinned ? "Đã ghim" : "Ghim";
      } else {
        el.btnPinActiveTask.style.display = "none";
      }
    }

    // Update Task Action Controls (Cancel, Retry, Delete)
    const btnCancelTask = document.getElementById("btnCancelTask");
    const btnRetryTask = document.getElementById("btnRetryTask");
    const btnDeleteTask = document.getElementById("btnDeleteTask");

    if (task) {
      if (btnCancelTask) {
        btnCancelTask.style.display = (task.status === "PENDING" || task.status === "IN_PROGRESS" || task.status === "PROCESSING") ? "inline-flex" : "none";
        btnCancelTask.onclick = async () => {
          if (!confirm(`Bạn có chắc chắn muốn hủy tác vụ #${task.id} không?`)) return;
          try {
            await window.apiClient.cancelTask(task.id);
            await loadDatabaseData();
            alert(`Đã gửi yêu cầu hủy tác vụ #${task.id}`);
          } catch (e) {
            alert(`Lỗi khi hủy: ${e.message}`);
          }
        };
      }
      if (btnRetryTask) {
        btnRetryTask.style.display = "inline-flex";
        btnRetryTask.onclick = async () => {
          try {
            const retried = await window.apiClient.retryTask(task.id);
            await loadDatabaseData();
            selectTask(retried, project);
            alert(`Đã khởi chạy lại tác vụ #${task.id}`);
          } catch (e) {
            alert(`Lỗi khi chạy lại: ${e.message}`);
          }
        };
      }
      if (btnDeleteTask) {
        btnDeleteTask.style.display = "inline-flex";
        btnDeleteTask.onclick = async () => {
          if (!confirm(`Bạn có chắc muốn xóa vĩnh viễn tác vụ #${task.id} không?`)) return;
          try {
            await window.apiClient.deleteTask(task.id);
            state.activeTask = null;
            await loadDatabaseData();
            alert(`Đã xóa tác vụ #${task.id}`);
          } catch (e) {
            alert(`Lỗi khi xóa: ${e.message}`);
          }
        };
      }
    } else {
      [btnCancelTask, btnRetryTask, btnDeleteTask].forEach(b => {
        if (b) b.style.display = "none";
      });
    }

    // Parse Output & Findings from DB
    let findings = [];
    let summary = "";
    let suggestions = [];

    const aiSummaryCard = document.getElementById("aiSummaryCard");
    const aiSummaryText = document.getElementById("aiSummaryText");
    const aiSuggestionsBox = document.getElementById("aiSuggestionsBox");
    const aiSuggestionsList = document.getElementById("aiSuggestionsList");

    if (task && task.output) {
      let rawJsonStr = typeof task.output === "string" ? task.output : JSON.stringify(task.output, null, 2);
      try {
        const parsed = typeof task.output === "string" ? JSON.parse(task.output) : task.output;
        summary = parsed.summary || "";
        if (typeof summary === "object") summary = JSON.stringify(summary);

        // Support BOTH 'issues' (Spring AI CodeReviewAgent) and 'findings' (Mock/other agents)
        const rawList = parsed.issues || parsed.findings || [];
        if (Array.isArray(rawList)) {
          findings = rawList.map(item => ({
            severity: (item.severity || "LOW").toUpperCase(),
            category: item.category || "CODE_QUALITY",
            file: item.file || "Dự án",
            line: item.line || null,
            title: item.title || (item.category ? `[${item.category}] ` : "") + (item.description ? (item.description.length > 70 ? item.description.substring(0, 70) + "..." : item.description) : "Phát hiện mã nguồn"),
            description: item.description || item.message || "",
            suggestion: item.recommendation || item.suggestion || ""
          }));
        }

        if (Array.isArray(parsed.suggestions)) {
          suggestions = parsed.suggestions;
        }
      } catch (e) {
        console.warn("Could not parse output as JSON, showing raw output", e);
        summary = rawJsonStr;
      }
    } else if (task && task.errorMessage) {
      summary = `Tác vụ gặp lỗi khi thực thi: ${task.errorMessage}`;
    }

    // Render AI Summary Card
    if (aiSummaryCard) {
      if (summary) {
        aiSummaryCard.style.display = "block";
        if (aiSummaryText) aiSummaryText.textContent = summary || "Đã nhận kết quả đánh giá từ AI Agent.";
        
        if (aiSuggestionsBox && aiSuggestionsList) {
          if (suggestions.length > 0) {
            aiSuggestionsBox.style.display = "block";
            aiSuggestionsList.innerHTML = suggestions.map(s => `<li>${s}</li>`).join("");
          } else {
            aiSuggestionsBox.style.display = "none";
          }
        }
      } else {
        aiSummaryCard.style.display = "none";
      }
    }

    // Render Metrics
    const criticalCount = findings.filter(f => f.severity === "CRITICAL").length;
    const highCount = findings.filter(f => f.severity === "HIGH").length;
    const medCount = findings.filter(f => f.severity === "MEDIUM").length;
    const lowCount = findings.filter(f => f.severity === "LOW" || f.severity === "CODE_QUALITY").length;

    el.metricCritical.textContent = criticalCount;
    el.metricHigh.textContent = highCount;
    el.metricMedium.textContent = medCount;
    el.metricLow.textContent = lowCount;
    el.totalFindingsNum.textContent = findings.length;

    // Render Findings List
    if (findings.length === 0) {
      if (task && (task.status === "PROCESSING" || task.status === "PENDING")) {
        el.findingsListWrapper.innerHTML = `
          <div style="padding: 32px 20px; text-align: center; color: var(--primary-accent); border: 1px dashed var(--border-main); border-radius: 8px; background: var(--bg-subtle);">
            <div style="font-size: 20px; margin-bottom: 8px;"> ⏳</div>
            <div style="font-weight: 600; font-size: 14px;">AI Agent đang phân tích mã nguồn...</div>
            <div style="font-size: 12px; color: var(--text-muted); margin-top: 4px;">Hệ thống đang tự động theo dõi tiến độ và sẽ cập nhật kết quả ngay khi hoàn thành.</div>
          </div>
        `;
      } else {
        el.findingsListWrapper.innerHTML = `
          <div style="padding: 24px; text-align: center; color: var(--text-muted); border: 1px dashed var(--border-main); border-radius: 8px;">
            ${summary ? 'AI Agent không phát hiện vấn đề bất thường nào cần cảnh báo.' : 'Tác vụ này chưa có kết quả đánh giá trong CSDL.'}
          </div>
        `;
      }
    } else {
      el.findingsListWrapper.innerHTML = findings.map(f => `
        <div class="finding-card-item">
          <div class="finding-header-row">
            <div style="display: flex; gap: 8px; align-items: center;">
              <span class="severity-tag ${f.severity}">${f.severity}</span>
              <span class="severity-tag ${f.category}">${f.category}</span>
            </div>
            <span class="finding-file-loc">${f.file}${f.line ? ':' + f.line : ''}</span>
          </div>
          <div class="finding-issue-title">${f.title}</div>
          <div class="finding-issue-desc">${f.description}</div>
          ${f.suggestion ? `<div class="finding-code-diff">Đề xuất sửa đổi từ AI Agent:\n${f.suggestion}</div>` : ''}
        </div>
      `).join("");
    }

    // Render Tool Traces
    el.toolTracesContainer.innerHTML = `
      <div class="tool-step-row">
        <div class="tool-step-num">1</div>
        <div class="tool-step-info">
          <div class="tool-step-header">
            <span>ListDirectoryTool</span>
            <span style="font-size: 11px; color: var(--text-muted);">120ms</span>
          </div>
          <div class="tool-step-content">Đọc cấu trúc cây thư mục tại: ${project.path || 'workspace'}\nKết quả: Khám phá các thư mục và source files.</div>
        </div>
      </div>
      <div class="tool-step-row">
        <div class="tool-step-num">2</div>
        <div class="tool-step-info">
          <div class="tool-step-header">
            <span>GrepTool</span>
            <span style="font-size: 11px; color: var(--text-muted);">85ms</span>
          </div>
          <div class="tool-step-content">Truy vấn mẫu bảo mật: "permitAll|SecurityConfig|jwt|password"\nKết quả: Tìm thấy các điểm nhạy cảm cần rà soát sâu.</div>
        </div>
      </div>
      <div class="tool-step-row">
        <div class="tool-step-num">3</div>
        <div class="tool-step-info">
          <div class="tool-step-header">
            <span>ReadFileTool</span>
            <span style="font-size: 11px; color: var(--text-muted);">64ms</span>
          </div>
          <div class="tool-step-content">Đọc nội dung chi tiết: SecurityConfig.java & Controller files.</div>
        </div>
      </div>
      <div class="tool-step-row">
        <div class="tool-step-num">4</div>
        <div class="tool-step-info">
          <div class="tool-step-header">
            <span>AI Reasoning</span>
            <span style="font-size: 11px; color: var(--text-muted);">1420ms</span>
          </div>
          <div class="tool-step-content">Lập luận tổng hợp: Hoàn tất phân tích mã nguồn và đề xuất bản vá.</div>
        </div>
      </div>
    `;

    // Render Project Tasks History in Tab 3
    const projectTasks = state.tasks.filter(t => t.projectId === project.id);
    el.totalTasksNum.textContent = projectTasks.length;
    el.tasksHistoryWrapper.innerHTML = projectTasks.map(t => `
      <div class="finding-card-item" style="cursor: pointer;" data-history-task-id="${t.id}">
        <div class="finding-header-row">
          <span class="severity-tag ${t.status === 'COMPLETED' ? 'LOW' : 'HIGH'}">${t.status}</span>
          <span class="finding-file-loc">Task #${t.id} • ${t.type}</span>
        </div>
        <div class="finding-issue-title" style="margin-top: 4px;">Tác vụ AI Review cho ${project.name}</div>
        <div class="workspace-meta-line" style="font-size: 11.5px; margin-top: 6px;">
          <span>Thời gian tạo CSDL: ${new Date(t.createdAt).toLocaleString('vi-VN')}</span>
          <span>•</span>
          <span>${formatRelativeTime(t.createdAt)}</span>
        </div>
      </div>
    `).join("");

    // Gắn sự kiện click vào từng task trong tab Lịch sử tác vụ
    el.tasksHistoryWrapper.querySelectorAll("[data-history-task-id]").forEach(item => {
      item.addEventListener("click", () => {
        const tId = Number(item.getAttribute("data-history-task-id"));
        const found = state.tasks.find(t => t.id === tId);
        if (found) {
          selectTask(found, project);
        }
      });
    });

    renderSidebar();
  }

  // Event Listeners Setup
  function initEventListeners() {
    // Theme toggle
    el.btnThemeToggle.addEventListener("click", () => {
      applyTheme(state.theme === "dark" ? "light" : "dark");
    });

    // Sidebar toggle
    el.btnToggleSidebar.addEventListener("click", () => {
      el.appSidebar.classList.toggle("collapsed");
    });

    // Brand link / Back to Home
    el.brandLink.addEventListener("click", () => {
      if (!state.currentUser) {
        state.view = "landing";
        renderView();
        return;
      }
      state.view = "welcome";
      renderView();
      el.navActiveProjectText.textContent = "soopi";
      document.querySelectorAll(".task-item-card").forEach(i => i.classList.remove("active"));
    });

    el.btnBackToHome.addEventListener("click", () => {
      el.brandLink.click();
    });

    // Tab buttons in workspace
    el.viewTabs.forEach(btn => {
      btn.addEventListener("click", () => {
        el.viewTabs.forEach(b => b.classList.remove("active"));
        btn.classList.add("active");
        const tab = btn.getAttribute("data-tab");
        document.getElementById("tabFindingsBody").style.display = tab === "findings" ? "block" : "none";
        document.getElementById("tabToolTraceBody").style.display = tab === "tooltrace" ? "block" : "none";
        document.getElementById("tabTasksHistoryBody").style.display = tab === "tasks" ? "block" : "none";
        const tabChat = document.getElementById("tabChatBody");
        if (tabChat) {
          tabChat.style.display = tab === "chat" ? "block" : "none";
          if (tab === "chat") loadChatConversations();
        }
      });
    });

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

    // Chat Controls (New Conv, Send button, Enter key)
    const btnNewChat = document.getElementById("btnNewChatConversation");
    if (btnNewChat) {
      btnNewChat.addEventListener("click", () => {
        activeChatConversationId = null;
        const container = document.getElementById("chatMessagesContainer");
        if (container) {
          container.innerHTML = `<div style="text-align: center; color: var(--text-muted); margin-top: 40px;">Bắt đầu phiên hội thoại mới với AI Agent!</div>`;
        }
        const listEl = document.getElementById("chatConversationsList");
        if (listEl) {
          listEl.querySelectorAll(".chat-conv-item").forEach(i => i.classList.remove("active"));
        }
      });
    }

    const btnSendChat = document.getElementById("btnSendChatMessage");
    if (btnSendChat) {
      btnSendChat.addEventListener("click", handleSendChatMessage);
    }

    const inputChat = document.getElementById("inputChatMessage");
    if (inputChat) {
      inputChat.addEventListener("keydown", (e) => {
        if (e.key === "Enter" && !e.shiftKey) {
          e.preventDefault();
          handleSendChatMessage();
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
        document.getElementById("inputProjectPath").value = pill.getAttribute("data-fill");
      });
    });

    // Submit: Open Project (Lưu vào CSDL)
    el.formOpenProject.addEventListener("submit", async (e) => {
      e.preventDefault();
      const path = document.getElementById("inputProjectPath").value;
      const name = document.getElementById("inputProjectName").value;
      const language = document.getElementById("inputProjectLang").value;
      const description = document.getElementById("inputProjectDesc").value;

      try {
        const newProj = await window.apiClient.createProject({ name, path, language, description });
        closeModals();
        await loadDatabaseData();
        selectTask(null, newProj);
      } catch (err) {
        alert("Lỗi khi tạo dự án vào CSDL: " + err.message);
      }
    });

    // Submit: Create AI Review Task (Lưu vào CSDL & chạy ngầm)
    el.formCreateTask.addEventListener("submit", async (e) => {
      e.preventDefault();
      const projectId = Number(document.getElementById("selectTaskProject").value);
      if (!projectId) {
        alert("Vui lòng chọn dự án trước!");
        return;
      }
      const type = document.getElementById("selectTaskType").value;
      const focus = document.getElementById("selectTaskFocus").value;
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
          selectedTools: selectedTools.length > 0 ? selectedTools : undefined,
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

    // Auth Tabs (Login vs Register)
    el.tabAuthLogin.addEventListener("click", () => {
      el.tabAuthLogin.classList.add("active");
      el.tabAuthRegister.classList.remove("active");
      el.formLogin.style.display = "block";
      el.formRegister.style.display = "none";
    });

    el.tabAuthRegister.addEventListener("click", () => {
      el.tabAuthRegister.classList.add("active");
      el.tabAuthLogin.classList.remove("active");
      el.formRegister.style.display = "block";
      el.formLogin.style.display = "none";
    });

    // Submit: Login Form
    el.formLogin.addEventListener("submit", async (e) => {
      e.preventDefault();
      const u = document.getElementById("loginUsername").value.trim();
      const p = document.getElementById("loginPassword").value;
      const submitBtn = el.formLogin.querySelector("button[type='submit']");
      const originalText = submitBtn.textContent;

      try {
        submitBtn.disabled = true;
        submitBtn.textContent = "Đang kiểm tra...";
        await window.apiClient.login(u, p);
        closeModals();
        state.currentUser = window.apiClient.getCurrentUser();
        updateUserDisplays();
        await checkConnection();
        await loadDatabaseData();
      } catch (err) {
        alert("Đăng nhập thất bại: " + (err.message || "Tài khoản hoặc mật khẩu không chính xác"));
      } finally {
        submitBtn.disabled = false;
        submitBtn.textContent = originalText;
      }
    });

    // Submit: Register Form
    el.formRegister.addEventListener("submit", async (e) => {
      e.preventDefault();
      const u = document.getElementById("regUsername").value.trim();
      const email = document.getElementById("regEmail").value.trim();
      const p = document.getElementById("regPassword").value;
      const submitBtn = el.formRegister.querySelector("button[type='submit']");
      const originalText = submitBtn.textContent;

      try {
        submitBtn.disabled = true;
        submitBtn.textContent = "Đang đăng ký...";
        await window.apiClient.register(u, email, p);
        closeModals();
        state.currentUser = window.apiClient.getCurrentUser();
        updateUserDisplays();
        await checkConnection();
        await loadDatabaseData();
      } catch (err) {
        alert("Đăng ký thất bại: " + (err.message || "Kiểm tra lại thông tin đăng ký"));
      } finally {
        submitBtn.disabled = false;
        submitBtn.textContent = originalText;
      }
    });

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
    await checkConnection();
    await loadDatabaseData();
  }

  document.addEventListener("DOMContentLoaded", init);
})();
