/**
 * ============================================================================
 * SOOPI AI AGENT - ELECTRON DESKTOP RUNTIME (MAIN PROCESS)
 * ============================================================================
 * Mục đích:
 * 1. Khởi tạo cửa sổ Desktop Windows (Electron BrowserWindow) với cấu hình bảo mật chuẩn.
 * 2. Tự động kiểm tra máy chủ backend Spring Boot (http://localhost:8081).
 *    - Nếu backend online: Tải giao diện trực tiếp từ server để luôn có dữ liệu mới nhất.
 *    - Nếu backend offline: Tải gói ứng dụng tĩnh ngoại tuyến (offline bundled app) từ thư mục local.
 * 3. Cung cấp API tương tác native cho hệ điều hành thông qua Electron IPC:
 *    - Hộp thoại chọn thư mục Windows nguyên bản (Native Folder Picker: dialog.showOpenDialog).
 * 4. Điều hướng liên kết ngoài (external links) an toàn sang trình duyệt mặc định của hệ điều hành.
 * ============================================================================
 */

const { app, BrowserWindow, shell, ipcMain, dialog, globalShortcut } = require('electron');
const path = require('path');
const http = require('http');

let mainWindow = null;

/**
 * Kiểm tra xem máy chủ backend Spring Boot tại URL chỉ định có đang hoạt động hay không.
 * Sử dụng HTTP GET với thời gian chờ (timeout) 1 giây.
 * 
 * @param {string} url - URL máy chủ cần kiểm tra (ví dụ: http://localhost:8081/)
 * @param {Function} onSuccess - Callback được gọi khi kết nối thành công
 * @param {Function} onError - Callback được gọi khi máy chủ chưa bật hoặc mất kết nối
 */
function checkServerAvailable(url, onSuccess, onError) {
  const req = http.get(url, { timeout: 1000 }, (res) => {
    res.resume();
    onSuccess();
  });
  req.on('error', () => { onError(); });
  req.on('timeout', () => { req.destroy(); onError(); });
}

/**
 * Tạo cửa sổ giao diện chính của ứng dụng Desktop (Main Window)
 */
function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1400,
    height: 880,
    minWidth: 980,
    minHeight: 650,
    title: "Soopi - AI Agent Platform",
    autoHideMenuBar: true,
    show: false, // Ẩn cửa sổ ban đầu để tránh chớp trắng (white flash), chỉ hiện khi đã sẵn sàng
    webPreferences: {
      nodeIntegration: false,      // Bảo mật: Vô hiệu hóa Node.js trong renderer
      contextIsolation: true,     // Bảo mật: Cô lập ngữ cảnh giữa Renderer và Preload
      sandbox: false,
      preload: path.join(__dirname, 'preload.js') // Script cầu nối IPC an toàn
    }
  });

  // Kiểm tra trạng thái máy chủ Spring Boot (mặc định cổng 8081)
  const backendUrl = "http://localhost:8081/";
  checkServerAvailable(backendUrl, () => {
    // Trường hợp 1: Backend đang chạy -> nạp trực tiếp giao diện từ server
    console.log(`[Soopi Desktop] Spring Boot backend online. Loading ${backendUrl}`);
    mainWindow.loadURL(backendUrl);
  }, () => {
    // Trường hợp 2: Backend chưa khởi động -> nạp gói giao diện tĩnh nội bộ
    console.log(`[Soopi Desktop] Spring Boot backend offline. Loading local bundle.`);
    mainWindow.loadFile(path.join(__dirname, 'app', 'index.html'));
  });

  // Hiển thị cửa sổ mượt mà ngay khi trang đã render xong
  mainWindow.once('ready-to-show', () => {
    mainWindow.show();
  });

  // Bắt sự kiện mở liên kết: Mọi URL http/https bên ngoài sẽ được mở trong trình duyệt hệ điều hành
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    if (url.startsWith('http:') || url.startsWith('https:')) {
      shell.openExternal(url);
    }
    return { action: 'deny' }; // Ngăn cửa sổ Electron tự chuyển trang ra ngoài
  });

  mainWindow.on('closed', () => {
    mainWindow = null;
  });
}

/**
 * ============================================================================
 * IPC HANDLER: Hộp thoại chọn thư mục mã nguồn nguyên bản (Native Folder Picker)
 * ============================================================================
 * Mục đích: Cho phép người dùng duyệt thư mục dự án cục bộ trên máy tính Windows
 * một cách trực quan, lấy đường dẫn tuyệt đối chính xác để gửi về cho AI Agent.
 */
ipcMain.handle('dialog:openDirectory', async () => {
  if (!mainWindow) return null;
  const result = await dialog.showOpenDialog(mainWindow, {
    title: "Chọn thư mục dự án cần AI Agent phân tích",
    properties: ['openDirectory']
  });
  if (!result.canceled && result.filePaths && result.filePaths.length > 0) {
    return result.filePaths[0];
  }
  return null;
});

/**
 * Vòng đời ứng dụng Electron (App Lifecycle)
 */
app.whenReady().then(() => {
  createWindow();

  app.on('activate', () => {
    // Trên macOS: Tạo lại cửa sổ khi người dùng click vào icon dock mà không có cửa sổ nào mở
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow();
    }
  });
});

// Thoát ứng dụng khi tất cả các cửa sổ đã đóng (ngoại trừ macOS)
app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit();
  }
});
