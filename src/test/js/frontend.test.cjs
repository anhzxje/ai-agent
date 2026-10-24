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
    document: { getElementById: () => null }, alert() {}, confirm: () => true
  });
  for (const file of ['modules/markdownUtils.js', 'modules/chatView.js', 'modules/adminView.js', 'api.js']) {
    vm.runInContext(fs.readFileSync(path.join(__dirname, '../../main/resources/static/js', file), 'utf8'), context);
  }
  return context;
}

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
