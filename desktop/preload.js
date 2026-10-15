/**
 * ============================================================================
 * ELECTRON PRELOAD SCRIPT - CẦU NỐI IPC BẢO MẬT (CONTEXT BRIDGE)
 * ============================================================================
 * Mục đích:
 * 1. Phơi bày API an toàn cho cửa sổ Renderer thông qua `window.electronAPI`.
 * 2. Cho phép giao diện web gọi hộp thoại chọn thư mục gốc của Windows (dialog:openDirectory)
 *    mà không cần cấp toàn bộ quyền Node.js vào trang web (đảm bảo an toàn theo chuẩn bảo mật Electron).
 * ============================================================================
 */
const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('electronAPI', {
  /**
   * Gọi Main Process để mở hộp thoại Windows Explorer chọn thư mục dự án
   * @returns {Promise<string|null>} Đường dẫn tuyệt đối của thư mục được chọn
   */
  openDirectory: () => ipcRenderer.invoke('dialog:openDirectory')
});
