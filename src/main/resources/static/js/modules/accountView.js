/** Account settings bindings; authentication and authorization stay on existing endpoints. */
(function () {
  let mounted = false;
  function mount({api, openModal, closeModals, applyTheme}) {
    if (mounted) return;
    mounted = true;
    // Account Manager Modal Actions
    const btnAccChangePassword = document.getElementById("btnAccChangePassword");
    if (btnAccChangePassword) {
      btnAccChangePassword.addEventListener("click", () => {
        closeModals();
        openModal(document.getElementById("modalChangePassword"));
      });
    }

    const btnAccPreferences = document.getElementById("btnAccPreferences");
    if (btnAccPreferences) {
      btnAccPreferences.addEventListener("click", async () => {
        closeModals();
        openModal(document.getElementById("modalUserPreferences"));
        const session = api.getToken?.();
        try {
          const prefs = await api.getMyPreferences();
          if (session !== api.getToken?.()) return;
          if (prefs) {
            const pTheme = document.getElementById("prefTheme");
            const pLang = document.getElementById("prefLanguage");
            const pModel = document.getElementById("prefModel");
            if (pTheme && prefs.theme) pTheme.value = prefs.theme;
            if (pLang && prefs.language) pLang.value = prefs.language;
            if (pModel && prefs.preferredModel) pModel.value = prefs.preferredModel;
          }
        } catch (e) {
          console.warn("Could not load user preferences", e);
        }
      });
    }

    // Submit: Change Password
    const formChangePassword = document.getElementById("formChangePassword");
    if (formChangePassword) {
      formChangePassword.addEventListener("submit", async (e) => {
        e.preventDefault();
        const oldPass = document.getElementById("changePassOld").value;
        const newPass = document.getElementById("changePassNew").value;
        const confirmPass = document.getElementById("changePassConfirm").value;

        if (newPass !== confirmPass) {
          window.WorkspaceView.formError(formChangePassword, "Xác nhận mật khẩu mới không khớp.");
          return;
        }

        const session = api.getToken?.();
        try {
          await api.changePassword(oldPass, newPass, confirmPass);
          if (session !== api.getToken?.()) return;
          window.WorkspaceView.notify("Đã đổi mật khẩu.");
          closeModals();
          document.getElementById("changePassOld").value = "";
          document.getElementById("changePassNew").value = "";
          document.getElementById("changePassConfirm").value = "";
        } catch (err) {
          if (session !== api.getToken?.()) return;
          window.WorkspaceView.formError(formChangePassword, "Không đổi được mật khẩu: " + err.message);
        }
      });
    }

    // Submit: User Preferences
    const formUserPreferences = document.getElementById("formUserPreferences");
    if (formUserPreferences) {
      formUserPreferences.addEventListener("submit", async (e) => {
        e.preventDefault();
        const theme = document.getElementById("prefTheme").value;
        const language = document.getElementById("prefLanguage").value;
        const preferredModel = document.getElementById("prefModel").value;

        const session = api.getToken?.();
        try {
          await api.updateMyPreferences({ theme, language, preferredModel });
          if (session !== api.getToken?.()) return;
          applyTheme(theme);
          window.WorkspaceView.notify("Đã lưu tùy chọn cá nhân.");
          closeModals();
        } catch (err) {
          if (session !== api.getToken?.()) return;
          window.WorkspaceView.formError(formUserPreferences, "Không lưu được tùy chọn: " + err.message);
        }
      });
    }

  }
  window.AccountView = {mount};
})();
