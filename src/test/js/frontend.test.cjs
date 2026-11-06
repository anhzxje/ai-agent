const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

function loadFrontend() {
  const context = vm.createContext({
    window: { location: { protocol: 'http:', origin: 'http://localhost:9090', port: '9090' } },
    localStorage: { getItem: () => null, setItem() {}, removeItem() {} },
    navigator: {}, console, setTimeout, AbortSignal,
    document: {
      getElementById: () => null,
      querySelectorAll: () => [],
      querySelector: () => null,
      createElement: (tag) => {
        const el = { tag, className: '', innerHTML: '', style: {} };
        el.classList = {
          add: (...classes) => {
            const current = el.className ? el.className.split(/\s+/).filter(Boolean) : [];
            classes.forEach(c => { if (!current.includes(c)) current.push(c); });
            el.className = current.join(' ');
          },
          remove: (...classes) => {
            const current = el.className ? el.className.split(/\s+/).filter(Boolean) : [];
            el.className = current.filter(c => !classes.includes(c)).join(' ');
          },
          contains: (c) => (el.className ? el.className.split(/\s+/).includes(c) : false),
          toggle: (c, force) => {
            const has = el.classList.contains(c);
            const shouldAdd = force !== undefined ? force : !has;
            if (shouldAdd) el.classList.add(c); else el.classList.remove(c);
            return shouldAdd;
          }
        };
        return el;
      }
    }, alert() {}, confirm: () => true
  });
  for (const file of ['modules/markdownUtils.js', 'modules/skillView.js', 'modules/projectView.js', 'modules/chatView.js', 'modules/adminView.js', 'api.js']) {
    vm.runInContext(fs.readFileSync(path.join(__dirname, '../../main/resources/static/js', file), 'utf8'), context);
  }
  return context;
}

test('review report displays evidence, classification, line range and actual server scope without dropping legacy fields', () => {
  const {window}=loadFrontend();
  const html=window.SkillView.renderTaskReport({id:20,status:'COMPLETED',output:{schemaVersion:2,summary:'Phạm vi giới hạn',
    issues:[{classification:'POTENTIAL_RISK',severity:'HIGH',category:'SECURITY',filePath:'AccountRepository.java',lineStart:6,lineEnd:8,
      evidence:'executeQuery(<input>)',explanation:'Luồng input vào SQL',suggestion:'Bind tham số'}],
    suggestions:['Cân nhắc tách lớp'],limitations:['Chưa kiểm tra deployment'],
    reviewMetadata:{model:'approved-model',modelCalls:2,toolCalls:1,maxModelCalls:25,maxToolCalls:24,peakRequestChars:1700,maxContextChars:96000,
      filesRead:[{filePath:'AccountRepository.java',lineStart:1,lineEnd:8}]}}});
  for(const text of ['AccountRepository.java:6-8','executeQuery(&lt;input&gt;)','POTENTIAL_RISK','Luồng input vào SQL','Bind tham số',
    'Chưa kiểm tra deployment','approved-model','2/25','1/24','AccountRepository.java:1-8']) assert.ok(html.includes(text),text);
  assert.ok(!html.includes('<input>'));
  const legacy=window.SkillView.renderTaskReport({id:1,status:'COMPLETED',output:{summary:'Cũ',issues:[{file:'Old.java',description:'Lỗi cũ',recommendation:'Sửa cũ'}],suggestions:[]}});
  assert.ok(legacy.includes('Old.java')); assert.ok(legacy.includes('Lỗi cũ')); assert.ok(legacy.includes('Sửa cũ'));
  const oldFindings=window.SkillView.renderTaskReport({id:2,status:'COMPLETED',output:{findings:[{file:'Legacy.java',description:'legacy finding'}]}});
  assert.ok(oldFindings.includes('Legacy.java')); assert.ok(oldFindings.includes('legacy finding')); 
});

test('zero issue report distinguishes no persisted findings from insufficient context', () => {
  const {window}=loadFrontend();
  const html=window.SkillView.renderTaskReport({id:21,status:'COMPLETED',output:{summary:'Chưa đủ dữ liệu',issues:[],suggestions:[],limitations:['Chưa đọc mã nguồn']}});
  assert.ok(html.includes('Không có finding')); assert.ok(html.includes('Chưa đọc mã nguồn'));
  assert.doesNotThrow(() => window.SkillView.renderTaskReport({id:22,status:'COMPLETED',output:{
    summary:'Dữ liệu cũ',issues:[],suggestions:[],reviewMetadata:{filesRead:[null,'invalid']}}}));
});

test('Markdown preserves code literally and never embeds code in event handlers', () => {
  const { window } = loadFrontend();
  const code = "const x = '**bold**';\n<img src=x onerror=alert(1)>\n`inline`\n";
  const html = window.MarkdownUtils.formatMarkdown('```js\n' + code + '```');
  assert.ok(html.includes("const x = &#39;**bold**&#39;;\n&lt;img"));
  assert.ok(html.includes('`inline`\n</code>'));
  assert.ok(!html.includes('<strong>'));
  assert.ok(!html.includes('<img'));
  assert.match(html, /onclick="window.MarkdownUtils.copyCodeSnippet\(this\)"/);
  assert.equal(window.MarkdownUtils.escapeHtml('"\'><img>'), '&quot;&#39;&gt;&lt;img&gt;');
});

test('Finding fields and stored user messages are escaped', () => {
  const { window } = loadFrontend();
  const payload = '<img src=x onerror=alert(1)>';
  const findings = window.ChatView.renderReviewFindingsHtml([{ title: payload, description: payload, suggestion: payload, file: payload }]);
  assert.ok(!findings.includes('<img'));
  const list = { innerHTML: '' };
  window.ChatView.renderChatMessagesStream([{ sender: 'USER', content: payload }],
    { currentUser: { username: 'alice' } }, { chatMessagesList: list });
  assert.ok(list.innerHTML.includes('&lt;img'));
  assert.ok(!list.innerHTML.includes('<img'));
});

test('Failed conversation deletion rejects and keeps local state', async () => {
  const context = loadFrontend();
  context.fetch = async () => { throw new Error('network unavailable'); };
  await assert.rejects(context.window.apiClient.deleteChatConversation(1), /network unavailable/);
  const state = { conversations: [{ id: 1 }], activeConversationId: 1 };
  await context.window.ChatView.deleteChatConversationById(1, state, {}, context.window.apiClient);
  assert.equal(state.conversations.length, 1);
  assert.equal(state.activeConversationId, 1);
});

test('Admin settings use the backend response fields and existing API methods', async () => {
  const context = loadFrontend();
  const model = { value: '' };
  const hint = { textContent: '' };
  context.document.getElementById = id => ({ inputAdminActiveModel: model, adminCurrentKeyHint: hint })[id] || null;
  await context.window.AdminView.loadAdminSettings({ getAdminSettings: async () => ({ activeModel: 'chosen-model', maskedApiKey: 'abcd****1234' }) });
  assert.equal(model.value, 'chosen-model');
  assert.ok(hint.textContent.includes('abcd****1234'));
  assert.equal(typeof context.window.apiClient.updateAdminSettings, 'function');
  assert.equal(typeof context.window.apiClient.updateToolStatus, 'function');
});

test('API requests use the current HTTP origin even on a custom port', () => {
  assert.equal(loadFrontend().window.apiClient.baseUrl, 'http://localhost:9090');
});

test('Local admin login stores the server session without sending a password', async () => {
  const context = loadFrontend();
  const stored = new Map();
  context.localStorage.setItem = (key, value) => stored.set(key, value);
  context.localStorage.getItem = key => stored.get(key) || null;
  context.fetch = async (url, options) => {
    assert.equal(url, 'http://localhost:9090/api/auth/local-admin');
    assert.equal(options.method, 'POST');
    assert.equal(options.headers['X-Local-Quick-Login'], '1');
    assert.equal(options.body, undefined);
    return { ok: true, json: async () => ({ token: 'local-session', username: 'admin', role: 'ADMIN' }) };
  };
  await context.window.apiClient.loginLocalAdmin();
  assert.equal(context.window.apiClient.getToken(), 'local-session');
  assert.equal(context.window.apiClient.getCurrentUser().username, 'admin');
});

test('Disabled local admin login does not create a browser session', async () => {
  const context = loadFrontend();
  context.fetch = async () => ({ ok: false, json: async () => ({ message: 'Local login disabled' }) });
  await assert.rejects(context.window.apiClient.loginLocalAdmin(), /Local login disabled/);
  assert.equal(context.window.apiClient.getToken(), null);
});

test('Admin chat moderation API: getUserConversationsForAdmin fetches conversations and handles errors', async () => {
  const context = loadFrontend();
  const mockConversations = [
    { id: 101, title: 'Chat 1', model: 'gemini-3.5-flash-lite', createdAt: '2026-11-06T00:00:00Z', updatedAt: '2026-11-06T01:00:00Z', projectId: 1, projectName: 'Project 1' }
  ];

  // Success case
  context.fetch = async (url, options) => {
    assert.equal(url, 'http://localhost:9090/api/admin/users/42/conversations');
    assert.equal(options.method, undefined);
    return { ok: true, json: async () => mockConversations };
  };
  const result = await context.window.apiClient.getUserConversationsForAdmin(42);
  assert.equal(result.length, 1);
  assert.equal(result[0].id, 101);
  assert.equal(result[0].title, 'Chat 1');

  // Network error fallback to empty array
  context.fetch = async () => { throw new Error('network failure'); };
  const fallback = await context.window.apiClient.getUserConversationsForAdmin(42);
  assert.equal(fallback.length, 0);
});

test('Admin chat moderation API: getConversationMessagesForAdmin fetches messages and handles errors', async () => {
  const context = loadFrontend();
  const mockMessages = [
    { id: 201, conversationId: 101, sender: 'USER', content: 'Hello AI', createdAt: '2026-11-06T00:01:00Z' },
    { id: 202, conversationId: 101, sender: 'AGENT', content: 'Hello! How can I help?', createdAt: '2026-11-06T00:01:05Z' }
  ];

  // Success case
  context.fetch = async (url, options) => {
    assert.equal(url, 'http://localhost:9090/api/admin/conversations/101/messages');
    assert.equal(options.method, undefined);
    return { ok: true, json: async () => mockMessages };
  };
  const result = await context.window.apiClient.getConversationMessagesForAdmin(101);
  assert.equal(result.length, 2);
  assert.equal(result[0].id, 201);
  assert.equal(result[1].id, 202);

  // Server error fallback to empty array
  context.fetch = async () => ({ ok: false, status: 500 });
  const fallback = await context.window.apiClient.getConversationMessagesForAdmin(101);
  assert.equal(fallback.length, 0);
});

test('Admin chat moderation API: deleteConversationForAdmin sends DELETE and rejects on failure', async () => {
  const context = loadFrontend();

  // Success case
  let deleteCalled = false;
  context.fetch = async (url, options) => {
    assert.equal(url, 'http://localhost:9090/api/admin/conversations/101');
    assert.equal(options.method, 'DELETE');
    deleteCalled = true;
    return { ok: true, json: async () => ({ message: 'Conversation deleted' }) };
  };
  const res = await context.window.apiClient.deleteConversationForAdmin(101);
  assert.ok(deleteCalled);
  assert.equal(res.message, 'Conversation deleted');

  // Error case
  context.fetch = async () => ({
    ok: false,
    status: 403,
    json: async () => ({ message: 'Forbidden' })
  });
  await assert.rejects(
    context.window.apiClient.deleteConversationForAdmin(101),
    /Forbidden/
  );
});

test('Admin chat moderation API: deleteMessageForAdmin sends DELETE and rejects on failure', async () => {
  const context = loadFrontend();

  // Success case
  let deleteMsgCalled = false;
  context.fetch = async (url, options) => {
    assert.equal(url, 'http://localhost:9090/api/admin/messages/201');
    assert.equal(options.method, 'DELETE');
    deleteMsgCalled = true;
    return { ok: true, json: async () => ({ message: 'Message deleted' }) };
  };
  const res = await context.window.apiClient.deleteMessageForAdmin(201);
  assert.ok(deleteMsgCalled);
  assert.equal(res.message, 'Message deleted');

  // Error case
  context.fetch = async () => ({
    ok: false,
    status: 404,
    json: async () => ({ message: 'Tin nhắn không tồn tại' })
  });
  await assert.rejects(
    context.window.apiClient.deleteMessageForAdmin(201),
    /Tin nhắn không tồn tại/
  );
});

test('AdminView: inspect tabs switching and chat moderation UI render properly', async () => {
  const context = loadFrontend();
  function createMockTab() {
    return {
      classList: {
        classes: new Set(),
        add(c) { this.classes.add(c); },
        remove(c) { this.classes.delete(c); },
        toggle(c, v) { if (v) this.classes.add(c); else this.classes.delete(c); },
        contains(c) { return this.classes.has(c); }
      }
    };
  }

  const elements = {
    tabInspectTasks: createMockTab(),
    tabInspectChats: createMockTab(),
    tabInspectProjects: createMockTab(),
    inspectTasksContainer: { style: {} },
    inspectChatsContainer: { style: {}, innerHTML: '', querySelectorAll: () => [] },
    inspectProjectsContainer: { style: {} },
    inspectTasksCount: { textContent: '' },
    inspectChatsCount: { textContent: '' },
    inspectProjectsCount: { textContent: '' },
    modalAdminInspectUser: {}
  };

  context.window.AdminView.initAdminViewElements(elements);
  context.window.AdminView.switchInspectTab('chats', elements);

  assert.equal(elements.tabInspectChats.classList.contains('active'), true);
  assert.equal(elements.tabInspectTasks.classList.contains('active'), false);
  assert.equal(elements.inspectChatsContainer.style.display, 'flex');
  assert.equal(elements.inspectTasksContainer.style.display, 'none');

  const mockConversations = [
    { id: 99, title: '<script>alert(1)</script>', model: 'gemini-3.5-flash-lite', updatedAt: '2026-11-06T02:00:00Z' }
  ];

  context.window.AdminView.renderInspectUserChats({ id: 1 }, mockConversations, elements, context.window.apiClient);
  assert.ok(elements.inspectChatsContainer.innerHTML.includes('data-action="view-conv-messages"'));
  assert.ok(elements.inspectChatsContainer.innerHTML.includes('data-action="delete-conv"'));
  assert.ok(elements.inspectChatsContainer.innerHTML.includes('&lt;script&gt;alert(1)&lt;/script&gt;'));
  assert.ok(!elements.inspectChatsContainer.innerHTML.includes('<script>alert(1)</script>'));
});

test('AdminView: deleting active conversation cleans up state and UI messages', async () => {
  const context = loadFrontend();
  const mockElements = {
    modalAdminPanel: { classList: { add() {} }, querySelector: () => null },
    chatMessagesList: { innerHTML: '<div>Previous message</div>' },
    chatEmptyState: { style: { display: 'none' } },
    inspectChatsContainer: { innerHTML: '', querySelectorAll: () => [] },
    inspectChatsCount: { textContent: '1' }
  };
  const mockState = {
    conversations: [{ id: 99, title: 'Violation chat' }],
    activeConversationId: 99,
    activeConversation: { id: 99, title: 'Violation chat' }
  };
  let deleteCalled = false;
  const mockApiClient = {
    deleteConversationForAdmin: async (id) => {
      assert.equal(id, 99);
      deleteCalled = true;
      return { message: 'Deleted' };
    },
    getAdminStats: async () => ({}),
    getAdminUsers: async () => [],
    getAdminTools: async () => [],
    getAdminSettings: async () => ({}),
    getAdminAllTasks: async () => [],
    getAdminAuditLogs: async () => []
  };

  // Open admin panel modal to store savedEl, savedState, savedApiClient
  await context.window.AdminView.openAdminPanelModal(mockElements, { currentUser: { role: 'ADMIN' }, ...mockState }, mockApiClient, () => {});

  const conversations = [{ id: 99, title: 'Violation chat' }];
  let capturedHandler = null;
  mockElements.inspectChatsContainer.querySelectorAll = (selector) => {
    if (selector === "[data-action='delete-conv']") {
      return [{
        getAttribute: (attr) => attr === 'data-conv-id' ? '99' : 'Violation chat',
        set onclick(fn) { capturedHandler = fn; },
        get onclick() { return capturedHandler; }
      }];
    }
    return [];
  };

  context.window.AdminView.renderInspectUserChats({ id: 1 }, conversations, mockElements, mockApiClient);
  assert.ok(typeof capturedHandler === 'function');
  await capturedHandler();

  assert.ok(deleteCalled);
  assert.equal(conversations.length, 0);
  assert.equal(mockElements.inspectChatsCount.textContent, 0);
  assert.equal(mockElements.chatMessagesList.innerHTML, '');
  assert.equal(mockElements.chatEmptyState.style.display, 'flex');
});

test('AdminView: loadAdminTools renders switches and updates tool status', async () => {
  const context = loadFrontend();
  const container = { innerHTML: '', querySelector: () => null, querySelectorAll: () => [] };
  context.document.getElementById = (id) => id === 'adminToolsListContainer' ? container : null;

  const mockTools = [
    { toolName: 'grep', displayName: 'Grep Tool', description: 'Search keywords', enabled: true },
    { toolName: 'glob', displayName: 'Glob Tool', description: 'Find files', enabled: false }
  ];

  let updatedName = null;
  let updatedEnabled = null;
  const mockApiClient = {
    getAdminTools: async () => mockTools,
    updateToolStatus: async (name, enabled) => {
      updatedName = name;
      updatedEnabled = enabled;
      return { toolName: name, enabled };
    }
  };

  let capturedHandlers = [];
  container.querySelectorAll = (selector) => {
    if (selector === 'input[data-tool-name]') {
      return [{
        getAttribute: () => 'grep',
        checked: false,
        set onchange(fn) { capturedHandlers.push({ input: this, fn }); }
      }];
    }
    return [];
  };

  await context.window.AdminView.loadAdminTools(mockApiClient);
  assert.ok(container.innerHTML.includes('Grep Tool'));
  assert.ok(container.innerHTML.includes('glob'));
  assert.equal(capturedHandlers.length, 1);

  await capturedHandlers[0].fn();
  assert.equal(updatedName, 'grep');
  assert.equal(updatedEnabled, false);
});

test('ChatView: content moderation error renders alert bubble with error message', async () => {
  const context = loadFrontend();
  const messagesList = {
    children: [],
    appendChild(child) { this.children.push(child); }
  };
  const typingIndicator = { style: { display: 'none' } };
  const sendButton = { disabled: false };
  const streamArea = { scrollTop: 0, scrollHeight: 100 };
  const elements = {
    chatMessagesList: messagesList,
    chatTypingIndicator: typingIndicator,
    btnSendCentralChat: sendButton,
    chatStreamArea: streamArea
  };
  const state = {
    activeConversationId: 10,
    activeConversation: { id: 10 },
    activeProject: null,
    isChatSending: false
  };
  const mockApiClient = {
    sendChatMessage: async () => {
      throw new Error('Tin nhắn bị từ chối do vi phạm quy chuẩn kiểm duyệt nội dung: Phát hiện rò rỉ secret / khóa nhạy cảm');
    }
  };

  await context.window.ChatView.handleSendCentralChatMessage(
    'AIzaSyD-secret-key-123456789012345678',
    state,
    elements,
    mockApiClient,
    () => {}
  );

  assert.equal(state.isChatSending, false);
  assert.equal(sendButton.disabled, false);
  assert.equal(typingIndicator.style.display, 'none');
  assert.equal(messagesList.children.length, 2);
  const userMsgRow = messagesList.children[0];
  assert.ok(userMsgRow.className.includes('rejected'));
  const errorRow = messagesList.children[1];
  assert.ok(errorRow.innerHTML.includes('Cảnh báo Kiểm duyệt Nội dung An toàn'));
  assert.ok(errorRow.innerHTML.includes('Tin nhắn bị từ chối do vi phạm quy chuẩn kiểm duyệt nội dung'));
  assert.ok(errorRow.innerHTML.includes('Phát hiện rò rỉ secret / khóa nhạy cảm'));
});

test('ProjectView: selectProjectWorkspace updates workspace badge title and expands tree', () => {
  const context = loadFrontend();
  const badge = { title: '' };
  const nameEl = { textContent: '' };
  const pathEl = { textContent: '', style: {} };
  const delBtn = { style: {}, title: '' };
  const hintEl = { textContent: '' };
  const navText = { textContent: '' };
  const selectWsBtn = { style: {} };

  const elements = {
    chatWorkspaceBadge: badge,
    chatWorkspaceName: nameEl,
    chatWorkspacePath: pathEl,
    btnDeleteActiveProject: delBtn,
    chatProjectHintText: hintEl,
    navActiveProjectText: navText,
    btnSelectWorkspace: selectWsBtn
  };
  const state = {
    view: 'welcome',
    activeProject: null,
    expandedProjectIds: new Set(),
    conversations: []
  };

  const project = { id: 42, name: 'MySecureProject', path: '/workspace/project' };
  let startNewChatCalled = false;

  context.window.ProjectView.selectProjectWorkspace(project, state, elements, {
    startNewChatConversationFn: () => { startNewChatCalled = true; }
  });

  assert.equal(state.view, 'chat');
  assert.equal(state.activeProject.id, 42);
  assert.ok(state.expandedProjectIds.has(42));
  assert.equal(badge.title, 'Thư mục: /workspace/project');
  assert.equal(nameEl.textContent, 'MySecureProject');
  assert.equal(delBtn.style.display, 'inline-flex');
  assert.ok(startNewChatCalled);
});

test('AdminView: deleting message refreshes active conversation when it matches current chat', async () => {
  const context = loadFrontend();
  const mockUser = { id: 7, username: 'testuser' };
  const mockMessages = [
    { id: 991, conversationId: 50, sender: 'USER', content: 'Inappropriate content', createdAt: '2026-11-06T00:00:00Z' }
  ];
  let deletedMessageId = null;
  let chatViewRefreshedConvId = null;

  context.window.ChatView.selectChatConversation = (convId) => {
    chatViewRefreshedConvId = convId;
  };

  const mockApiClient = {
    deleteMessageForAdmin: async (msgId) => {
      deletedMessageId = msgId;
      return { success: true };
    }
  };

  let capturedDeleteHandler = null;
  const container = {
    innerHTML: '',
    querySelectorAll: (selector) => {
      if (selector === "[data-action='delete-message']") {
        return [{
          getAttribute: (attr) => attr === 'data-message-id' ? '991' : null,
          set onclick(fn) { capturedDeleteHandler = fn; }
        }];
      }
      return [];
    },
    querySelector: () => null
  };

  const el = {
    inspectChatsContainer: container,
    modalAdminInspectUser: { classList: { add() {} } }
  };
  const state = {
    currentUser: { role: 'ADMIN', username: 'admin' },
    activeConversationId: 50
  };

  // Open modal to register savedState and savedEl
  await context.window.AdminView.openAdminPanelModal(el, state, mockApiClient, () => {});
  // Display messages list
  context.window.AdminView.displayMessagesList(mockUser, 50, 'Chat Title', [], mockMessages, el, mockApiClient);

  assert.ok(capturedDeleteHandler !== null);
  await capturedDeleteHandler();

  assert.equal(deletedMessageId, 991);
  assert.equal(chatViewRefreshedConvId, 50);
});



test('Skill selector exposes versions, keeps legacy default and sends only selected IDs/versions', async () => {
  const context = loadFrontend();
  const container = { innerHTML: '', querySelector: () => null, querySelectorAll: () => [] };
  const catalog = [{ id: 'security-review', version: '1.0.0', description: '<img src=x>', languages: ['*'] },
    { id: 'performance-review', version: '1.0.0', description: 'Performance', languages: ['*'] }];
  await context.window.SkillView.loadSkills({ getSkills: async () => catalog }, container);
  assert.ok(container.innerHTML.includes('security-review'));
  assert.ok(container.innerHTML.includes('1.0.0'));
  assert.ok(!container.innerHTML.includes('<img'));
  assert.ok(!container.innerHTML.includes(' checked'));
  assert.equal(context.window.SkillView.readSelection(container).length, 0);
  container.querySelectorAll = () => [{ value: 'security-review', dataset: { version: '1.0.0' } }];
  assert.equal(JSON.stringify(context.window.SkillView.readSelection(container)), '[{"id":"security-review","version":"1.0.0"}]');
});

test('Applied skills display uses completed server output rather than requested selections', () => {
  const { window } = loadFrontend();
  const pending = window.SkillView.renderAppliedSkills({ input: { skills: [{ id: 'requested' }] } });
  assert.ok(!pending.includes('requested'));
  const html = window.SkillView.renderAppliedSkills({ output: { appliedSkills: [{ id: 'security-review', version: '<img>' }] } });
  assert.ok(html.includes('security-review'));
  assert.ok(!html.includes('<img>'));
  assert.ok(window.SkillView.renderTaskReport({ id: 1, status: 'COMPLETED', output: { summary: 'OK', issues: [], suggestions: [], appliedSkills: [] } }).includes('OK'));
});

test('Skill catalog API is authenticated and selected skills survive task submission', async () => {
  const context = loadFrontend(); const calls = [];
  context.window.apiClient.getHeaders = () => ({ Authorization: 'Bearer offline-test' });
  context.window.apiClient.getCurrentUser = () => ({ id: 1, username: 'tester' });
  context.fetch = async (url, options) => { calls.push({ url, options }); return { ok: true, json: async () => [] }; };
  await context.window.apiClient.getSkills();
  await context.window.apiClient.createTask({ projectId: 1, type: 'CODE_REVIEW', skills: [{ id: 'security-review', version: '1.0.0' }] });
  assert.ok(calls[0].url.endsWith('/api/skills'));
  assert.equal(calls[0].options.headers.Authorization, 'Bearer offline-test');
  assert.equal(JSON.parse(calls[1].options.body).skills[0].version, '1.0.0');
});

test('Review task cards escape project data and preserve applied skill provenance', () => {
  const { window } = loadFrontend();
  const html = window.SkillView.renderTaskCard({ id: 1, status: 'COMPLETED', type: 'CODE_REVIEW',
    output: { issues: [], appliedSkills: [{ id: 'security-review', version: '1.0.0' }] } },
    { name: '<img src=x onerror=alert(1)>' }, 'vừa xong');
  assert.ok(!html.includes('<img'));
  assert.ok(html.includes('&lt;img'));
  assert.ok(html.includes('security-review@1.0.0'));
  assert.ok(html.includes('data-welcome-task-id="1"'));
});

test('failed project and task reads reject rather than reporting an empty successful dashboard', async () => {
  const context=loadFrontend();
  const client=context.window.apiClient;
  client.getCurrentUser=()=>({username:'alice'});client.getToken=()=> 'session';
  context.fetch=async()=>({ok:false,status:503});
  await assert.rejects(client.getProjects(),/503/);
  await assert.rejects(client.getTasks(),/503/);
});

test('a slow conversation response cannot replace the currently selected conversation', async () => {
  const context=loadFrontend();let finish;
  const state={projects:[],activeConversationId:null,expandedProjectIds:new Set()};
  const api={getToken:()=> 'session',getChatConversationDetail:id=>id===1?new Promise(resolve=>{finish=resolve;}):Promise.resolve({id:2,title:'Current',messages:[]})};
  const first=context.window.ChatView.selectChatConversation(1,state,{},api);
  await context.window.ChatView.selectChatConversation(2,state,{},api);
  finish({id:1,title:'Stale',messages:[]});await first;
  assert.equal(state.activeConversation.id,2);
});

test('conversation lists arriving after an account switch cannot overwrite the new account', async () => {
  const context = loadFrontend();
  let finish, token = 'alice', renders = 0;
  const state = {currentUser: {username: 'alice'}, conversations: []};
  const api = {getToken: () => token, getChatConversations: () => new Promise(resolve => { finish = resolve; })};
  const loading = context.window.ChatView.loadChatConversations(state, {}, api, false, () => renders++);
  token = 'bob';
  state.currentUser = {username: 'bob'};
  state.conversations = [{id: 2, title: 'Bob'}];
  finish([{id: 1, title: 'Alice'}]);
  await loading;
  assert.equal(state.conversations[0].id, 2);
  assert.equal(renders, 0);
});

test('malformed legacy report fields do not prevent opening the report or invent skill provenance', () => {
  const {window}=loadFrontend();
  const html=window.SkillView.renderTaskReport({id:1,status:'COMPLETED',output:'{"issues":[null,{"severity":3,"title":"Finding"}]}'});
  assert.ok(html.includes('Finding'));assert.ok(!html.includes('Skill đã áp dụng: security'));
  assert.doesNotThrow(()=>window.SkillView.renderTaskReport({id:2,status:'COMPLETED',output:'null'}));
});

test('chat rendering preserves scroll position when the reader has moved away from the bottom', () => {
  const {window}=loadFrontend();
  const area={scrollHeight:2000,scrollTop:200,clientHeight:500};
  window.ChatView.renderChatMessagesStream([{sender:'AGENT',content:'A new answer'}],{currentUser:{username:'a'}},{chatMessagesList:{innerHTML:''},chatStreamArea:area});
  assert.equal(area.scrollTop,200);
  area.scrollTop=1500;
  window.ChatView.renderChatMessagesStream([{sender:'AGENT',content:'Next answer'}],{currentUser:{username:'a'}},{chatMessagesList:{innerHTML:''},chatStreamArea:area});
  assert.equal(area.scrollTop,2000);
});

test('admin control reads preserve HTTP errors and restrict the resource allowlist', async () => {
  const context=loadFrontend();
  context.fetch=async()=>({ok:false,status:403});
  await assert.rejects(context.window.apiClient.getAdminControlData('users'),error=>error.status===403);
  await assert.rejects(context.window.apiClient.getAdminControlData('../auth'),/resource/i);
});

test('review requests exclude display-only projectName while preserving the backend contract', async () => {
  const context=loadFrontend();
  context.window.apiClient.getCurrentUser=()=>({username:'tester'});
  let body;
  context.fetch=async(url,options)=>{body=JSON.parse(options.body);return {ok:true,json:async()=>({id:9,status:'PENDING'})};};
  await context.window.apiClient.createTask({projectId:1,projectName:'services aiagent',type:'CODE_REVIEW',
    input:{projectPath:'/workspace/service',focus:'all'},selectedTools:['file_system'],
    skills:[{id:'security-review',version:'1.0.0'}]});
  assert.equal(Object.hasOwn(body,'projectName'),false);
  assert.deepEqual(Object.keys(body).sort(),['input','projectId','selectedTools','skills','type']);
  assert.equal(JSON.parse(body.input).focus,'all');
  assert.equal(body.skills[0].version,'1.0.0');
});

test('late unauthorized workspace responses cannot log out a different account', async () => {
  const context=loadFrontend();let token='alice',user={username:'alice'},resolve;
  const api=context.window.apiClient;
  api.getToken=()=>token;api.getCurrentUser=()=>user;
  api.logout=()=>{token=null;user=null;};
  context.fetch=()=>new Promise(done=>{resolve=done;});
  const pending=api.getProjects();token='bob';user={username:'bob'};
  resolve({status:401,ok:false});await pending;
  assert.equal(token,'bob');assert.equal(user.username,'bob');
});

test('chat generation completing after account or conversation switch cannot replace the active chat', async () => {
  for(const change of ['account','conversation']) {
    const {window}=loadFrontend();let token='alice',finish;
    const state={currentUser:{username:'alice'},isChatSending:false,activeConversationId:1,activeProject:null,activeConversation:null,projects:[],conversations:[]};
    const messages=[];
    const api={getToken:()=>token,sendChatMessage:()=>new Promise(resolve=>{finish=resolve;}),getChatConversationDetail:async()=>({id:1,title:'Old',messages:[]}),getChatConversations:async()=>[]};
    const pending=window.ChatView.handleSendCentralChatMessage('hello',state,{chatMessagesList:{appendChild:node=>messages.push(node)}},api,()=>{});
    if(change==='account'){token='bob';state.currentUser={username:'bob'};}
    state.activeConversationId=2;
    finish({conversationId:1,content:'private old response'});await pending;
    assert.equal(state.activeConversationId,2,change);
    assert.equal(messages.length,1,'only the optimistic message from before the switch');
  }
});

test('a model update for an old conversation cannot change the newly selected conversation',async()=>{
 const {window}=loadFrontend();let finish,handler;
 const state={activeConversationId:1,activeConversation:{model:'old'},conversations:[]};
 const select={value:'selected',addEventListener:(type,fn)=>{handler=fn;}};
 window.apiClient.getToken=()=> 'alice';
 window.apiClient.updateChatConversationModel=()=>new Promise(resolve=>{finish=resolve;});
 window.ChatView.mount({el:{selectChatModel:select},state});
 const pending=handler();state.activeConversationId=2;state.activeConversation={model:'new'};finish({});await pending;
 assert.equal(state.activeConversation.model,'new');
});

test('project deletion finishing in a prior account cannot remove the new account project',async()=>{
 const {window}=loadFrontend();let token='alice',finish;
 const state={projects:[{id:1,name:'old'}],tasks:[],conversations:[],expandedProjectIds:new Set(),activeProject:null};
 const api={getToken:()=>token,deleteProject:()=>new Promise(resolve=>{finish=resolve;})};
 const pending=window.ProjectView.handleDeleteProject(1,'old',state,{},api);
 token='bob';state.projects=[{id:1,name:'new'}];finish(true);await pending;
 assert.equal(state.projects[0]?.name,'new');
});

test('a failed project deletion keeps the server-owned project in browser state',async()=>{
 const {window}=loadFrontend();
 const state={projects:[{id:1,name:'project'}],tasks:[],conversations:[],expandedProjectIds:new Set(),activeProject:null};
 await window.ProjectView.handleDeleteProject(1,'project',state,{}, {deleteProject:async()=>{throw new Error('offline');}});
 assert.equal(state.projects.length,1);
});

test('a failed old conversation request cannot replace the current chat with its error',async()=>{
 const {window}=loadFrontend();let fail;
 const state={currentUser:{username:'alice'},projects:[],activeProject:null,activeConversationId:null};
 const messages={innerHTML:''};
 const api={getToken:()=> 'alice',getChatConversationDetail:id=>id===1?new Promise((resolve,reject)=>{fail=reject;}):Promise.resolve({id:2,title:'Current',messages:[{sender:'AGENT',content:'new response'}]})};
 const old=window.ChatView.selectChatConversation(1,state,{chatMessagesList:messages},api,()=>{});
 await window.ChatView.selectChatConversation(2,state,{chatMessagesList:messages},api,()=>{});
 fail(new Error('old request offline'));await old;
 assert.ok(messages.innerHTML.includes('new response'));
 assert.ok(!messages.innerHTML.includes('old request offline'));
});
