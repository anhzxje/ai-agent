/**
 * ============================================================================
 * APP STATE - QUẢN LÝ TRẠNG THÁI TOÀN CỤC CỦA SOOPI AI AGENT
 * ============================================================================
 * Cung cấp kho lưu trữ dữ liệu trung tâm (Single Source of Truth):
 * - Quản lý view hiện tại (welcome / chat / workspace).
 * - Danh sách dự án (projects), tác vụ AI (tasks), hội thoại chat (conversations).
 * - Dự án đang kích hoạt (activeProject), cuộc trò chuyện đang mở (activeConversation).
 * - Thông tin người dùng hiện tại (currentUser) và giao diện Sáng/Tối (theme).
 * ============================================================================
 */
(function() {
  const state = {
    view: "welcome", // "welcome" | "chat" | "workspace"
    projects: [],
    tasks: [],
    pinnedTasks: [],
    conversations: [],
    activeConversationId: null,
    activeConversation: null,
    isChatSending: false,
    activeProject: null,
    activeTask: null,
    activeTab: "findings",
    currentUser: window.apiClient ? window.apiClient.getCurrentUser() : null,
    theme: localStorage.getItem("ai_agent_theme") || "light",
    expandedProjectIds: new Set()
  };

  const listeners = new Set();

  function subscribe(fn) {
    if (typeof fn === "function") {
      listeners.add(fn);
      return () => listeners.delete(fn);
    }
  }

  function notify(changeType, payload) {
    listeners.forEach(fn => {
      try {
        fn(changeType, payload, state);
      } catch (err) {
        console.error("Lỗi trong State Listener:", err);
      }
    });
  }

  function setState(updates) {
    if (updates && typeof updates === "object") {
      Object.assign(state, updates);
      notify("state-updated", updates);
    }
  }

  window.AppState = {
    state,
    subscribe,
    notify,
    setState
  };
})();
