/**
 * ============================================================================
 * AGENT API CLIENT - GIAO TIẾP VỚI SPRING BOOT REST API
 * ============================================================================
 * Mục đích:
 * 1. Quản lý trạng thái xác thực người dùng (JWT token, thông tin User).
 * 2. Cung cấp các hàm gọi API theo chuẩn RESTful cho toàn bộ hệ thống:
 *    - Phân hệ Xác thực: /api/auth (Login, Register)
 *    - Phân hệ Dự án: /api/projects (CRUD dự án mã nguồn)
 *    - Phân hệ Tác vụ AI: /api/tasks (Tạo, Xem, Ghim, Hủy, Chạy lại, Nhân bản, Xóa, Xuất file)
 *    - Phân hệ Cá nhân hóa: /api/users (Thông tin cá nhân, Đổi mật khẩu, Tùy chọn theme/ngôn ngữ)
 *    - Phân hệ Hệ thống: /api/system (Lấy model hoạt động, danh sách tools đang bật)
 *    - Phân hệ Quản trị: /api/admin (Thống kê, Quản lý tài khoản, Bật/tắt tool, Cấu hình model/key, Audit log)
 *    - Phân hệ Trò chuyện: /api/chat (Hội thoại AI tương tác đa phiên, lưu lịch sử)
 * 3. Hỗ trợ tự động nhận diện môi trường:
 *    - Trình duyệt Web (qua localhost:8080 hoặc domain)
 *    - Ứng dụng Desktop Electron (.exe) với fallback ngoại tuyến mượt mà.
 * ============================================================================
 */
class AgentApiClient {
  constructor(baseUrl = "") {
    if (baseUrl) {
      this.baseUrl = baseUrl;
    } else if (window.location.protocol.startsWith("http")) {
      this.baseUrl = window.location.origin;
    } else {
      this.baseUrl = "http://localhost:8080";
    }
    this.tokenKey = "ai_agent_jwt_token";
    this.userKey = "ai_agent_user";
    this.isBackendOnline = false;
    this.isExplicitDemoMode = false;
  }

  getToken() {
    return localStorage.getItem(this.tokenKey);
  }

  setToken(token) {
    if (token) {
      localStorage.setItem(this.tokenKey, token);
    } else {
      localStorage.removeItem(this.tokenKey);
    }
  }

  getCurrentUser() {
    const raw = localStorage.getItem(this.userKey);
    if (raw) {
      try {
        return JSON.parse(raw);
      } catch (e) {
        return null;
      }
    }
    return null;
  }

  setCurrentUser(user) {
    if (user) {
      localStorage.setItem(this.userKey, JSON.stringify(user));
    } else {
      localStorage.removeItem(this.userKey);
    }
  }

  getHeaders() {
    const headers = {
      "Content-Type": "application/json"
    };
    const token = this.getToken();
    if (token) {
      headers["Authorization"] = `Bearer ${token}`;
    }
    return headers;
  }

  async checkHealth() {
    try {
      const res = await fetch(`${this.baseUrl}/api/projects`, {
        method: "GET",
        headers: this.getHeaders(),
        signal: AbortSignal.timeout(1500)
      });
      if (res.status === 200 || res.status === 401 || res.status === 403) {
        this.isBackendOnline = true;
        return true;
      }
    } catch (err) {
      if (this.baseUrl !== "http://localhost:8080") {
        try {
          const res = await fetch("http://localhost:8080/api/projects", {
            method: "GET",
            headers: this.getHeaders(),
            signal: AbortSignal.timeout(1500)
          });
          if (res.status === 200 || res.status === 401 || res.status === 403) {
            this.baseUrl = "http://localhost:8080";
            this.isBackendOnline = true;
            return true;
          }
        } catch (e2) {}
      }
    }

    this.isBackendOnline = false;
    return false;
  }

  async login(username, password) {
    const urls = [this.baseUrl, "http://localhost:8080"];
    const unique = [...new Set(urls.filter(Boolean))];

    let lastErr = null;
    for (const url of unique) {
      try {
        const res = await fetch(`${url}/api/auth/login`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ username, password }),
          signal: AbortSignal.timeout(3000)
        });

        if (res.ok) {
          const data = await res.json();
          this.baseUrl = url;
          this.isBackendOnline = true;
          this.setToken(data.token);
          const user = { username: data.username || username, role: data.role || "USER", email: `${username}@test.com` };
          this.setCurrentUser(user);
          return data;
        } else {
          let msg = "Đăng nhập thất bại. Kiểm tra lại username/mật khẩu!";
          try {
            const errData = await res.json();
            if (errData && errData.message) msg = errData.message;
          } catch (_) {}
          throw new Error(msg);
        }
      } catch (err) {
        lastErr = err;
        if (err.message && !err.message.includes("Failed to fetch") && !err.message.includes("timeout") && !err.message.includes("NetworkError")) {
          throw err;
        }
      }
    }

    throw new Error(lastErr ? (lastErr.message || "Không thể kết nối đến máy chủ Spring Boot (http://localhost:8080)") : "Lỗi kết nối máy chủ");
  }

  async register(username, email, password) {
    const urls = [this.baseUrl, "http://localhost:8080"];
    const unique = [...new Set(urls.filter(Boolean))];

    let lastErr = null;
    for (const url of unique) {
      try {
        const res = await fetch(`${url}/api/auth/register`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ username, email, password }),
          signal: AbortSignal.timeout(3000)
        });

        if (res.ok) {
          const data = await res.json();
          this.baseUrl = url;
          this.isBackendOnline = true;
          this.setToken(data.token);
          const user = { username: data.username || username, email, role: data.role || "USER" };
          this.setCurrentUser(user);
          return data;
        } else {
          let msg = "Đăng ký thất bại.";
          try {
            const errData = await res.json();
            if (errData && errData.message) msg = errData.message;
          } catch (_) {}
          throw new Error(msg);
        }
      } catch (err) {
        lastErr = err;
        if (err.message && !err.message.includes("Failed to fetch") && !err.message.includes("timeout") && !err.message.includes("NetworkError")) {
          throw err;
        }
      }
    }

    throw new Error(lastErr ? (lastErr.message || "Không thể kết nối đến máy chủ Spring Boot (http://localhost:8080)") : "Lỗi kết nối máy chủ");
  }

  logout() {
    this.setToken(null);
    this.setCurrentUser(null);
    localStorage.removeItem(this.tokenKey);
    localStorage.removeItem(this.userKey);
  }

  async getProjects() {
    if (!this.getCurrentUser() || !this.getToken()) {
      return [];
    }

    if (!this.isBackendOnline && this.isExplicitDemoMode) {
      return this.getUserLocalProjects();
    }
    try {
      const res = await fetch(`${this.baseUrl}/api/projects`, {
        headers: this.getHeaders(),
        signal: AbortSignal.timeout(5000)
      });
      if (res.ok) {
        this.isBackendOnline = true;
        return await res.json();
      }
      if (res.status === 401 || res.status === 403) {
        this.logout();
        return [];
      }
    } catch (e) {
      this.isBackendOnline = false;
      console.warn("Không thể tải danh sách dự án từ máy chủ:", e.message);
    }
    return this.isExplicitDemoMode ? this.getUserLocalProjects() : [];
  }

  async createProject(projectData) {
    if (!this.getCurrentUser()) {
      throw new Error("Vui lòng đăng nhập trước khi tạo dự án");
    }

    try {
      const res = await fetch(`${this.baseUrl}/api/projects`, {
        method: "POST",
        headers: this.getHeaders(),
        body: JSON.stringify(projectData),
        signal: AbortSignal.timeout(10000)
      });
      if (res.ok) {
        this.isBackendOnline = true;
        return await res.json();
      }
      let errorMsg = "Không thể tạo dự án";
      try {
        const err = await res.json();
        if (err && (err.message || err.error)) errorMsg = err.message || err.error;
      } catch (_) {}
      throw new Error(errorMsg);
    } catch (e) {
      if (this.isExplicitDemoMode) {
        return this.saveNewLocalProject(projectData);
      }
      throw e;
    }
  }

  async deleteProject(projectId) {
    try {
      const res = await fetch(`${this.baseUrl}/api/projects/${projectId}`, {
        method: "DELETE",
        headers: this.getHeaders(),
        signal: AbortSignal.timeout(6000)
      });
      if (res.ok || res.status === 404) {
        this.deleteUserLocalProject(projectId);
        return true;
      }
      let errMsg = "Không thể xóa dự án";
      try {
        const err = await res.json();
        if (err && (err.message || err.error)) errMsg = err.message || err.error;
      } catch (_) {}
      throw new Error(errMsg);
    } catch (e) {
      this.deleteUserLocalProject(projectId);
      if (e.message && e.message.includes("Không thể xóa")) {
        throw e;
      }
      return true;
    }
  }

  deleteUserLocalProject(projectId) {
    const list = this.getUserLocalProjects();
    const filtered = list.filter(p => Number(p.id) !== Number(projectId));
    this.saveUserLocalProjects(filtered);
  }

  saveNewLocalProject(projectData) {
    const user = this.getCurrentUser();
    const newProj = {
      id: Date.now(),
      name: projectData.name,
      description: projectData.description || "",
      path: projectData.path,
      language: projectData.language || "Java",
      ownerUsername: user?.username || "guest",
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString()
    };
    const list = this.getUserLocalProjects();
    list.unshift(newProj);
    this.saveUserLocalProjects(list);
    return newProj;
  }

  async getTasks() {
    if (!this.getCurrentUser() || !this.getToken()) {
      return [];
    }

    try {
      const res = await fetch(`${this.baseUrl}/api/tasks`, {
        headers: this.getHeaders(),
        signal: AbortSignal.timeout(5000)
      });
      if (res.ok) {
        this.isBackendOnline = true;
        const tasks = await res.json();
        return Array.isArray(tasks) ? tasks : [];
      }
      if (res.status === 401 || res.status === 403) {
        this.logout();
        return [];
      }
      console.warn(`GET /api/tasks returned HTTP ${res.status}`);
    } catch (e) {
      this.isBackendOnline = false;
      console.warn("Lỗi khi kết nối GET /api/tasks:", e);
    }

    if (this.isExplicitDemoMode) {
      return this.getUserLocalTasks();
    }
    return [];
  }

  async getTask(taskId) {
    if (!this.getCurrentUser() || !this.getToken()) {
      return null;
    }
    try {
      const res = await fetch(`${this.baseUrl}/api/tasks/${taskId}`, {
        headers: this.getHeaders(),
        signal: AbortSignal.timeout(5000)
      });
      if (res.ok) {
        this.isBackendOnline = true;
        return await res.json();
      }
      console.warn(`GET /api/tasks/${taskId} returned HTTP ${res.status}`);
    } catch (e) {
      console.warn(`Lỗi khi gọi GET /api/tasks/${taskId}:`, e);
    }
    return null;
  }

  async getTasksByProject(projectId) {
    if (!this.getCurrentUser()) return [];
    try {
      const res = await fetch(`${this.baseUrl}/api/tasks/project/${projectId}`, {
        headers: this.getHeaders(),
        signal: AbortSignal.timeout(5000)
      });
      if (res.ok) {
        this.isBackendOnline = true;
        return await res.json();
      }
    } catch (e) {
      // ignore
    }
    if (this.isExplicitDemoMode) {
      const all = this.getUserLocalTasks();
      return all.filter(t => t.projectId === Number(projectId));
    }
    return [];
  }

  async createTask(taskData) {
    if (!this.getCurrentUser()) {
      throw new Error("Vui lòng đăng nhập trước khi tạo tác vụ AI");
    }

    const payload = { ...taskData };
    if (payload.input && typeof payload.input === 'object') {
      payload.input = JSON.stringify(payload.input);
    }

    try {
      const res = await fetch(`${this.baseUrl}/api/tasks`, {
        method: "POST",
        headers: this.getHeaders(),
        body: JSON.stringify(payload),
        signal: AbortSignal.timeout(10000)
      });
      if (res.ok) {
        this.isBackendOnline = true;
        return await res.json();
      }
      const err = await res.json().catch(() => ({ message: "Không thể tạo task" }));
      throw new Error(err.message || `HTTP ${res.status}`);
    } catch (e) {
      if (!this.isExplicitDemoMode) throw e;
      // Local fallback for explicit demo mode only
      const user = this.getCurrentUser();
      const newTask = {
        id: Date.now(),
        projectId: taskData.projectId,
        projectName: taskData.projectName || "Current Project",
        type: taskData.type || "CODE_REVIEW",
        status: "PROCESSING",
        input: typeof taskData.input === "string" ? taskData.input : JSON.stringify(taskData.input),
        output: null,
        errorMessage: null,
        createdAt: new Date().toISOString(),
        completedAt: null,
        isPinned: true
      };

      setTimeout(() => {
        newTask.status = "COMPLETED";
        newTask.completedAt = new Date().toISOString();
        newTask.output = JSON.stringify({
          summary: "AI Code Review hoàn thành với các công cụ ListDirectoryTool, ReadFileTool, GrepTool.",
          findings: [
            {
              severity: "CRITICAL",
              category: "SECURITY",
              file: "src/main/java/prj/anhzxje/aiagent/agent/CodeReviewAgent.java",
              line: 65,
              title: "Path Traversal Risk via unchecked projectPath parameter",
              description: "AI Agent phát hiện đường dẫn projectPath chưa được chuẩn hóa chống Path Traversal.",
              suggestion: "Path normalized = Paths.get(projectPath).normalize();\nif (!normalized.startsWith(allowedBasePath)) throw new SecurityException(\"Unauthorized directory path\");"
            },
            {
              severity: "HIGH",
              category: "SECURITY",
              file: "src/main/java/prj/anhzxje/aiagent/security/JwtAuthenticationFilter.java",
              line: 42,
              title: "Potential NullPointer on malformed Authorization header",
              description: "Thiếu kiểm tra độ dài chuỗi Bearer trước khi cắt token.",
              suggestion: "if (bearer != null && bearer.startsWith(\"Bearer \") && bearer.length() > 7) { ... }"
            }
          ]
        });
        const tasks = this.getUserLocalTasks();
        const idx = tasks.findIndex(t => t.id === newTask.id);
        if (idx >= 0) tasks[idx] = newTask;
        this.saveUserLocalTasks(tasks);
        window.dispatchEvent(new CustomEvent("agent-task-updated", { detail: newTask }));
      }, 2500);

      const tasks = this.getUserLocalTasks();
      tasks.unshift(newTask);
      this.saveUserLocalTasks(tasks);
      return newTask;
    }
  }

  async togglePin(taskId) {
    try {
      const res = await fetch(`${this.baseUrl}/api/tasks/${taskId}/pin`, {
        method: "PATCH",
        headers: this.getHeaders(),
        signal: AbortSignal.timeout(3000)
      });
      if (res.ok) {
        return await res.json();
      }
    } catch (e) {
      // ignore
    }
    if (this.isExplicitDemoMode) {
      const tasks = this.getUserLocalTasks();
      const task = tasks.find(t => t.id === Number(taskId));
      if (task) {
        task.isPinned = !task.isPinned;
        this.saveUserLocalTasks(tasks);
        return task;
      }
    }
    return null;
  }

  // User-isolated local storage helpers for demo mode
  getUserLocalProjects() {
    const user = this.getCurrentUser();
    if (!user) return [];
    const key = `projects_user_${user.username}`;
    const raw = localStorage.getItem(key);
    if (raw) {
      try { return JSON.parse(raw); } catch (e) {}
    }
    const initial = [
      {
        id: 1,
        name: "soopi",
        description: "Spring Boot & Spring AI Code Review Engine",
        path: "D:/AI Agent/aiagent/aiagent",
        language: "Java",
        ownerUsername: user.username,
        createdAt: new Date(Date.now() - 3600 * 1000 * 24).toISOString(),
        updatedAt: new Date(Date.now() - 3600 * 1000 * 5).toISOString()
      }
    ];
    this.saveUserLocalProjects(initial);
    return initial;
  }

  saveUserLocalProjects(list) {
    const user = this.getCurrentUser();
    if (!user) return;
    const key = `projects_user_${user.username}`;
    localStorage.setItem(key, JSON.stringify(list));
  }

  getUserLocalTasks() {
    const user = this.getCurrentUser();
    if (!user) return [];
    const key = `tasks_user_${user.username}`;
    const raw = localStorage.getItem(key);
    if (raw) {
      try { return JSON.parse(raw); } catch (e) {}
    }
    const initial = [
      {
        id: 101,
        projectId: 1,
        projectName: "soopi",
        type: "CODE_REVIEW",
        status: "COMPLETED",
        isPinned: true,
        createdAt: new Date(Date.now() - 1000 * 60 * 25).toISOString(),
        completedAt: new Date(Date.now() - 1000 * 60 * 24).toISOString(),
        input: JSON.stringify({ projectPath: "D:/AI Agent/aiagent/aiagent", focus: "security" }),
        output: JSON.stringify({
          summary: "Automated AI Code Review completed. Found 2 critical security issues and 1 performance bottleneck.",
          findings: [
            {
              severity: "CRITICAL",
              category: "SECURITY",
              file: "src/main/java/prj/anhzxje/aiagent/agent/CodeReviewAgent.java",
              line: 65,
              title: "Path Traversal Risk via unchecked projectPath parameter",
              description: "AI Agent phát hiện đường dẫn projectPath chưa được chuẩn hóa chống Path Traversal.",
              suggestion: "Path normalized = Paths.get(projectPath).normalize();\nif (!normalized.startsWith(allowedBasePath)) throw new SecurityException(\"Unauthorized directory path\");"
            }
          ]
        })
      }
    ];
    this.saveUserLocalTasks(initial);
    return initial;
  }

  saveUserLocalTasks(list) {
    const user = this.getCurrentUser();
    if (!user) return;
    const key = `tasks_user_${user.username}`;
    localStorage.setItem(key, JSON.stringify(list));
  }

  async getAdminStats() {
    try {
      const res = await fetch(`${this.baseUrl}/api/admin/stats`, {
        headers: this.getHeaders(),
        signal: AbortSignal.timeout(3000)
      });
      if (res.ok) return await res.json();
    } catch (e) {
      console.warn("Could not fetch admin stats:", e);
    }
    return null;
  }

  async getAdminUsers() {
    try {
      const res = await fetch(`${this.baseUrl}/api/admin/users`, {
        headers: this.getHeaders(),
        signal: AbortSignal.timeout(3000)
      });
      if (res.ok) return await res.json();
    } catch (e) {
      console.warn("Could not fetch admin users:", e);
    }
    return [];
  }

  async updateUserRole(userId, role) {
    const res = await fetch(`${this.baseUrl}/api/admin/users/${userId}/role?role=${encodeURIComponent(role)}`, {
      method: "PUT",
      headers: this.getHeaders(),
      signal: AbortSignal.timeout(4000)
    });
    if (res.ok) return await res.json();
    const err = await res.json().catch(() => ({ message: "Không thể đổi vai trò" }));
    throw new Error(err.message || `Lỗi HTTP ${res.status}`);
  }

  async deleteUser(userId) {
    const res = await fetch(`${this.baseUrl}/api/admin/users/${userId}`, {
      method: "DELETE",
      headers: this.getHeaders(),
      signal: AbortSignal.timeout(5000)
    });
    if (res.ok) return await res.json();
    const err = await res.json().catch(() => ({ message: "Không thể xóa tài khoản" }));
    throw new Error(err.message || `Lỗi HTTP ${res.status}`);
  }

  async getUserTasksForAdmin(userId) {
    try {
      const res = await fetch(`${this.baseUrl}/api/admin/users/${userId}/tasks`, {
        headers: this.getHeaders(),
        signal: AbortSignal.timeout(4000)
      });
      if (res.ok) return await res.json();
    } catch (e) {
      console.warn("Could not fetch user tasks for admin:", e);
    }
    return [];
  }

  async getUserProjectsForAdmin(userId) {
    try {
      const res = await fetch(`${this.baseUrl}/api/admin/users/${userId}/projects`, {
        headers: this.getHeaders(),
        signal: AbortSignal.timeout(4000)
      });
      if (res.ok) return await res.json();
    } catch (e) {
      console.warn("Could not fetch user projects for admin:", e);
    }
    return [];
  }

  async updateUserStatus(userId, locked) {
    const res = await fetch(`${this.baseUrl}/api/admin/users/${userId}/status?locked=${locked}`, {
      method: "PUT",
      headers: this.getHeaders(),
      signal: AbortSignal.timeout(4000)
    });
    if (res.ok) return await res.json();
    const err = await res.json().catch(() => ({ message: "Không thể đổi trạng thái khóa" }));
    throw new Error(err.message || `Lỗi HTTP ${res.status}`);
  }

  async toggleUserLock(userId) {
    const res = await fetch(`${this.baseUrl}/api/admin/users/${userId}/lock`, {
      method: "PUT",
      headers: this.getHeaders(),
      signal: AbortSignal.timeout(4000)
    });
    if (res.ok) return await res.json();
    const err = await res.json().catch(() => ({ message: "Không thể khóa/mở khóa tài khoản" }));
    throw new Error(err.message || `Lỗi HTTP ${res.status}`);
  }

  async getAdminTools() {
    try {
      const res = await fetch(`${this.baseUrl}/api/admin/tools`, {
        headers: this.getHeaders(),
        signal: AbortSignal.timeout(3000)
      });
      if (res.ok) return await res.json();
    } catch (e) {
      console.warn("Could not fetch admin tools:", e);
    }
    return [];
  }

  async updateToolStatus(toolName, enabled) {
    const res = await fetch(`${this.baseUrl}/api/admin/tools/${encodeURIComponent(toolName)}?enabled=${enabled}`, {
      method: "PUT",
      headers: this.getHeaders(),
      signal: AbortSignal.timeout(4000)
    });
    if (res.ok) return await res.json();
    const err = await res.json().catch(() => ({ message: "Không thể cập nhật công cụ" }));
    throw new Error(err.message || `Lỗi HTTP ${res.status}`);
  }

  async toggleTool(toolName) {
    const res = await fetch(`${this.baseUrl}/api/admin/tools/${encodeURIComponent(toolName)}/toggle`, {
      method: "PUT",
      headers: this.getHeaders(),
      signal: AbortSignal.timeout(4000)
    });
    if (res.ok) return await res.json();
    const err = await res.json().catch(() => ({ message: "Không thể bật/tắt công cụ" }));
    throw new Error(err.message || `Lỗi HTTP ${res.status}`);
  }

  async getAdminSettings() {
    try {
      const res = await fetch(`${this.baseUrl}/api/admin/settings`, {
        headers: this.getHeaders(),
        signal: AbortSignal.timeout(3000)
      });
      if (res.ok) return await res.json();
    } catch (e) {
      console.warn("Could not fetch admin settings:", e);
    }
    return null;
  }

  async updateAdminSettings(settings) {
    const res = await fetch(`${this.baseUrl}/api/admin/settings`, {
      method: "PUT",
      headers: this.getHeaders(),
      body: JSON.stringify(settings),
      signal: AbortSignal.timeout(5000)
    });
    if (res.ok) return await res.json();
    const err = await res.json().catch(() => ({ message: "Không thể lưu cấu hình hệ thống" }));
    throw new Error(err.message || `Lỗi HTTP ${res.status}`);
  }

  async getAllAdminTasks(status, userId) {
    let url = `${this.baseUrl}/api/admin/tasks?`;
    if (status) url += `status=${encodeURIComponent(status)}&`;
    if (userId) url += `userId=${encodeURIComponent(userId)}&`;
    try {
      const res = await fetch(url, {
        headers: this.getHeaders(),
        signal: AbortSignal.timeout(4000)
      });
      if (res.ok) return await res.json();
    } catch (e) {
      console.warn("Could not fetch admin tasks:", e);
    }
    return [];
  }

  async getAdminAllTasks(status, userId) {
    return this.getAllAdminTasks(status, userId);
  }

  async getAdminErrorTasks() {
    try {
      const res = await fetch(`${this.baseUrl}/api/admin/tasks/errors`, {
        headers: this.getHeaders(),
        signal: AbortSignal.timeout(4000)
      });
      if (res.ok) return await res.json();
    } catch (e) {
      console.warn("Could not fetch admin error tasks:", e);
    }
    return [];
  }

  async getAuditLogs() {
    try {
      const res = await fetch(`${this.baseUrl}/api/admin/audit-logs`, {
        headers: this.getHeaders(),
        signal: AbortSignal.timeout(3000)
      });
      if (res.ok) return await res.json();
    } catch (e) {
      console.warn("Could not fetch audit logs:", e);
    }
    return [];
  }

  async getAdminAuditLogs() {
    return this.getAuditLogs();
  }

  // User Actions
  async cancelTask(taskId) {
    const res = await fetch(`${this.baseUrl}/api/tasks/${taskId}/cancel`, {
      method: "POST",
      headers: this.getHeaders(),
      signal: AbortSignal.timeout(3000)
    });
    if (res.ok) return await res.json();
    const err = await res.json().catch(() => ({ message: "Không thể hủy tác vụ" }));
    throw new Error(err.message || `Lỗi HTTP ${res.status}`);
  }

  async deleteTask(taskId) {
    const res = await fetch(`${this.baseUrl}/api/tasks/${taskId}`, {
      method: "DELETE",
      headers: this.getHeaders(),
      signal: AbortSignal.timeout(4000)
    });
    if (res.ok) return await res.json();
    const err = await res.json().catch(() => ({ message: "Không thể xóa tác vụ" }));
    throw new Error(err.message || `Lỗi HTTP ${res.status}`);
  }

  async deleteAllMyTasks() {
    const res = await fetch(`${this.baseUrl}/api/tasks`, {
      method: "DELETE",
      headers: this.getHeaders(),
      signal: AbortSignal.timeout(4000)
    });
    if (res.ok) return await res.json();
    const err = await res.json().catch(() => ({ message: "Không thể xóa lịch sử tác vụ" }));
    throw new Error(err.message || `Lỗi HTTP ${res.status}`);
  }

  async retryTask(taskId) {
    const res = await fetch(`${this.baseUrl}/api/tasks/${taskId}/retry`, {
      method: "POST",
      headers: this.getHeaders(),
      signal: AbortSignal.timeout(4000)
    });
    if (res.ok) return await res.json();
    const err = await res.json().catch(() => ({ message: "Không thể chạy lại tác vụ" }));
    throw new Error(err.message || `Lỗi HTTP ${res.status}`);
  }

  async changePassword(currentPassword, newPassword, confirmPassword) {
    const res = await fetch(`${this.baseUrl}/api/users/me/password`, {
      method: "PUT",
      headers: this.getHeaders(),
      body: JSON.stringify({ currentPassword, newPassword, confirmPassword }),
      signal: AbortSignal.timeout(4000)
    });
    if (res.ok) return await res.json();
    const err = await res.json().catch(() => ({ message: "Không thể đổi mật khẩu" }));
    throw new Error(err.message || `Lỗi HTTP ${res.status}`);
  }

  async getMyProfile() {
    try {
      const res = await fetch(`${this.baseUrl}/api/users/me`, {
        headers: this.getHeaders(),
        signal: AbortSignal.timeout(3000)
      });
      if (res.ok) return await res.json();
    } catch (e) {}
    return null;
  }

  async getMyPreferences() {
    try {
      const res = await fetch(`${this.baseUrl}/api/users/me/preferences`, {
        headers: this.getHeaders(),
        signal: AbortSignal.timeout(3000)
      });
      if (res.ok) return await res.json();
    } catch (e) {}
    return null;
  }

  async updateMyPreferences(prefs) {
    const res = await fetch(`${this.baseUrl}/api/users/me/preferences`, {
      method: "PUT",
      headers: this.getHeaders(),
      body: JSON.stringify(prefs),
      signal: AbortSignal.timeout(4000)
    });
    if (res.ok) return await res.json();
    const err = await res.json().catch(() => ({ message: "Không thể lưu cài đặt cá nhân" }));
    throw new Error(err.message || `Lỗi HTTP ${res.status}`);
  }

  async getSystemModels() {
    try {
      const res = await fetch(`${this.baseUrl}/api/system/models`, {
        headers: this.getHeaders(),
        signal: AbortSignal.timeout(3000)
      });
      if (res.ok) return await res.json();
    } catch (e) {}
    return { activeModel: "gemini-3.5-flash-lite", allowedModels: ["gemini-3.5-flash-lite", "gemini-3.1-flash-lite", "gemini-2.5-flash"] };
  }

  async getSystemTools() {
    try {
      const res = await fetch(`${this.baseUrl}/api/system/tools`, {
        headers: this.getHeaders(),
        signal: AbortSignal.timeout(3000)
      });
      if (res.ok) return await res.json();
    } catch (e) {}
    return [];
  }

  // Interactive AI Agent Chat
  async getChatConversations() {
    try {
      const res = await fetch(`${this.baseUrl}/api/chat/conversations`, {
        headers: this.getHeaders(),
        signal: AbortSignal.timeout(5000)
      });
      if (res.ok) return await res.json();
    } catch (e) {}
    return [];
  }

  async createChatConversation(title, model, projectId) {
    const res = await fetch(`${this.baseUrl}/api/chat/conversations`, {
      method: "POST",
      headers: this.getHeaders(),
      body: JSON.stringify({ title, model, projectId }),
      signal: AbortSignal.timeout(6000)
    });
    if (res.ok) return await res.json();
    throw new Error("Không thể tạo cuộc hội thoại mới");
  }

  async getChatConversationDetail(convId) {
    const res = await fetch(`${this.baseUrl}/api/chat/conversations/${convId}`, {
      headers: this.getHeaders(),
      signal: AbortSignal.timeout(6000)
    });
    if (res.ok) return await res.json();
    throw new Error("Không thể tải nội dung hội thoại");
  }

  async deleteChatConversation(convId) {
    const res = await fetch(`${this.baseUrl}/api/chat/conversations/${convId}`, {
      method: "DELETE", headers: this.getHeaders(), signal: AbortSignal.timeout(5000)
    });
    if (res.ok || res.status === 404) return true;
    const err = await res.json().catch(() => ({}));
    throw new Error(err.message || "Không thể xóa cuộc hội thoại");
  }

  async updateChatConversationModel(convId, model) {
    const res = await fetch(`${this.baseUrl}/api/chat/conversations/${convId}/model`, {
      method: "PATCH",
      headers: this.getHeaders(),
      body: JSON.stringify({ model }),
      signal: AbortSignal.timeout(4000)
    });
    if (res.ok) return await res.json();
    let errMsg = "Không thể cập nhật mô hình cho cuộc hội thoại";
    try {
      const err = await res.json();
      if (err && err.message) errMsg = err.message;
    } catch (_) {}
    throw new Error(errMsg);
  }

  async sendChatMessage(message, conversationId, model, projectId) {
    const res = await fetch(`${this.baseUrl}/api/chat`, {
      method: "POST",
      headers: this.getHeaders(),
      body: JSON.stringify({ message, conversationId, model, projectId }),
      signal: AbortSignal.timeout(60000) // 60s cho LLM + Tool calling trên codebase
    });
    if (res.ok) return await res.json();
    const err = await res.json().catch(() => ({ message: "Không thể gửi tin nhắn cho AI Agent" }));
    throw new Error(err.message || `Lỗi HTTP ${res.status}`);
  }

  async pingDatabase() {
    const t0 = performance.now();
    try {
      const res = await fetch(`${this.baseUrl}/api/projects`, {
        method: "GET",
        headers: this.getHeaders(),
        signal: AbortSignal.timeout(2000)
      });
      const t1 = performance.now();
      const latency = Math.round(t1 - t0);
      return {
        online: res.status === 200 || res.status === 401 || res.status === 403,
        latency: latency,
        status: res.status,
        url: this.baseUrl
      };
    } catch (e) {
      return { online: false, latency: null, error: e.message, url: this.baseUrl };
    }
  }
}

window.apiClient = new AgentApiClient();
