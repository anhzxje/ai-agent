@echo off
:: ========================================================
:: SCRIPT DONG GOI UNG DUNG DESKTOP SOOPI.EXE
:: Muc dich:
:: 1. Tat tien trinh Soopi.exe cu dang chay (tranh loi EBUSY lock file).
:: 2. Dong bo tai nguyen giao dien tinh moi nhat tu backend sang Electron.
:: 3. Goi electron-packager de tao file chay Soopi.exe doc lap cho Windows x64.
:: ========================================================
title Build Soopi AI Agent Desktop (.exe)
echo ========================================================
echo   Building Soopi Desktop App (.exe) for Windows...
echo ========================================================
taskkill /F /IM Soopi.exe 2>nul
cd /d "%~dp0desktop"
call npm run build
echo.
echo ========================================================
echo   BUILD THANH CONG!
echo   File thuc thi .exe nam tai:
echo   desktop\dist\Soopi-win32-x64\Soopi.exe
echo ========================================================
pause

