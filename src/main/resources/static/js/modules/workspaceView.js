/** Workspace presentation. Reads persisted data; navigation never invokes generation APIs. */
(function () {
  const escape = window.MarkdownUtils.escapeHtml;
  const labels = { dashboard: 'Dashboard', projects: 'Projects', chat: 'AI Chat', review: 'Code Review', fix: 'Code Fix', tests: 'Test Generation', history: 'Task History', settings: 'Settings' };
  function output(task) {
    try { return typeof task.output === 'string' ? JSON.parse(task.output) || {} : task.output || {}; }
    catch (_) { return {}; }
  }
  function summarize(projects, tasks) {
    const completed = tasks.filter(t => t.status === 'COMPLETED');
    const recentIssues = completed.flatMap(task => {
      const issues = output(task).issues;
      return Array.isArray(issues) ? issues.filter(i => i && typeof i === 'object').map(issue => ({ ...issue, taskId: task.id, projectName: task.projectName })) : [];
    });
    return { projects: projects.length, tasks: tasks.length, completed: completed.length,
      running: tasks.filter(t => ['PENDING', 'PROCESSING'].includes(t.status)).length,
      failed: tasks.filter(t => ['ERROR','FAILED'].includes(t.status)).length, issues: recentIssues.length, recentIssues };
  }
  function filterTasks(tasks, filters = {}) {
    const query = String(filters.query || '').trim().toLowerCase();
    return tasks.filter(t => (!filters.status || t.status === filters.status)
      && (!filters.type || t.type === filters.type)
      && (!filters.project || String(t.projectId) === String(filters.project))
      && (!query || `${t.id} ${t.projectName || ''} ${t.type || ''}`.toLowerCase().includes(query)));
  }
  function date(value) {
    if (!value || !Number.isFinite(new Date(value).getTime())) return '<span class="muted">—</span>';
    return `<time datetime="${escape(value)}">${escape(new Date(value).toLocaleString('vi-VN'))}</time>`;
  }
  function badge(status) {
    const known = ['COMPLETED', 'ERROR', 'FAILED', 'PENDING', 'PROCESSING', 'CANCELLED'];
    const style = known.includes(status) ? status.toLowerCase() : 'unknown';
    return `<span class="status-indicator-pill ${style}">${escape(status || 'Chưa rõ')}</span>`;
  }
  function taskRows(tasks, projects) {
    if (!tasks.length) return '<tr><td colspan="6"><div class="workspace-empty">Chưa có tác vụ phù hợp. Chọn dự án để bắt đầu một Code Review.</div></td></tr>';
    return tasks.map(task => {
      const project = projects.find(p => String(p.id) === String(task.projectId));
      return `<tr><td><button class="text-button" data-open-report="${escape(task.id)}">#${escape(task.id)}</button><small>${escape(task.type || 'TASK')}</small></td>
        <td>${escape(project?.name || task.projectName || 'Dự án đã xóa')}</td><td>${badge(task.status)}</td>
        <td>${date(task.createdAt)}</td><td>${date(task.completedAt)}</td>
        <td><div class="task-row-actions"><button class="btn-nav-secondary" data-open-report="${escape(task.id)}">Xem chi tiết</button>
        ${['PENDING','PROCESSING'].includes(task.status)?`<button class="text-button" data-cancel-task="${escape(task.id)}">Hủy</button>`:''}
        ${['COMPLETED','ERROR','FAILED','CANCELLED'].includes(task.status)&&task.type==='CODE_REVIEW'?`<button class="text-button" data-retry-task="${escape(task.id)}" title="Gửi lại review bằng một yêu cầu mới">Chạy lại</button>`:''}</div>${task.errorMessage ? `<details class="task-error"><summary>Lỗi tác vụ</summary><p>${escape(task.errorMessage)}</p></details>` : ''}</td></tr>`;
    }).join('');
  }
  function singleFlight() {
    let pending = false;
    return async function (action) {
      if (pending) return;
      pending = true;
      try { return await action(); } finally { pending = false; }
    };
  }
  function table(tasks, projects) {
    return `<div class="table-scroll"><table class="workspace-table"><thead><tr><th scope="col">Tác vụ / Loại</th><th scope="col">Dự án</th><th scope="col">Trạng thái</th><th scope="col">Tạo lúc</th><th scope="col">Hoàn thành</th><th scope="col">Báo cáo</th></tr></thead><tbody>${taskRows(tasks, projects)}</tbody></table></div>`;
  }
  function heading(title, description, action = '') {
    return `<div class="page-heading"><div><h1>${title}</h1><p>${description}</p></div>${action}</div>`;
  }
  let context;
  let page = 'dashboard';
  let initialized = false;
  const byId = id => document.getElementById(id);
  function dashboard(state) {
    const stats = summarize(state.projects, state.tasks);
    const tasks = [...state.tasks].sort((a,b) => new Date(b.createdAt || 0)-new Date(a.createdAt || 0));
    return `${heading('Không gian phát triển của bạn', 'Theo dõi dự án, tiến trình phân tích và các báo cáo đã lưu.', '<button class="btn-nav-action" data-action="review">Tạo Code Review</button>')}
      <div class="overview-grid">${[['Dự án',stats.projects],['Đang xử lý',stats.running],['Hoàn thành',stats.completed],['Thất bại',stats.failed]].map(([label,value])=>`<div class="overview-item"><span>${label}</span><strong>${value}</strong></div>`).join('')}</div>
      <div class="section-title-row"><h2>Tác vụ gần đây</h2><button class="text-button" data-page="history">Xem lịch sử</button></div>${table(tasks.slice(0,5),state.projects)}
      <div class="section-title-row"><h2>Vấn đề trong báo cáo hoàn thành</h2><span class="count-badge">${stats.issues}</span></div>
      <div class="issue-feed">${stats.recentIssues.slice(0,6).map(issue=>`<button class="issue-feed-row" data-open-report="${escape(issue.taskId)}"><span class="severity-badge ${['HIGH','CRITICAL'].includes(issue.severity)?'HIGH':'MEDIUM'}">${escape(issue.severity || 'INFO')}</span><span>${escape(issue.title || issue.description || 'Xem vấn đề')}<small>${escape(issue.file || issue.category || `Task #${issue.taskId}`)}</small></span></button>`).join('') || '<div class="workspace-empty">Chưa có vấn đề được ghi nhận trong báo cáo hoàn thành.</div>'}</div>
      <p class="workspace-note">Số liệu lấy từ dữ liệu tác vụ đã tải. Backend chưa cung cấp thống kê token hoặc chi phí LLM.</p>`;
  }
  function renderHistory() {
    if (!context) return;
    const filters = { query: byId('historySearch')?.value, status: byId('historyStatus')?.value,
      type: byId('historyType')?.value, project: byId('historyProject')?.value };
    byId('historyResults').innerHTML = table(filterTasks(context.state.tasks,filters),context.state.projects);
  }
  function renderSettings(state) {
    const selected = state.theme;
    return `${heading('Settings', 'Tài khoản, giao diện và khả năng hiện có của workspace.')}
      <div class="settings-section"><h2>Giao diện</h2><p>Theme được lưu trên trình duyệt này.</p><div class="segmented-control"><button class="btn-nav-secondary" data-theme-choice="dark" aria-pressed="${selected==='dark'}">Dark</button><button class="btn-nav-secondary" data-theme-choice="light" aria-pressed="${selected==='light'}">Light</button></div></div>
      <div class="settings-section"><h2>Tài khoản</h2><p>${escape(state.currentUser?.username)} · ${escape(state.currentUser?.role)}</p><button class="btn-nav-secondary" data-action="account">Quản lý tài khoản</button> <button class="btn-nav-secondary" data-action="preferences">Tùy chọn cá nhân</button></div>
      <div class="settings-section"><h2>Skill catalog</h2><p>Metadata từ server. Skill chỉ được áp dụng khi bạn chọn và gửi review.</p><div id="settingsSkills" aria-live="polite">${skillMetadata()}</div></div>
      <div class="settings-section"><h2>Model, tools và giới hạn</h2><p>Model hợp lệ được tải từ cấu hình server. Giới hạn runtime tiếp tục do backend quản lý; hiện chưa có API chỉnh budget cho người dùng.</p>${state.currentUser?.role==='ADMIN'?'<button class="btn-nav-secondary" data-action="admin">Quản trị model và tools</button>':'<p class="muted">Cấu hình hệ thống dành cho quản trị viên.</p>'}</div>`;
  }
  function skillMetadata() {
    const skills = window.SkillView?.getCatalog?.();
    if (!skills) return '<p class="muted">Catalog chưa tải được. Làm mới dữ liệu để thử lại.</p>';
    return skills.map(s=>`<div class="catalog-row"><strong>${escape(s.id)} <span class="muted">v${escape(s.version)}</span></strong><p>${escape(s.description)}</p><small>${escape((s.languages || []).join(', '))}</small></div>`).join('') || '<p>Chưa có skill nội bộ.</p>';
  }
  function refresh() {
    if (!context) return;
    const {state} = context;
    const d=byId('workspaceDashboard'); if(d) d.innerHTML=dashboard(state);
    const review=byId('workspaceReview');
    if(review) review.innerHTML=`${heading('Code Review', 'Đánh giá mã nguồn với trọng tâm, tools và skill do bạn lựa chọn.', '<button class="btn-nav-action" data-action="review">Cấu hình review</button>')}
      <div class="review-intro"><div class="review-step"><span>1</span><div><h2>Chọn ngữ cảnh</h2><p>Dự án, trọng tâm và model được cấu hình.</p></div></div><div class="review-step"><span>2</span><div><h2>Chọn skill phù hợp</h2><p>Không chọn skill sẽ giữ review legacy. Có thể kết hợp nhiều skill trong budget.</p></div></div><div class="review-step"><span>3</span><div><h2>Đọc báo cáo</h2><p>Issues, mức độ, đề xuất và appliedSkills do server xác nhận.</p></div></div></div>
      <div class="section-title-row"><h2>Các phiên review</h2></div>${table(state.tasks.filter(t=>t.type==='CODE_REVIEW'),state.projects)}`;
    const history=byId('historyProject');
    if(history) {
      const value=history.value;
      history.innerHTML='<option value="">Mọi dự án</option>'+state.projects.map(p=>`<option value="${escape(p.id)}">${escape(p.name)}</option>`).join('');
      history.value=value;
    }
    renderHistory();
    const settings=byId('workspaceSettings');if(settings) settings.innerHTML=renderSettings(state);
    const status=byId('workspaceTaskStatus'); if(status) {const running=summarize(state.projects,state.tasks).running;status.textContent=running?`${running} tác vụ đang xử lý`:'Không có tác vụ đang chạy';}
    const projectLanguage=byId('projectLanguage');
    if(projectLanguage){
      const selected=projectLanguage.value;
      projectLanguage.innerHTML='<option value="">Mọi ngôn ngữ</option>'+[...new Set(state.projects.map(p=>p.language).filter(Boolean))].map(language=>`<option value="${escape(language)}">${escape(language)}</option>`).join('');
      projectLanguage.value=selected;
    }
    filterProjects();
  }
  function filterProjects() {
    const query=(byId('projectSearch')?.value || '').trim().toLowerCase();
    const language=byId('projectLanguage')?.value || '';
    let visible=0;
    document.querySelectorAll('[data-welcome-proj-id]').forEach(card=>{
      const project=context.state.projects.find(p=>String(p.id)===card.dataset.welcomeProjId);
      const match=project && `${project.name} ${project.path || ''} ${project.description || ''}`.toLowerCase().includes(query) && (!language || project.language===language);
      card.hidden=!match;if(match)visible++;
    });
    const empty=byId('projectSearchEmpty');if(empty) empty.hidden=!context.state.projects.length || visible>0;
  }
  function navigate(next) {
    if (!context.state.currentUser) return;
    page = labels[next] ? next : 'dashboard';
    if (page==='chat') {context.state.view='chat';context.renderView();}
    else {context.state.view='welcome';context.renderView();}
    window.ChatView?.onViewChanged?.(context.state, window.apiClient);
    document.body.classList.remove('sidebar-mobile-open');
    byId('workspaceTitle')?.focus();
  }
  function sync() {
    if(!context)return;
    const {state}=context;
    document.body.classList.toggle('authenticated',!!state.currentUser);
    if(!state.currentUser) {
      setDataState('ready');
      document.body.classList.remove('sidebar-mobile-open');
      document.querySelectorAll('.workspace-page').forEach(node=>node.hidden=true);
      byId('workspaceNav').hidden=true;
      byId('workspaceTitle').textContent='Developer workspace';
      return;
    }
    byId('workspaceNav').hidden=false;
    if(state.view==='chat')page='chat';
    else if(page==='chat')page='projects';
    document.querySelectorAll('.workspace-page').forEach(node=>node.hidden=node.dataset.workspacePage!==page);
    byId('welcomeView').style.display=page==='projects'?'flex':'none';
    byId('chatAssistantView').style.display=page==='chat'?'flex':'none';
    byId('workspaceTitle').textContent=labels[page];
    byId('navActiveProjectText').textContent=state.activeProject?.name || 'Chưa chọn dự án';
    document.querySelectorAll('[data-nav-page]').forEach(button=>{
      const active=button.dataset.navPage===page;
      button.classList.toggle('active',active);
      if(active)button.setAttribute('aria-current','page');else button.removeAttribute('aria-current');
    });
    document.body.classList.toggle('sidebar-mini',localStorage.getItem('ai_agent_sidebar')==='collapsed');
    refresh();
  }
  function setDataState(kind, message='') {
    const banner=byId('workspaceDataBanner');if(!banner)return;
    const main=byId('appMain');main?.setAttribute('aria-busy',String(kind==='loading'));
    banner.hidden=kind==='ready';
    banner.className='workspace-data-banner '+kind;
    banner.innerHTML=kind==='loading'?'<span class="loading-spinner" aria-hidden="true"></span>Đang tải dữ liệu workspace…':`<span>${escape(message)}</span><button class="btn-nav-secondary" data-action="refresh">Thử tải lại</button>`;
  }
  function notify(message, error=false) {
    const area=byId('workspaceToasts'); if(!area)return;
    const node=document.createElement('div');node.className=`workspace-toast ${error?'is-error':''}`;
    node.setAttribute('role',error?'alert':'status');node.textContent=message;
    area.appendChild(node);setTimeout(()=>node.remove(),6000);
  }
  function formError(form,message) {
    let error=form.querySelector('.form-error');
    if(!error){error=document.createElement('p');error.className='form-error';error.setAttribute('role','alert');form.prepend(error);}
    error.textContent=message;error.tabIndex=-1;error.focus();
  }
  const taskActions = new Set();
  async function runTaskAction(button, action, id) {
    if(taskActions.has(id))return;
    taskActions.add(id);button.disabled=true;
    try {
      if(action==='retry')await context.api.retryTask(id);else await context.api.cancelTask(id);
      await context.reload();
      notify(action==='retry'?'Đã gửi lại review.':'Đã yêu cầu hủy tác vụ.');
    } catch(error){notify(error.message + ' Không tự thử lại.',true);}
    finally{taskActions.delete(id);button.disabled=false;}
  }
  function enhanceControls() {
    document.querySelectorAll('.project-tree-header,.project-session-row,.sidebar-conv-item,.project-expand-arrow').forEach(node=>{node.setAttribute('role','button');node.tabIndex=0;});
  }
  function mount(options) {
    context=options;if(initialized)return;initialized=true;
    document.addEventListener('click',event=>{
      const taskAction=event.target.closest('[data-cancel-task],[data-retry-task]');
      if(taskAction){const retry=taskAction.hasAttribute('data-retry-task');runTaskAction(taskAction,retry?'retry':'cancel',taskAction.dataset.retryTask || taskAction.dataset.cancelTask);}
      const nav=event.target.closest('[data-nav-page], [data-page]');
      if(nav)navigate(nav.dataset.navPage || nav.dataset.page);
      const report=event.target.closest('[data-open-report]');
      if(report){const task=context.state.tasks.find(t=>String(t.id)===report.dataset.openReport);if(task)context.openTask(task);}
      const action=event.target.closest('[data-action]')?.dataset.action;
      const actions={review:'btnTopNewTask',account:'userMenuDropdown',admin:'btnTopAdmin',preferences:'btnAccPreferences',project:'btnTopOpenProject',refresh:'btnRefreshTasks'};
      if(actions[action]) byId(actions[action])?.click();
      const theme=event.target.closest('[data-theme-choice]');if(theme){context.applyTheme(theme.dataset.themeChoice);refresh();}
      const projectReview=event.target.closest('[data-project-review]');
      if(projectReview){context.openReview(Number(projectReview.dataset.projectReview));}
    });
    ['historySearch','historyStatus','historyType','historyProject'].forEach(id=>byId(id)?.addEventListener('input',renderHistory));
    ['projectSearch','projectLanguage'].forEach(id=>byId(id)?.addEventListener('input',filterProjects));
    document.addEventListener('keydown',event=>{
      if(document.body.classList.contains('sidebar-mobile-open')){
        const toggle=byId('btnToggleSidebar');
        if(event.key==='Escape'){document.body.classList.remove('sidebar-mobile-open');toggle.setAttribute('aria-expanded','false');toggle.focus();}
        if(event.key==='Tab'){
          const controls=Array.from(byId('appSidebar').querySelectorAll('button,[tabindex="0"]')).filter(node=>node.getClientRects().length);
          const first=controls[0],last=controls.at(-1);
          if(event.shiftKey&&document.activeElement===first){event.preventDefault();last?.focus();}
          else if(!event.shiftKey&&document.activeElement===last){event.preventDefault();first?.focus();}
        }
      }
      const target=event.target.closest('[role="button"]');
      if(target && target===event.target && (event.key==='Enter'||event.key===' ')){event.preventDefault();target.click();}
    });
    byId('sidebarScrim')?.addEventListener('click',()=>{document.body.classList.remove('sidebar-mobile-open');byId('btnToggleSidebar').setAttribute('aria-expanded','false');byId('btnToggleSidebar').focus();});
    // Preserve existing handlers while upgrading their custom controls for keyboard access.
    document.querySelectorAll('.brand-wrapper,.user-badge-dropdown,.user-card-link,.action-card,.db-status-pill,.view-tab-btn:not(button)').forEach(node=>{
      node.setAttribute('role','button');node.tabIndex=0;
    });
    document.querySelectorAll('label.form-label-text').forEach(label=>{
      const field=label.parentElement.querySelector('input:not([type="checkbox"]),select,textarea');
      if(field?.id)label.htmlFor=field.id;
    });
    document.querySelectorAll('button[title]').forEach(button=>{if(!button.hasAttribute('aria-label'))button.setAttribute('aria-label',button.title);});
    document.querySelectorAll('.modal-close-btn').forEach(button=>button.setAttribute('aria-label','Đóng hộp thoại'));
    document.querySelectorAll('.btn-toggle-password').forEach(button=>button.removeAttribute('tabindex'));
    sync();
  }
  window.WorkspaceView={summarize,filterTasks,taskRows,singleFlight,enhanceControls,setDataState,mount,sync,refresh,navigate,notify,formError};
})();
