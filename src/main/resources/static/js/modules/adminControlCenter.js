/** Admin-only control center. All overview reads are metadata; no generation endpoint is used. */
(function () {
  const escape = window.MarkdownUtils.escapeHtml;
  const pages = {overview:'Overview',users:'User Management',models:'AI Models & Providers',budget:'API Usage & Budget',skills:'Skill Management',tools:'Tool Management',tasks:'Agent Task Monitoring',audit:'Audit Logs',system:'System Settings'};
  let context = null;
  let page = 'overview';
  let generation = 0;
  let currentData = {};
  let initialized = false;
  let confirmation = null;
  let mutationPending = false;
  const byId = id => document.getElementById(id);

  // Arbitrary backend messages are not an appropriate place to display credentials.
  function safeText(value) {
    const text = String(value ?? '');
    if (/api[\s_-]*key|\b(?:authorization|bearer|password|secret|access_token|refresh_token)\b|\btoken\s*[:=]|AIza[\w-]{16,}|\bsk-[\w-]{12,}|eyJ[\w-]+\.[\w-]+\.[\w-]+/i.test(text)) return '[Nội dung nhạy cảm đã ẩn]';
    return text.slice(0, 2000);
  }
  function text(value) { return escape(safeText(value)); }
  function successRate(stats) {
    if (!stats || !Number.isFinite(stats.completedTasks) || !Number.isFinite(stats.failedTasks)) return 'Chưa có dữ liệu';
    const denominator = stats.completedTasks + stats.failedTasks;
    return denominator > 0 ? `${(100 * stats.completedTasks / denominator).toFixed(1)}%` : 'Chưa có dữ liệu';
  }
  function filterUsers(users, filters = {}) {
    const query = String(filters.query || '').trim().toLowerCase();
    return users.filter(user => (!filters.role || user.role === filters.role)
      && (!filters.status || Boolean(user.isLocked) === (filters.status === 'locked'))
      && (!query || `${user.id} ${user.username} ${user.email || ''}`.toLowerCase().includes(query)));
  }
  function timestamp(value) {
    const parsed = new Date(value);
    return value && Number.isFinite(parsed.getTime()) ? `<time datetime="${escape(value)}">${escape(parsed.toLocaleString('vi-VN'))}</time>` : '—';
  }
  function table(headers, rows, empty) {
    return `<div class="table-scroll"><table class="workspace-table"><thead><tr>${headers.map(label=>`<th scope="col">${label}</th>`).join('')}</tr></thead><tbody>${rows || `<tr><td colspan="${headers.length}"><div class="workspace-empty">${empty}</div></td></tr>`}</tbody></table></div>`;
  }
  function status(value) {
    const style = ['COMPLETED','ERROR','FAILED','PROCESSING','PENDING','CANCELLED'].includes(value) ? value.toLowerCase() : 'unknown';
    return `<span class="status-indicator-pill ${style}">${text(value || 'Chưa rõ')}</span>`;
  }
  function taskRows(tasks) {
    return tasks.map(task=>`<tr><td>#${text(task.id)}<small>${text(task.type)}</small></td><td>${text(task.username || '—')}</td><td>${text(task.projectName || '—')}</td><td>${status(task.status)}</td><td>${timestamp(task.createdAt)}</td><td>${task.errorMessage ? `<details class="admin-task-error"><summary>Xem lỗi</summary><p>${text(task.errorMessage)}</p></details>` : '<span class="muted">Không ghi nhận lỗi</span>'}</td></tr>`).join('');
  }
  function taskTable(tasks) {
    return table(['Tác vụ','Người dùng','Dự án','Trạng thái','Tạo lúc','Lỗi'],taskRows(tasks),'Chưa có tác vụ trong trang dữ liệu này.');
  }
  function skillRows(skills) {
    return skills.map(skill=>`<tr><td><strong>${text(skill.id)}</strong><small>v${text(skill.version)}</small></td><td>${text(skill.description)}</td><td>${text((skill.languages || []).join(', '))}</td><td>${text((skill.conflictsWith || []).join(', ') || 'Không khai báo')}</td></tr>`).join('');
  }
  function metric(label, value, help = '') {
    return `<div class="overview-item"><span>${label}</span><strong>${text(value ?? 'Chưa có dữ liệu')}</strong>${help ? `<small>${help}</small>` : ''}</div>`;
  }
  function auditRows(logs) {
    return logs.map(log=>`<tr><td>${timestamp(log.createdAt)}</td><td>${text(log.username || '—')}</td><td>${text(log.action)}</td><td>${text(log.details || '—')}</td></tr>`).join('');
  }
  function overview(data) {
    const stats = data.stats || {};
    const tasks = Array.isArray(data.tasks) ? data.tasks : [];
    const logs = Array.isArray(data.logs) ? data.logs : [];
    const quotaErrors = tasks.filter(task=>/\b429\b|quota|rate.?limit|resource_exhausted/i.test(task.errorMessage || '')).length;
    return `<div class="overview-grid admin-overview-grid">${metric('Total Users',stats.totalUsers)}${metric('Total Tasks',stats.totalTasks)}${metric('Running / Pending',stats.pendingOrProcessingTasks)}${metric('Failed Tasks',stats.failedTasks)}${metric('Task Success Rate',successRate(stats),'Completed / (Completed + Failed)')}${metric('Active Model',data.settings?.activeModel || 'Chưa có dữ liệu')}</div>
      <div class="admin-observability"><div><h3>LLM Request Usage</h3><p>Chưa có API thống kê số request/token của provider. Không suy ra usage từ số task.</p></div><div><h3>Quota / Rate Limit Errors</h3><p><strong>${Array.isArray(data.tasks)?quotaErrors:'Chưa có dữ liệu'}</strong> tác vụ có dấu hiệu 429/quota trong trang tác vụ đã tải (${tasks.length} tác vụ). Đây không phải tổng quota của provider.</p></div></div>
      <div class="section-title-row"><h2>Recent Agent Tasks</h2><button class="text-button" data-admin-page="tasks">Xem giám sát</button></div>${Array.isArray(data.tasks)?taskTable(tasks.slice(0,5)):'<p class="admin-notice">Không tải được trang tác vụ.</p>'}
      <div class="section-title-row"><h2>System Activity</h2></div>${table(['Thời gian','Người dùng','Hành động','Chi tiết'],auditRows(logs.slice(0,5)),Array.isArray(data.logs)?'Chưa có hoạt động kiểm toán được trả về.':'Không tải được nhật ký kiểm toán.')}`;
  }
  function userRows(users, currentUser) {
    return users.map(user=>{
      const self = String(user.id) === String(currentUser.id) || user.username === currentUser.username;
      return `<tr><td><strong>${text(user.username)}</strong><small>#${text(user.id)} · ${text(user.email || '—')}</small></td><td>${text(user.role)}</td><td><span class="status-indicator-pill ${user.isLocked?'error':'completed'}">${user.isLocked?'Đã khóa':'Hoạt động'}</span></td><td>${text(user.projectCount ?? '—')} / ${text(user.taskCount ?? '—')}</td><td>${timestamp(user.createdAt)}</td><td><div class="task-row-actions"><button class="btn-nav-secondary" data-admin-inspect="${escape(user.id)}">Kiểm duyệt</button><button class="text-button" data-admin-user-lock="${escape(user.id)}" ${self?'disabled title="Không thể thay đổi tài khoản hiện tại"':''}>${user.isLocked?'Mở khóa':'Khóa'}</button><button class="text-button" data-admin-user-role="${escape(user.id)}" ${self?'disabled':''}>${user.role==='ADMIN'?'Giáng USER':'Nâng ADMIN'}</button><button class="text-button admin-danger" data-admin-user-delete="${escape(user.id)}" ${self?'disabled':''}>Xóa</button></div></td></tr>`;
    }).join('');
  }
  function usersPage(users) {
    return `<div class="filter-bar admin-user-filters"><label>Tìm người dùng<input id="adminUserSearch" class="form-input-control" type="search" placeholder="ID, tên hoặc email"></label><label>Vai trò<select id="adminUserRole" class="form-select-control"><option value="">Mọi vai trò</option><option>ADMIN</option><option>USER</option></select></label><label>Trạng thái<select id="adminUserStatus" class="form-select-control"><option value="">Mọi trạng thái</option><option value="active">Hoạt động</option><option value="locked">Đã khóa</option></select></label></div><div id="adminUserResults">${usersTable(users)}</div>`;
  }
  function usersTable(users) {
    return table(['Người dùng','Vai trò','Trạng thái','Dự án / Task','Tạo lúc','Thao tác'],userRows(users,context.state.currentUser),'Không có người dùng phù hợp.');
  }
  function modelsPage(settings) {
    const allowed = Array.isArray(settings.allowedModels) ? settings.allowedModels : [];
    return `<div class="admin-provider-header"><span class="coming-soon-badge">Google Gemini</span><p>Provider của tích hợp Spring AI hiện tại. Chưa có API quản lý nhiều provider.</p></div>
      <form id="adminCenterModelForm" class="settings-section"><h2>Model và allowlist</h2><p>Thay đổi cấu hình không gửi yêu cầu tới model. Model mới chỉ được dùng khi người dùng chủ động gửi nội dung.</p><label class="form-label-text" for="adminModelInput">Active model</label><input id="adminModelInput" class="form-input-control" value="${escape(settings.activeModel || '')}" required autocomplete="off"><label class="form-label-text" for="adminAllowlistInput">Model allowlist · mỗi dòng một model</label><textarea id="adminAllowlistInput" class="form-textarea-control" rows="5" required>${escape(allowed.join('\n'))}</textarea><button class="btn-nav-action" type="submit">Xem lại thay đổi</button></form>
      <form id="adminCenterKeyForm" class="settings-section"><h2>Thông tin xác thực provider</h2><p>${settings.hasCustomApiKey?'Có cấu hình API key tùy chỉnh.':'Trạng thái từ server: chỉ nhận metadata đã che; không hiển thị giá trị key.'} Key cũ không được điền lại vào form.</p><label class="form-label-text" for="adminKeyInput">API key mới</label><input id="adminKeyInput" type="password" class="form-input-control" autocomplete="new-password" required spellcheck="false"><button class="btn-nav-secondary" type="submit">Xác nhận cập nhật key</button><p class="workspace-note">Chỉ lưu cấu hình; không gọi provider để kiểm tra key.</p></form>`;
  }
  function budgetPage() {
    const fields=['Model calls / review','Tool calls / review','Skills / review','Prompt budget (ký tự)','Skill budget (ký tự)','Context budget (ký tự)','Concurrency limit'];
    return `<div class="admin-observability"><div><h2>Internal application budget</h2><p>Giới hạn an toàn do ứng dụng Spring AI thực thi. Chưa có API đọc hoặc chỉnh sửa budget runtime; không hiển thị giá trị mặc định trong mã nguồn như cấu hình đang chạy.</p></div><div><h2>Quota thật của provider</h2><p>Request/token theo thời gian, rate limit và quota còn lại do provider quyết định. Backend chưa cung cấp telemetry này; giới hạn nội bộ không xác nhận request sẽ được provider chấp nhận.</p></div></div>
      ${table(['Giới hạn nội bộ','Cấu hình runtime','Khả năng quản lý'],fields.map(field=>`<tr><td>${field}</td><td>Chưa có API đọc</td><td><span class="muted">Chưa hỗ trợ chỉnh sửa</span></td></tr>`).join(''),'')}
      <div class="settings-section"><h2>HTTP 429 / Quota exhaustion</h2><p>Đọc lỗi đã lưu tại Agent Task Monitoring. Không tự retry, đổi model/provider hoặc tăng budget khi gặp quota. Một task có thể chứa nhiều model calls; số task không phải số request LLM.</p><button class="btn-nav-secondary" data-admin-page="tasks">Xem lỗi tác vụ</button></div>`;
  }
  function toolsPage(tools) {
    return `<p class="workspace-note">Cấu hình này điều khiển công cụ mà tác vụ được phép sử dụng. Bật/tắt không tự thực thi công cụ.</p><div class="admin-tool-list">${tools.map(tool=>`<div class="admin-tool-row"><div><h2>${text(tool.displayName || tool.toolName)}</h2><code>${text(tool.toolName)}</code><p>${text(tool.description)}</p><small>${text(tool.category || '')}</small></div><button class="btn-nav-secondary" data-admin-tool="${escape(tool.toolName)}" aria-pressed="${Boolean(tool.enabled)}">${tool.enabled?'Đang bật · Tắt':'Đang tắt · Bật'}</button></div>`).join('') || '<div class="workspace-empty">Chưa có công cụ được cấu hình.</div>'}</div>`;
  }
  function tasksPage(tasks) {
    return `<p class="workspace-note">Trang dữ liệu server trả về tối đa 100 tác vụ gần đây. Lọc trên trang đã tải; không suy diễn thành thống kê toàn bộ hệ thống.</p><div class="filter-bar"><label>Tìm task<input id="adminTaskSearch" class="form-input-control" type="search" placeholder="ID, người dùng, dự án"></label><label>Trạng thái<select id="adminTaskStatus" class="form-select-control"><option value="">Mọi trạng thái</option>${['PENDING','PROCESSING','COMPLETED','ERROR','CANCELLED'].map(s=>`<option>${s}</option>`).join('')}</select></label></div><div id="adminTaskResults">${taskTable(tasks)}</div>`;
  }
  function systemPage() {
    return `<div class="settings-section"><h2>System Settings</h2><p>Quản trị model, allowlist và key tại AI Models & Providers; quyền tools tại Tool Management. Đây là các thao tác đã có API.</p><button class="btn-nav-secondary" data-admin-page="models">Model configuration</button> <button class="btn-nav-secondary" data-admin-page="tools">Tool configuration</button></div><div class="settings-section"><h2>Cấu hình chưa có endpoint</h2><p>Runtime budgets, concurrency và cấu hình đa provider chưa có API đọc/sửa. Không có thao tác lưu cho các mục này.</p><button class="text-button" data-admin-page="budget">Xem khả năng budget</button></div><div class="settings-section"><h2>Giao diện và quyền truy cập</h2><p>Dùng chung theme Graphite Blue với Developer Workspace. Backend kiểm tra ROLE_ADMIN cho toàn bộ /api/admin; việc ẩn navigation không thay thế kiểm tra quyền.</p></div>`;
  }
  function isAdmin() {
    return context?.state.currentUser?.role==='ADMIN' && context.api.getCurrentUser()?.role==='ADMIN';
  }
  function authorized(token, requestGeneration) {
    return isAdmin() && token===context.api.getToken() && requestGeneration===generation && context.el.modalAdminPanel.classList.contains('active');
  }
  async function readPage(next) {
    const api=context.api;
    if(next==='overview'){
      const results=await Promise.allSettled(['stats','tasks','settings','audit-logs'].map(resource=>api.getAdminControlData(resource)));
      return Object.fromEntries(['stats','tasks','settings','logs'].map((key,index)=>[key,results[index].status==='fulfilled'?results[index].value:null]).concat([['partial',results.some(r=>r.status==='rejected')],['denied',results.some(r=>r.status==='rejected'&&[401,403].includes(r.reason.status))]]));
    }
    if(next==='users')return {users:await api.getAdminControlData('users')};
    if(next==='models')return {settings:await api.getAdminControlData('settings')};
    if(next==='skills')return {skills:await api.getSkills()};
    if(next==='tools')return {tools:await api.getAdminControlData('tools')};
    if(next==='tasks')return {tasks:await api.getAdminControlData('tasks')};
    if(next==='audit')return {logs:await api.getAdminControlData('audit-logs')};
    return {};
  }
  async function navigate(next) {
    if(!isAdmin())return;
    page=Object.hasOwn(pages,next)?next:'overview';
    const requestGeneration=++generation,token=context.api.getToken();
    byId('adminControlTitle').textContent=pages[page];
    document.querySelectorAll('[data-admin-nav]').forEach(button=>{
      const active=button.dataset.adminNav===page;
      button.classList.toggle('active',active);
      if(active){
        button.setAttribute('aria-current','page');
        if(window.matchMedia('(max-width: 800px)').matches)button.scrollIntoView({block:'nearest',inline:'nearest'});
      }else button.removeAttribute('aria-current');
    });
    const content=byId('adminControlContent');content.setAttribute('aria-busy','true');
    content.innerHTML='<div class="admin-loading" role="status"><span class="loading-spinner" aria-hidden="true"></span>Đang tải dữ liệu quản trị…</div>';
    byId('adminControlStatus').textContent='Đang tải';
    try {
      const data=await readPage(page);
      if(!authorized(token,requestGeneration))return;
      if(data.denied){const error=new Error('Access denied');error.status=403;throw error;}
      currentData=data;
      const renders={overview:()=>overview(data),users:()=>usersPage(data.users),models:()=>modelsPage(data.settings),budget:budgetPage,skills:()=>`<p class="workspace-note">Catalog nội bộ từ server, chỉ đọc. Chưa có API sửa nội dung skill.</p>${table(['Skill / Version','Description','Applicable languages','Conflicts'],skillRows(data.skills),'Chưa có skill trong catalog.')}`,tools:()=>toolsPage(data.tools),tasks:()=>tasksPage(data.tasks),audit:()=>`<label class="admin-audit-filter">Tìm audit log<input id="adminAuditSearch" type="search" class="form-input-control" placeholder="Người dùng hoặc hành động"></label><div id="adminAuditResults">${table(['Thời gian','Người dùng','Hành động','Chi tiết'],auditRows(data.logs),'Chưa có nhật ký kiểm toán.')}</div>`,system:systemPage};
      content.innerHTML=(data.partial?'<p class="admin-notice" role="status">Một số nguồn chưa tải được. Số liệu thiếu được ghi rõ, không thay bằng 0.</p>':'')+renders[page]();
      byId('adminControlStatus').textContent=data.partial?'Dữ liệu chưa đầy đủ':'Đã tải dữ liệu';
    } catch(error) {
      if(!authorized(token,requestGeneration))return;
      currentData={};
      const denied=[401,403].includes(error.status);
      content.innerHTML=`<div class="workspace-empty" role="alert"><h2>${denied?'Không có quyền truy cập':'Không tải được dữ liệu'}</h2><p>${denied?'Phiên đăng nhập không có quyền ADMIN hoặc đã hết hạn.':'Hãy kiểm tra kết nối và tải lại. Không có yêu cầu model nào được gửi.'}</p>${denied?'':'<button class="btn-nav-secondary" data-admin-refresh>Thử lại</button>'}</div>`;
      byId('adminControlStatus').textContent=denied?'Truy cập bị từ chối':'Lỗi tải dữ liệu';
    } finally {
      if(requestGeneration===generation)content.setAttribute('aria-busy','false');
    }
  }
  function confirmAction(title,message) {
    if(confirmation)return Promise.resolve(false);
    byId('adminConfirmTitle').textContent=title;
    byId('adminConfirmMessage').textContent=safeText(message);
    byId('modalAdminConfirm').classList.add('active');
    return new Promise(resolve=>{confirmation=resolve;});
  }
  function finishConfirmation(accepted) {
    if(byId('modalAdminConfirm').classList.contains('active'))byId('modalAdminConfirm').classList.remove('active');
    const resolve=confirmation;confirmation=null;resolve?.(accepted);
  }
  async function mutate(title,message,action) {
    if(mutationPending||!isAdmin())return;
    mutationPending=true;
    const token=context.api.getToken(),mutationGeneration=generation;
    try {
      if(!await confirmAction(title,message))return;
      if(!authorized(token,mutationGeneration))return;
      byId('adminControlStatus').textContent='Đang lưu thay đổi';
      await action();
      if(!authorized(token,mutationGeneration))return;
      if(!Array.from(document.querySelectorAll('.workspace-toast')).some(node=>node.textContent==='Đã lưu thay đổi quản trị.'))window.WorkspaceView.notify('Đã lưu thay đổi quản trị.');
      await navigate(page);
    } catch(error) {
      // Never echo raw provider/backend error bodies, which may contain submitted credentials.
      if(authorized(token,mutationGeneration))window.WorkspaceView.notify('Không lưu được thay đổi. Kiểm tra quyền hoặc kết nối; không tự thử lại.',true);
      byId('adminControlStatus').textContent='Không lưu được thay đổi';
    } finally {mutationPending=false;}
  }
  function filterCurrentPage() {
    if(page==='users'){
      const users=filterUsers(currentData.users || [],{query:byId('adminUserSearch').value,role:byId('adminUserRole').value,status:byId('adminUserStatus').value});
      byId('adminUserResults').innerHTML=usersTable(users);
    } else if(page==='tasks'){
      const query=byId('adminTaskSearch').value.toLowerCase(),selected=byId('adminTaskStatus').value;
      const tasks=(currentData.tasks || []).filter(t=>(!selected||t.status===selected)&&`${t.id} ${t.username || ''} ${t.projectName || ''}`.toLowerCase().includes(query));
      byId('adminTaskResults').innerHTML=taskTable(tasks);
    } else if(page==='audit'){
      const query=byId('adminAuditSearch').value.toLowerCase();
      const logs=(currentData.logs || []).filter(log=>`${log.username || ''} ${log.action || ''}`.toLowerCase().includes(query));
      byId('adminAuditResults').innerHTML=table(['Thời gian','Người dùng','Hành động','Chi tiết'],auditRows(logs),'Không có nhật ký phù hợp.');
    }
  }
  function handleClick(event) {
    const nav=event.target.closest('[data-admin-nav],[data-admin-page]');
    if(nav){navigate(nav.dataset.adminNav || nav.dataset.adminPage);return;}
    if(event.target.closest('[data-admin-refresh]')){navigate(page);return;}
    const inspect=event.target.closest('[data-admin-inspect]');
    if(inspect){const user=currentData.users?.find(u=>String(u.id)===inspect.dataset.adminInspect);if(user)window.AdminView.openInspectUserModal(user,context.el,context.api,context.openModal);return;}
    const userAction=event.target.closest('[data-admin-user-lock],[data-admin-user-role],[data-admin-user-delete]');
    if(userAction){
      const id=userAction.dataset.adminUserLock || userAction.dataset.adminUserRole || userAction.dataset.adminUserDelete;
      const user=currentData.users?.find(u=>String(u.id)===id);if(!user||userAction.disabled)return;
      if(userAction.hasAttribute('data-admin-user-delete'))mutate('Xóa người dùng',`Xóa tài khoản ${safeText(user.username)} và dữ liệu phụ thuộc? Không thể hoàn tác.`,()=>context.api.deleteUser(user.id));
      else if(userAction.hasAttribute('data-admin-user-role')){const role=user.role==='ADMIN'?'USER':'ADMIN';mutate('Thay đổi quyền',`Đổi vai trò ${safeText(user.username)} sang ${role}?`,()=>context.api.updateUserRole(user.id,role));}
      else mutate(user.isLocked?'Mở khóa tài khoản':'Khóa tài khoản',`Thay đổi trạng thái truy cập của ${safeText(user.username)}?`,()=>context.api.updateUserStatus(user.id,!user.isLocked));
      return;
    }
    const toolButton=event.target.closest('[data-admin-tool]');
    if(toolButton){const tool=currentData.tools?.find(t=>t.toolName===toolButton.dataset.adminTool);if(tool)mutate('Thay đổi quyền công cụ',`${tool.enabled?'Tắt':'Bật'} công cụ ${safeText(tool.displayName || tool.toolName)} cho các tác vụ?`,()=>context.api.updateToolStatus(tool.toolName,!tool.enabled));}
  }
  function handleSubmit(event) {
    if(!['adminCenterModelForm','adminCenterKeyForm'].includes(event.target.id))return;
    event.preventDefault();
    if(mutationPending)return;
    if(event.target.id==='adminCenterKeyForm'){
      const input=byId('adminKeyInput');let key=input.value.trim();if(!key)return;
      // Clear the sensitive form field immediately; confirmation never interpolates the credential.
      input.value='';
      mutate('Cập nhật API key','Thay thông tin xác thực provider? Chỉ lưu cấu hình, không gọi model để thử key.',async()=>{try{await context.api.updateAdminSettings({apiKey:key});}finally{key='';}}).finally(()=>{key='';});
      return;
    }
    const model=byId('adminModelInput').value.trim();
    const allowedModels=[...new Set(byId('adminAllowlistInput').value.split(/[\n,]+/).map(value=>value.trim()).filter(Boolean))];
    if(!model||!allowedModels.includes(model)){window.WorkspaceView.formError(event.target,'Active model phải nằm trong allowlist không rỗng.');return;}
    mutate('Cập nhật model và allowlist','Áp dụng model mặc định và danh sách được phép? Không có yêu cầu LLM được gửi khi lưu.',()=>context.api.updateAdminSettings({model,allowedModels}));
  }
  function bind() {
    if(initialized)return;initialized=true;
    byId('modalAdminPanel').addEventListener('click',handleClick);
    byId('adminControlContent').addEventListener('input',filterCurrentPage);
    byId('adminControlContent').addEventListener('submit',handleSubmit);
    byId('adminCenterTheme').addEventListener('click',()=>context.el.btnThemeToggle.click());
    byId('adminConfirmAccept').addEventListener('click',()=>finishConfirmation(true));
    byId('adminConfirmCancel').addEventListener('click',()=>finishConfirmation(false));
    new MutationObserver(()=>{if(!byId('modalAdminConfirm').classList.contains('active'))finishConfirmation(false);}).observe(byId('modalAdminConfirm'),{attributes:true,attributeFilter:['class']});
    new MutationObserver(()=>{
      if(!byId('modalAdminPanel').classList.contains('active')){generation++;currentData={};byId('adminControlContent').innerHTML='';finishConfirmation(false);}
    }).observe(byId('modalAdminPanel'),{attributes:true,attributeFilter:['class']});
  }
  async function open(el,state,api,openModal) {
    if(state.currentUser?.role!=='ADMIN'||api.getCurrentUser()?.role!=='ADMIN'){
      window.WorkspaceView.notify('Chỉ ADMIN được truy cập AI Control Center.',true);return;
    }
    context={el,state,api,openModal};bind();
    openModal(el.modalAdminPanel);
    await navigate('overview');
  }
  window.AdminControlCenter={open,navigate,confirmAction,successRate,filterUsers,skillRows,safeText,taskRows,overview};
})();
