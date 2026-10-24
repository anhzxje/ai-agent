/**
 * ============================================================================
 * ADMIN VIEW MODULE - QUẢN LÝ BẢNG ĐIỀU HÀNH & GIÁM SÁT HỆ THỐNG
 * ============================================================================
 * Chức năng:
 * 1. Nạp và hiển thị các số liệu thống kê Dashboard Admin (Users, Projects, Tasks).
 * 2. Quản lý danh sách người dùng: Khóa/Mở khóa tài khoản, Đổi quyền, Xóa người dùng.
 * 3. Kiểm duyệt thanh tra tác vụ AI & cuộc trò chuyện của từng thành viên (User Audit).
 * 4. Bật / Tắt công cụ Agent động (Tool Calling: ListDirectory, Glob, FileSystem, Grep).
 * 5. Cấu hình Model AI & cập nhật Gemini API Key an toàn.
 * 6. Giám sát nhật ký kiểm toán (Audit Logs) và danh sách tác vụ toàn hệ thống.
 * ============================================================================
 */
(function() {
  const escapeHtml = window.MarkdownUtils.escapeHtml;
  let savedEl = null;
  let savedState = null;
  let savedApiClient = null;

  async function openAdminPanelModal(el, state, apiClient, openModalFn) {
    if (!state.currentUser || state.currentUser.role !== 'ADMIN') {
      alert("Bạn không có quyền quản trị viên (ADMIN) để truy cập tính năng này.");
      return;
    }
    savedEl = el;
    savedState = state;
    savedApiClient = apiClient;

    openModalFn(el.modalAdminPanel);

    // Reset scroll position to top of modal
    const scrollContainer = el.modalAdminPanel ? el.modalAdminPanel.querySelector('.admin-panel-modal') : null;
    if (scrollContainer) {
      scrollContainer.scrollTop = 0;
    }
    const navButtons = document.querySelectorAll("#adminStickyNav [data-scroll-to]");
    navButtons.forEach((b, idx) => b.classList.toggle("active", idx === 0));

    // Initialize sticky navigation and handlers
    setupAdminNavigation(apiClient);

    // Concurrently load all 5 sections so all data is ready on the continuous scrollable page
    await Promise.allSettled([
      loadAdminDashboardData(el, state, apiClient),
      loadAdminTools(apiClient),
      loadAdminSettings(apiClient),
      loadAdminSystemTasks(apiClient),
      loadAdminAuditLogs(apiClient)
    ]);
  }

  async function loadAdminDashboardData(el, state, apiClient) {
    if (el.adminUsersTableBody) {
      el.adminUsersTableBody.innerHTML = `<tr><td colspan="7" style="text-align: center; color: var(--text-muted); padding: 16px;">Đang tải danh sách tài khoản từ CSDL...</td></tr>`;
    }

    try {
      const [stats, users] = await Promise.all([
        apiClient.getAdminStats(),
        apiClient.getAdminUsers()
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
                  <strong>${escapeHtml(u.username)}</strong>
                  ${isSelf ? '<span style="font-size: 10px; margin-left: 4px; opacity: 0.6;">(Bạn)</span>' : ''}
                </td>
                <td style="color: var(--text-secondary);">${escapeHtml(u.email || '-')}</td>
                <td><span class="${roleClass}">${escapeHtml(u.role)}</span></td>
                <td>${lockStatusHtml}</td>
                <td>
                  <span style="font-family: var(--font-mono); font-size: 11.5px; color: var(--text-secondary);">
                    ${u.projectCount || 0} dự án • ${u.taskCount || 0} tác vụ
                  </span>
                </td>
                <td style="color: var(--text-muted); font-size: 11.5px;">${dateStr}</td>
                <td style="text-align: right; white-space: nowrap;">
                  <button type="button" class="btn-table-action primary" data-action="inspect-user" data-user-id="${u.id}" data-username="${escapeHtml(u.username)}" data-email="${escapeHtml(u.email || '')}" data-role="${escapeHtml(u.role)}" title="Thanh tra nhật ký chat & tác vụ của tài khoản này">
                    Kiểm duyệt
                  </button>
                  ${isSelf ? '' : `
                    <button type="button" class="btn-table-action" data-action="toggle-lock" data-user-id="${u.id}" data-username="${escapeHtml(u.username)}" data-locked="${u.isLocked}" title="${u.isLocked ? 'Mở khóa' : 'Khóa'} tài khoản">
                      ${u.isLocked ? 'Mở khóa' : 'Khóa'}
                    </button>
                    <button type="button" class="btn-table-action" data-action="toggle-role" data-user-id="${u.id}" data-username="${escapeHtml(u.username)}" data-role="${escapeHtml(u.role)}" title="Chuyển quyền sang ${u.role === 'ADMIN' ? 'USER' : 'ADMIN'}">
                      ${u.role === 'ADMIN' ? 'Giáng USER' : 'Nâng ADMIN'}
                    </button>
                    <button type="button" class="btn-table-action danger" data-action="delete-user" data-user-id="${u.id}" data-username="${escapeHtml(u.username)}" title="Xóa tài khoản vĩnh viễn">
                      Xóa
                    </button>
                  `}
                </td>
              </tr>
            `;
          }).join("");

          // Bind Action Buttons
          el.adminUsersTableBody.querySelectorAll("[data-action='inspect-user']").forEach(btn => {
            btn.addEventListener("click", () => {
              const uId = Number(btn.getAttribute("data-user-id"));
              const username = btn.getAttribute("data-username");
              const email = btn.getAttribute("data-email");
              const role = btn.getAttribute("data-role");
              openInspectUserModal({ id: uId, username, email, role }, el, apiClient, openModalFn);
            });
          });

          el.adminUsersTableBody.querySelectorAll("[data-action='toggle-lock']").forEach(btn => {
            btn.addEventListener("click", async () => {
              const uId = Number(btn.getAttribute("data-user-id"));
              const username = btn.getAttribute("data-username");
              const isLocked = btn.getAttribute("data-locked") === "true";
              const actionText = isLocked ? "mở khóa" : "khóa";

              if (confirm(`Bạn có chắc muốn ${actionText} tài khoản "${username}" không?`)) {
                try {
                  await apiClient.toggleUserLock(uId);
                  await loadAdminDashboardData(el, state, apiClient);
                } catch (err) {
                  alert("Lỗi khi thay đổi trạng thái: " + err.message);
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
                  await apiClient.updateUserRole(uId, nextRole);
                  await loadAdminDashboardData(el, state, apiClient);
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
                  await apiClient.deleteUser(uId);
                  await loadAdminDashboardData(el, state, apiClient);
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
        el.adminUsersTableBody.innerHTML = `<tr><td colspan="7" style="text-align: center; color: var(--diff-del); padding: 16px;">Lỗi kết nối máy chủ quản trị: ${escapeHtml(e.message)}</td></tr>`;
      }
    }
  }

  // Open and load User Inspection (Admin Audit) Modal
  async function openInspectUserModal(user, el, apiClient, openModalFn) {
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
    openModalFn(el.modalAdminInspectUser);

    try {
      const [userTasks, userProjects] = await Promise.all([
        apiClient.getUserTasksForAdmin(user.id),
        apiClient.getUserProjectsForAdmin(user.id)
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
              <div class="inspect-item-card">
                <div class="inspect-item-header">
                  <div style="display: flex; align-items: center; gap: 8px;">
                    <span class="task-type-pill" style="font-size: 10px;">${escapeHtml(t.type || 'TASK')}</span>
                    <strong style="font-size: 13px;">${escapeHtml(t.projectName || 'Dự án')}</strong>
                    <span style="font-size: 11px; opacity: 0.6; font-family: var(--font-mono);">#${t.id}</span>
                  </div>
                  <span class="severity-tag ${t.status === 'COMPLETED' ? 'LOW' : 'HIGH'}" style="font-size: 10px;">${escapeHtml(t.status)}</span>
                </div>
                <div class="inspect-item-body">
                  ${escapeHtml(summaryText)}
                </div>
                <div class="inspect-item-footer">
                  <span>${dateStr}</span>
                  ${t.isPinned ? '<span style="color: var(--accent); font-weight: 600;">★ Đã ghim</span>' : ''}
                </div>
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
          el.inspectProjectsContainer.innerHTML = userProjects.map(p => {
            const dateStr = p.createdAt ? new Date(p.createdAt).toLocaleDateString('vi-VN') : 'Gần đây';
            return `
              <div class="inspect-item-card">
                <div class="inspect-item-header">
                  <div style="display: flex; align-items: center; gap: 8px;">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"></path>
                    </svg>
                    <strong style="font-size: 13.5px;">${escapeHtml(p.name)}</strong>
                    <span style="font-size: 11px; opacity: 0.6; font-family: var(--font-mono);">#${p.id}</span>
                  </div>
                  <span class="badge-role-user" style="font-size: 10px;">${escapeHtml(p.language || 'Code')}</span>
                </div>
                <div class="inspect-item-body" style="font-family: var(--font-mono); font-size: 11.5px; opacity: 0.85;">
                  ${escapeHtml(p.path || 'Chưa có đường dẫn')}
                </div>
                <div class="inspect-item-footer">
                  <span>Tạo ngày: ${dateStr}</span>
                </div>
              </div>
            `;
          }).join("");
        }
      }
    } catch (err) {
      console.error("Lỗi tải thông tin kiểm duyệt:", err);
      if (el.inspectTasksContainer) {
        el.inspectTasksContainer.innerHTML = `<div style="color: var(--diff-del); padding: 20px; text-align: center;">Lỗi tải dữ liệu: ${escapeHtml(err.message)}</div>`;
      }
    }
  }

  function setupAdminNavigation(apiClient) {
    if (apiClient) savedApiClient = apiClient;
    const client = apiClient || savedApiClient;

    // 1. Sticky Navigation Click Handlers (Smooth scroll to section)
    const navButtons = document.querySelectorAll("#adminStickyNav [data-scroll-to]");
    navButtons.forEach(btn => {
      btn.onclick = (e) => {
        e.preventDefault();
        const targetId = btn.getAttribute("data-scroll-to");
        const target = document.getElementById(targetId);
        if (target) {
          target.scrollIntoView({ behavior: "smooth", block: "start" });
          navButtons.forEach(b => b.classList.remove("active"));
          btn.classList.add("active");
        }
      };
    });

    // 2. Scroll Spy on Modal Scroll Container (.admin-panel-modal)
    const scrollContainer = document.querySelector(".admin-panel-modal");
    if (scrollContainer && !scrollContainer._scrollSpyAttached) {
      scrollContainer._scrollSpyAttached = true;
      let isThrottled = false;
      scrollContainer.addEventListener("scroll", () => {
        if (isThrottled) return;
        isThrottled = true;
        requestAnimationFrame(() => {
          isThrottled = false;
          const sections = [
            "adminSectionUsers",
            "adminSectionTools",
            "adminSectionSettings",
            "adminSectionTasks",
            "adminSectionAudit"
          ];
          const containerRect = scrollContainer.getBoundingClientRect();
          const threshold = containerRect.top + 90;
          let activeSectionId = sections[0];

          for (const secId of sections) {
            const secEl = document.getElementById(secId);
            if (secEl) {
              const rect = secEl.getBoundingClientRect();
              if (rect.top <= threshold) {
                activeSectionId = secId;
              }
            }
          }

          document.querySelectorAll("#adminStickyNav [data-scroll-to]").forEach(b => {
            b.classList.toggle("active", b.getAttribute("data-scroll-to") === activeSectionId);
          });
        });
      });
    }

    // 3. Refresh Buttons for Each Section
    const btnRefreshUsers = document.getElementById("btnRefreshAdminData");
    if (btnRefreshUsers) {
      btnRefreshUsers.onclick = () => {
        if (savedEl && savedState && client) {
          loadAdminDashboardData(savedEl, savedState, client);
        }
      };
    }

    const btnRefreshTools = document.getElementById("btnRefreshAdminTools");
    if (btnRefreshTools) {
      btnRefreshTools.onclick = () => {
        if (client) loadAdminTools(client);
      };
    }

    const btnRefreshTasks = document.getElementById("btnRefreshAdminTasks");
    if (btnRefreshTasks) {
      btnRefreshTasks.onclick = () => {
        if (client) loadAdminSystemTasks(client);
      };
    }

    const btnRefreshAudit = document.getElementById("btnRefreshAdminAudit");
    if (btnRefreshAudit) {
      btnRefreshAudit.onclick = () => {
        if (client) loadAdminAuditLogs(client);
      };
    }

    // 4. Form Submits: Model Configuration & Gemini API Key
    const formModel = document.getElementById("formAdminModelConfig");
    if (formModel) {
      formModel.onsubmit = async (e) => {
        e.preventDefault();
        const modelInput = document.getElementById("inputAdminActiveModel");
        const model = modelInput ? modelInput.value.trim() : "";
        if (!model) return;
        try {
          if (client) await client.updateAdminSettings({ model });
          alert(`Đã lưu mô hình hoạt động: ${model}`);
        } catch (err) {
          alert(`Lỗi cập nhật model: ${err.message}`);
        }
      };
    }

    const formKey = document.getElementById("formAdminKeyConfig");
    if (formKey) {
      formKey.onsubmit = async (e) => {
        e.preventDefault();
        const keyInput = document.getElementById("inputAdminGeminiKey");
        const key = keyInput ? keyInput.value.trim() : "";
        if (!key) return;
        try {
          if (client) {
            await client.updateAdminSettings({ apiKey: key });
            alert("Đã cập nhật Gemini API Key thành công!");
            if (keyInput) keyInput.value = "";
            await loadAdminSettings(client);
          }
        } catch (err) {
          alert(`Lỗi cập nhật API Key: ${err.message}`);
        }
      };
    }
  }

  async function loadAdminTools(apiClient) {
    const container = document.getElementById("adminToolsListContainer");
    if (!container) return;
    container.innerHTML = `<div style="text-align: center; color: var(--text-muted); padding: 16px;">Đang tải danh sách công cụ...</div>`;
    try {
      const tools = await apiClient.getAdminTools();
      if (!tools || tools.length === 0) {
        container.innerHTML = `<div style="color: var(--text-muted); padding: 16px;">Không có công cụ nào.</div>`;
        return;
      }
      container.innerHTML = tools.map(t => `
        <div class="tool-switch-card">
          <div class="tool-switch-info">
            <div class="tool-switch-name">${escapeHtml(t.displayName || t.toolName)} <code>(${escapeHtml(t.toolName)})</code></div>
            <div class="tool-switch-desc">${escapeHtml(t.description || 'Công cụ hệ thống cho phép Agent tự động gọi')}</div>
          </div>
          <label class="toggle-switch">
            <input type="checkbox" data-tool-name="${escapeHtml(t.toolName)}" ${t.enabled ? 'checked' : ''}>
            <span class="toggle-slider"></span>
          </label>
        </div>
      `).join("");

      container.querySelectorAll("input[data-tool-name]").forEach(input => {
        input.onchange = async () => {
          const name = input.getAttribute("data-tool-name");
          try {
            await apiClient.updateToolStatus(name, input.checked);
          } catch (err) {
            alert(`Lỗi thay đổi trạng thái công cụ: ${err.message}`);
            input.checked = !input.checked;
          }
        };
      });
    } catch (err) {
      container.innerHTML = `<div style="color: var(--diff-del); padding: 16px;">Lỗi tải công cụ: ${escapeHtml(err.message)}</div>`;
    }
  }

  async function loadAdminSettings(apiClient) {
    const inputModel = document.getElementById("inputAdminActiveModel");
    const hint = document.getElementById("adminCurrentKeyHint");
    try {
      const settings = await apiClient.getAdminSettings();
      if (settings) {
        if (inputModel) inputModel.value = settings.activeModel || "gemini-3.5-flash-lite";
        if (hint) hint.textContent = `Trạng thái Key hiện tại: ${settings.maskedApiKey || 'Chưa thiết lập'}`;
      }
    } catch (err) {
      console.warn("Could not load admin settings", err);
    }
  }

  async function loadAdminSystemTasks(apiClient) {
    const tbody = document.getElementById("adminSystemTasksTableBody");
    if (!tbody) return;
    tbody.innerHTML = `<tr><td colspan="7" style="text-align: center; color: var(--text-muted); padding: 16px;">Đang tải tác vụ...</td></tr>`;
    try {
      const tasks = await apiClient.getAdminAllTasks();
      if (!tasks || tasks.length === 0) {
        tbody.innerHTML = `<tr><td colspan="7" style="text-align: center; color: var(--text-muted); padding: 16px;">Chưa có tác vụ nào trong hệ thống.</td></tr>`;
        return;
      }
      tbody.innerHTML = tasks.map(t => {
        const timeStr = t.createdAt ? new Date(t.createdAt).toLocaleString('vi-VN') : '-';
        return `
          <tr>
            <td style="font-family: var(--font-mono); font-weight: 600;">#${t.id}</td>
            <td><strong>${escapeHtml(t.ownerUsername || 'User')}</strong></td>
            <td>${escapeHtml(t.projectName || 'Dự án')}</td>
            <td><span class="task-type-pill" style="font-size: 10px;">${escapeHtml(t.type || 'TASK')}</span></td>
            <td><span class="severity-tag ${t.status === 'COMPLETED' ? 'LOW' : 'HIGH'}">${escapeHtml(t.status)}</span></td>
            <td style="font-size: 11px; color: var(--text-muted);">${timeStr}</td>
            <td style="font-size: 11.5px; color: ${t.errorMessage ? 'var(--diff-del)' : 'var(--text-secondary)'};">
              ${escapeHtml(t.errorMessage ? t.errorMessage : 'Hoàn thành tốt')}
            </td>
          </tr>
        `;
      }).join("");
    } catch (err) {
      tbody.innerHTML = `<tr><td colspan="7" style="text-align: center; color: var(--diff-del); padding: 16px;">Lỗi: ${escapeHtml(err.message)}</td></tr>`;
    }
  }

  async function loadAdminAuditLogs(apiClient) {
    const tbody = document.getElementById("adminAuditTableBody");
    if (!tbody) return;
    tbody.innerHTML = `<tr><td colspan="5" style="text-align: center; color: var(--text-muted); padding: 16px;">Đang tải nhật ký kiểm toán...</td></tr>`;
    try {
      const logs = await apiClient.getAdminAuditLogs();
      if (!logs || logs.length === 0) {
        tbody.innerHTML = `<tr><td colspan="5" style="text-align: center; color: var(--text-muted); padding: 16px;">Chưa có nhật ký kiểm toán nào.</td></tr>`;
        return;
      }
      tbody.innerHTML = logs.map(l => {
        const timeStr = l.createdAt ? new Date(l.createdAt).toLocaleString('vi-VN') : '-';
        return `
          <tr>
            <td style="font-family: var(--font-mono); font-size: 11px; color: var(--text-muted);">${timeStr}</td>
            <td><strong>${escapeHtml(l.username || 'System')}</strong></td>
            <td><span style="font-family: var(--font-mono); font-size: 11px; background: var(--bg-subtle); padding: 2px 6px; border-radius: 4px;">${escapeHtml(l.action)}</span></td>
            <td style="font-size: 12px; color: var(--text-secondary);">${escapeHtml(l.details || '-')}</td>
            <td style="font-family: var(--font-mono); font-size: 11px; color: var(--text-muted);">${escapeHtml(l.ipAddress || '127.0.0.1')}</td>
          </tr>
        `;
      }).join("");
    } catch (err) {
      tbody.innerHTML = `<tr><td colspan="5" style="text-align: center; color: var(--diff-del); padding: 16px;">Lỗi: ${escapeHtml(err.message)}</td></tr>`;
    }
  }

  window.AdminView = {
    openAdminPanelModal,
    loadAdminDashboardData,
    openInspectUserModal,
    setupAdminNavigation,
    setupAdminTabs: setupAdminNavigation,
    loadAdminTools,
    loadAdminSettings,
    loadAdminSystemTasks,
    loadAdminAuditLogs
  };
})();
