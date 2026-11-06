/** Review submission is an explicit, single-flight user action. */
(function () {
  let mounted = false;
  function mount({state, el, closeModals, loadDatabaseData, selectTask}) {
    if (mounted) return;
    mounted = true;
    const submitReview = window.WorkspaceView.singleFlight();
    // Submit: Create AI Review Task (Lưu vào CSDL & chạy ngầm)
    if (el.formCreateTask) {
      el.formCreateTask.addEventListener("submit", (e) => {
        e.preventDefault();
        submitReview(async () => {
        const selTaskProj = document.getElementById("selectTaskProject");
        const projectId = selTaskProj ? Number(selTaskProj.value) : null;
        if (!projectId) {
          window.WorkspaceView.formError(el.formCreateTask, "Vui lòng chọn dự án trước.");
          return;
        }
        const type = document.getElementById("selectTaskType") ? document.getElementById("selectTaskType").value : "CODE_REVIEW";
        const focus = document.getElementById("selectTaskFocus") ? document.getElementById("selectTaskFocus").value : "";
        const model = document.getElementById("selectTaskModel") ? document.getElementById("selectTaskModel").value : "";
        const prompt = document.getElementById("inputTaskPrompt") ? document.getElementById("inputTaskPrompt").value.trim() : "";
        const selectedTools = Array.from(document.getElementById("taskToolsContainer")?.querySelectorAll('input[name="taskTools"]:checked') || []).map(cb => cb.value);

        const project = state.projects.find(p => p.id === projectId);
        if (!project) { window.WorkspaceView.formError(el.formCreateTask, 'Dự án không còn khả dụng. Vui lòng làm mới.'); return; }
        const submitButton = document.getElementById('btnSubmitNewTask');
        submitButton.disabled = true;
        submitButton.textContent = 'Đang gửi review…';
        el.formCreateTask.querySelector('.form-error')?.remove();
        const session = window.apiClient.getToken?.();
        const isCurrent = () => session === window.apiClient.getToken?.();
        try {
          const newTask = await window.apiClient.createTask({
            projectId: projectId,
            type: type,
            input: { projectPath: project.path, focus: focus },
            selectedTools: selectedTools,
            model: model || undefined,
            prompt: prompt || undefined,
            skills: window.SkillView.readSelection(document.getElementById("taskSkillsContainer"))
          });

          if (!isCurrent()) return;
          closeModals();
          await loadDatabaseData();
          if (project && isCurrent()) {
            selectTask(newTask, project);
          }
        } catch (err) {
          if (!isCurrent()) return;
          window.WorkspaceView.formError(el.formCreateTask, 'Không tạo được review: ' + err.message + ' Yêu cầu không được tự gửi lại.');
        } finally {
          submitButton.disabled = false;
          submitButton.textContent = 'Bắt đầu review';
        }
        });
      });
    }

  }
  window.TaskForm = {mount};
})();
