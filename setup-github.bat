@echo off
REM Forces x431-ai-scanner into being its OWN git repo (separate from the parent CaseForge
REM repo it lives inside) and pushes to GitHub. Logs EVERYTHING to setup-github.log so we
REM can diagnose what happened even if the window closes.

setlocal EnableDelayedExpansion
cd /d "%~dp0"

set "LOG=%~dp0setup-github.log"
echo --- run started at %DATE% %TIME% --- > "%LOG%"

call :step "Checking git is on PATH"
where git >> "%LOG%" 2>&1
if errorlevel 1 (
  echo Git is not on your PATH. Install Git for Windows from https://git-scm.com/download/win | tee
  echo Git is not on your PATH. >> "%LOG%"
  goto :end_pause
)

call :step "Wiping any partial .git directory"
if exist ".git" (
  attrib -r -h -s ".git\*.*" /S /D >nul 2>&1
  rmdir /s /q ".git"
)
if exist ".git" (
  echo ERROR: Could not remove .git. Close Android Studio (and any other program with the project open), then re-run. >> "%LOG%"
  echo ERROR: Could not remove .git. Close Android Studio and re-run.
  goto :end_pause
)

call :step "Initializing fresh nested repo"
git init -b main >> "%LOG%" 2>&1
git config user.email "reasner196@gmail.com" >> "%LOG%" 2>&1
git config user.name "Ricky" >> "%LOG%" 2>&1

call :step "Staging files (respects .gitignore)"
git add . >> "%LOG%" 2>&1

call :step "Committing"
git diff --cached --quiet
if errorlevel 1 (
  git commit -m "CaseForge Scanner AI - initial commit" >> "%LOG%" 2>&1
) else (
  echo ERROR: Nothing to commit. .gitignore may be excluding everything. >> "%LOG%"
  echo ERROR: Nothing to commit.
  goto :end_pause
)

call :step "Wiring up GitHub remote"
git remote remove origin >> "%LOG%" 2>&1
git remote add origin "https://github.com/thisisthecoolesthting/x431-ai-scanner.git" >> "%LOG%" 2>&1

call :step "Pushing to GitHub (a browser window may open for login)"
git push -u origin main >> "%LOG%" 2>&1
if errorlevel 1 (
  echo Initial push rejected. Retrying with --force to overwrite the empty/auto-initialized repo. >> "%LOG%"
  echo First push rejected. Retrying with --force...
  git push -u origin main --force >> "%LOG%" 2>&1
  if errorlevel 1 (
    echo ERROR: push failed even with --force - see setup-github.log for details. >> "%LOG%"
    echo PUSH FAILED. Open setup-github.log for details.
    goto :end_pause
  )
)

echo --- SUCCESS at %DATE% %TIME% --- >> "%LOG%"
echo.
echo SUCCESS. Your code is on GitHub.
echo   Actions:  https://github.com/thisisthecoolesthting/x431-ai-scanner/actions
echo   APK:      https://github.com/thisisthecoolesthting/x431-ai-scanner/releases/tag/latest
echo.

:end_pause
echo.
echo --- press any key to close ---
pause >nul
exit /b

:step
echo. >> "%LOG%"
echo === %~1 === >> "%LOG%"
echo %~1
goto :eof
