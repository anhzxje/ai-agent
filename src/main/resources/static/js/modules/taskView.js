/** Task cards, pin interactions and persisted report presentation. */
(function () {
  function create({state, el, openModal, openLoginModal, startNewChatConversation, renderSidebar}) {
    const formatRelativeTime = window.ProjectView.formatRelativeTime;
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
          <p style="font-weight: 600; margin-bottom: 4px;">Chưa có tác vụ AI nào thuộc tài khoản <strong>${window.MarkdownUtils.escapeHtml(state.currentUser.username)}</strong> trong CSDL.</p>
          <p style="font-size: 12px; color: var(--text-muted); margin-bottom: 12px;">
            Mở dự án hoặc tạo phiên chat mới để bắt đầu làm việc cùng HUS AI Agent.
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

    container.innerHTML = state.tasks.map(task => {
      const project = state.projects.find(p => p.id === task.projectId) || { name: task.projectName || "Dự án" };
      return window.SkillView.renderTaskCard(task, project, formatRelativeTime(task.createdAt));
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

          renderSidebar();
          renderWelcomeTasks();
        }
      });
    });
  }

  function selectTask(task, project) {
    if (!task) return;
    state.activeTask = task;
    if (project) state.activeProject = project;
    const title = document.getElementById("taskReportTitle");
    const content = document.getElementById("taskReportContent");
    if (title) title.textContent = `Báo cáo review #${task.id}`;
    if (content) content.innerHTML = window.SkillView.renderTaskReport(task);
    openModal(document.getElementById("modalTaskReport"));
  }
    return {renderWelcomeTasks, selectTask};
  }
  window.TaskView = {create};
})();
