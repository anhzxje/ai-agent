@echo off
:: ========================================================
:: SCRIPT KHOI CHAY NHANH UNG DUNG DESKTOP SOOPI
:: Muc dich:
:: 1. Uu tien khoi chay file Soopi.exe da dong goi san (neu ton tai).
:: 2. Neu chua dong goi, tu dong chuyen sang chay Electron dev qua 'npm start'.
:: ========================================================
title Running Soopi AI Agent Desktop
echo Starting Soopi Desktop Application...
if exist "%~dp0desktop\dist\Soopi-win32-x64\Soopi.exe" (
  start "" "%~dp0desktop\dist\Soopi-win32-x64\Soopi.exe"
) else (
  cd /d "%~dp0desktop"
  start npm start
)
exit

