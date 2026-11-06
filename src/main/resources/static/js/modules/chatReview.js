/** Conversation-bound review cards and non-overlapping task polling. */
(function () {
  const escape = window.MarkdownUtils.escapeHtml;
  const active = task => ['PENDING', 'PROCESSING', 'RUNNING'].includes(task.status);
  const isReview = message => /^\s*\/review(?:\s|$)/i.test(message || '');
  function outputOf(task) {
    try { const result = typeof task.output === 'string' ? JSON.parse(task.output) : task.output; return result && typeof result === 'object' ? result : {}; }
    catch (_) { return {}; }
  }
  function renderCard(task) {
    const output = outputOf(task), issues = Array.isArray(output.issues) ? output.issues : (Array.isArray(output.findings) ? output.findings : []);
    const metadata = output.reviewMetadata || {};
    let input = task.input || {};
    try { if (typeof input === "string") input = JSON.parse(input); } catch (_) { input = {}; }
    if (!input || typeof input !== 'object') input = {};
    const files = Array.isArray(metadata.filesRead) ? metadata.filesRead.filter(f => f && typeof f === 'object') : [];
    const skills = Array.isArray(output.appliedSkills) ? output.appliedSkills.filter(s => s && typeof s === 'object').map(s => `${escape(s.id)}@${escape(s.version)}`).join(', ') || 'Legacy' : 'Chưa có metadata server';
    const findings = issues.filter(f => f && typeof f === 'object').map((f, i) => `<details class="chat-review-finding" data-severity="${escape(f.severity || '')}" data-category="${escape(f.category || '')}"><summary><strong>${escape(f.severity || 'INFO')}</strong> · ${escape(f.category || '')} · ${escape(f.title || 'Finding ' + (i + 1))}</summary><p>${escape(f.classification || '')} ${escape(f.filePath || f.file || '')}${f.lineStart || f.line ? ':' + escape(f.lineStart || f.line) + (f.lineEnd && f.lineEnd !== f.lineStart ? '-' + escape(f.lineEnd) : '') : ''}</p>${f.evidence ? `<pre>${escape(f.evidence)}</pre>` : ''}<p>${escape(f.explanation || f.description || '')}</p><p><strong>Đề xuất:</strong> ${escape(f.suggestion || f.recommendation || '')}</p><button type="button" data-copy-finding="${i}">Sao chép finding</button></details>`).join('');
    const categories = [...new Set(issues.map(f => f?.category).filter(Boolean))];
    const severities = [...new Set(issues.map(f => f?.severity).filter(Boolean))];
    return `<article class="chat-review-card" data-review-id="${escape(task.id)}"><div class="chat-review-heading"><strong>Code review #${escape(task.id)}</strong><span role="status">${escape(task.status)}</span></div>
      ${input.focus ? `<p>Trọng tâm: ${escape(input.focus)}</p>` : ''}
      ${active(task) ? '<p>Đang theo dõi trạng thái từ server…</p>' : ''}${task.errorMessage ? `<p role="alert">${escape(task.errorMessage)}</p>` : ''}
      ${task.status === 'COMPLETED' ? `<p>${escape(output.summary || '')}</p><p>Skill đã áp dụng: ${skills}</p>${issues.length === 0 ? '<p>Không có finding trong báo cáo đã lưu; điều này không xác nhận dự án an toàn.</p>' : ''}
      ${issues.length ? `<div class="chat-review-filters"><label>Severity <select data-review-filter="severity"><option value="">Tất cả</option>${severities.map(s=>`<option value="${escape(s)}">${escape(s)}</option>`).join('')}</select></label><label>Category <select data-review-filter="category"><option value="">Tất cả</option>${categories.map(s=>`<option value="${escape(s)}">${escape(s)}</option>`).join('')}</select></label></div>${findings}` : ''}
      ${Array.isArray(output.suggestions) && output.suggestions.length ? `<h4>Đề xuất cải thiện</h4><ul>${output.suggestions.map(s=>`<li>${escape(s)}</li>`).join('')}</ul>` : ''}
      ${Array.isArray(output.limitations) && output.limitations.length ? `<h4>Giới hạn review</h4><ul>${output.limitations.map(s=>`<li>${escape(s)}</li>`).join('')}</ul>` : ''}
      <details><summary>Review metadata</summary><p>Model: ${escape(metadata.model || '')} · Model calls: ${escape(metadata.modelCalls ?? '')} · Tool calls: ${escape(metadata.toolCalls ?? '')}</p><ul>${files.map(f=>`<li>${escape(f.filePath)}:${escape(f.lineStart)}-${escape(f.lineEnd)}</li>`).join('')}</ul></details>` : ''}
      <div class="chat-review-actions">${active(task) ? '<button type="button" data-review-action="cancel">Hủy</button>' : ''}${['ERROR','CANCELLED'].includes(task.status) ? '<button type="button" data-review-action="retry">Chạy lại thủ công</button>' : ''}${task.status === 'COMPLETED' ? '<button type="button" data-review-action="open">Mở báo cáo đầy đủ</button><button type="button" data-review-action="follow">Thảo luận báo cáo</button>' : ''}</div></article>`;
  }
  function create({api, state, render, onError = () => {}}) {
    let generation = 0, tasks = [], pending = null, aborter = new AbortController();
    const context = () => ({generation, id: state.activeConversationId, project: state.activeProject?.id, token: api.getToken?.()});
    const current = c => c.generation === generation && c.id === state.activeConversationId && c.project === state.activeProject?.id && c.token === api.getToken?.() && !!state.currentUser;
    const poller = window.TaskPolling.create({shouldPoll: () => !!state.currentUser && state.view === 'chat' && tasks.some(active), poll: load, onError});
    function load() {
      const c = context(); c.signal = aborter.signal; if (!c.id) return Promise.resolve();
      if (pending && pending.generation === c.generation && pending.id === c.id && pending.project === c.project && pending.token === c.token) return pending.promise;
      c.promise = Promise.resolve().then(() => api.getConversationReviews(c.id, {signal: c.signal})).then(result => {
        if (!current(c)) return; tasks = Array.isArray(result) ? result : []; render(tasks); poller.start();
      }).catch(e => { if (current(c)) throw e; }).finally(() => { if (pending === c) pending = null; });
      pending = c; return c.promise;
    }
    function invalidate() { generation++; tasks = []; pending = null; poller.stop(); aborter.abort(); aborter = new AbortController(); }
    return {load, invalidate, getTasks: () => tasks};
  }
  window.ChatReview = {isReview, renderCard, create};
})();
