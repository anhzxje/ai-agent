/** Internal skill selection and server-owned review provenance. */
(function () {
  const escape = window.MarkdownUtils.escapeHtml;
  let catalog = null;
  async function loadSkills(api, container) {
    if (!container) return;
    if (catalog && container.querySelector('input[name="taskSkills"]')) return;
    try {
      const skills = await api.getSkills();
      catalog = skills;
      container.innerHTML = skills.map(skill => `
        <label class="skill-option">
          <input type="checkbox" name="taskSkills" value="${escape(skill.id)}" data-version="${escape(skill.version)}">
          <span><strong>${escape(skill.id)}</strong> v${escape(skill.version)}
            <small>${escape(skill.description)} · ${escape((skill.languages || []).join(', '))}</small>
          </span>
        </label>`).join('');
    } catch (error) {
      container.innerHTML = `<p role="status">Không tải được danh sách skill. Có thể review legacy. ${escape(error.message)}</p>`;
    }
  }
  function readSelection(container) {
    if (!container) return [];
    return Array.from(container.querySelectorAll('input[name="taskSkills"]:checked'))
      .map(input => ({ id: input.value, version: input.dataset.version }));
  }
  function outputOf(task) {
    try {
      const value = typeof task.output === 'string' ? JSON.parse(task.output) : task.output;
      return value && typeof value === 'object' && !Array.isArray(value) ? value : {};
    }
    catch (_) { return {}; }
  }
  function renderAppliedSkills(task) {
    const skills = outputOf(task).appliedSkills;
    if (!Array.isArray(skills)) return '';
    return `<p class="applied-skills">Skill đã áp dụng: ${skills.length
      ? skills.map(skill => `${escape(skill.id)}@${escape(skill.version)}`).join(', ')
      : 'Legacy (không áp dụng skill)'}</p>`;
  }
  function renderTaskReport(task) {
    const output = outputOf(task);
    const running = ['PENDING', 'PROCESSING'].includes(task.status);
    const sourceIssues = Array.isArray(output.issues) ? output.issues : (Array.isArray(output.findings) ? output.findings : []);
    const issues = sourceIssues.filter(issue => issue && typeof issue === "object");
    const metadata = output.reviewMetadata && typeof output.reviewMetadata === 'object' && !Array.isArray(output.reviewMetadata)
      ? output.reviewMetadata : null;
    const scope = metadata && Array.isArray(metadata.filesRead)
      ? metadata.filesRead.filter(f => f && typeof f === 'object' && !Array.isArray(f)) : [];
    return `<div class="report-metadata"><span>Task #${escape(task.id)} · ${escape(task.type || 'CODE_REVIEW')}</span><span>${escape(task.projectName || '')}</span></div>
      <p class="report-status" role="status">${running ? '<span class="loading-spinner" aria-hidden="true"></span>' : ''}Trạng thái: <strong>${escape(task.status)}</strong></p>
      ${running ? '<p class="muted">Đang theo dõi tiến trình từ server. Đóng báo cáo sẽ không hủy tác vụ.</p>' : ''}
      ${!Array.isArray(output.appliedSkills) ? '<p class="muted">Chưa có metadata skill được server xác nhận cho báo cáo này.</p>' : ''}
      ${renderAppliedSkills(task)}
      ${task.errorMessage ? `<p role="alert">${escape(task.errorMessage)}</p>` : ''}
      ${window.MarkdownUtils.formatMarkdown(output.summary || '')}
      ${!running && task.status === 'COMPLETED' && issues.length === 0 ? '<p>Không có finding trong báo cáo đã lưu. Kết quả này không xác nhận toàn bộ dự án an toàn.</p>' : ''}
      ${window.ChatView.renderReviewFindingsHtml(issues)}
      ${Array.isArray(output.limitations) && output.limitations.length ? `<div role="note"><h3>Giới hạn review</h3><ul>${output.limitations.map(s => `<li>${escape(s)}</li>`).join('')}</ul></div>` : ''}
      ${metadata ? `<details><summary>Phạm vi và budget nội bộ đã sử dụng</summary>
        <p>Model: ${escape(metadata.model || '')} · Model calls: ${escape(metadata.modelCalls)}/${escape(metadata.maxModelCalls)} · Tool calls: ${escape(metadata.toolCalls)}/${escape(metadata.maxToolCalls)}</p>
        <p>Context request lớn nhất: ${escape(metadata.peakRequestChars)}/${escape(metadata.maxContextChars)} ký tự. Đây là budget ứng dụng, không phải quota provider.</p>
        <ul>${scope.map(f => `<li>${escape(f.filePath)}:${escape(f.lineStart)}-${escape(f.lineEnd)}</li>`).join('')}</ul>
        ${scope.length ? '' : '<p>Chưa có dòng mã nguồn được ghi nhận.</p>'}</details>` : ''}
      ${Array.isArray(output.suggestions) && output.suggestions.length ? `<h3>Đề xuất cải thiện</h3><ul>${output.suggestions.map(s => `<li>${escape(s)}</li>`).join('')}</ul>` : ''}`;
  }
  function renderTaskCard(task, project, timeAgo) {
    const output = outputOf(task);
    const issues = output.issues || output.findings || [];
    const running = task.status === 'PENDING' || task.status === 'PROCESSING';
    const count = Array.isArray(issues) ? issues.length : 0;
    return `<div class="welcome-task-card" data-welcome-task-id="${escape(task.id)}">
      <div class="welcome-task-top">
        <div class="welcome-task-id-badge">
          <span class="task-hash">Task #${escape(task.id)}</span>
          <span class="task-type-tag">${escape(task.type || 'TASK')}</span>
        </div>
        <div style="display: flex; align-items: center; gap: 8px;">
          <button class="btn-pin-task-welcome ${task.isPinned ? 'pinned' : ''}" data-pin-task="${escape(task.id)}">
            ${task.isPinned ? 'Bỏ ghim' : 'Ghim'}
          </button>
          <span class="status-indicator-pill ${escape(String(task.status || '').toLowerCase())}">
            ${running ? '<span class="spin-dot"></span>' : ''}${escape(task.status)}
          </span>
        </div>
      </div>
      <div class="welcome-task-project-name" title="${escape(project.name)}">${escape(project.name)}</div>
      ${renderAppliedSkills(task)}
      <div class="welcome-task-meta">
        <span>${task.status === 'COMPLETED' ? `${count} vấn đề phát hiện` : (running ? 'AI đang phân tích...' : 'Xem trạng thái tác vụ')}</span>
        <span>•</span><span>${escape(timeAgo)}</span>
      </div>
      <button class="btn-view-task-details">Xem kết quả chi tiết →</button>
    </div>`;
  }
  window.SkillView = { getCatalog: () => catalog, loadSkills, readSelection, renderAppliedSkills, renderTaskReport, renderTaskCard };
})();
