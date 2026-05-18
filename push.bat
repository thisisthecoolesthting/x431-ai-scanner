@echo off
REM Quick-push every subsequent code change to GitHub.
REM First time: run setup-github.bat. After that, run this whenever you want to push.
setlocal
cd /d "%~dp0"

where git >nul 2>&1
if errorlevel 1 (
  echo Git is not on your PATH. Install Git for Windows from https://git-scm.com/download/win
  pause
  exit /b 1
)

git add .
git diff --cached --quiet
if errorlevel 1 (
  set /p MSG=Commit message (or press Enter for "update"):
  if "%MSG%"=="" set MSG=update
  git commit -m "%MSG%"
)
git push
pause
