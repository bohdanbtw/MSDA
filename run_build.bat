@echo off
cd /d "%~dp0"
powershell -ExecutionPolicy Bypass -File "AndroidCppApp\tools\build-android-package.ps1"
