@echo off
REM Copies the freshly built debug APK to D:\app-debug.apk
set "SRC=%~dp0app\build\outputs\apk\debug\app-debug.apk"
set "DST=D:\app-debug.apk"

if not exist "%SRC%" (
  echo APK not found at:
  echo   %SRC%
  echo Run Build ^> Build Bundle(s) / APK(s) ^> Build APK(s) in Android Studio first.
  pause
  exit /b 1
)

copy /Y "%SRC%" "%DST%"
if errorlevel 1 (
  echo Copy failed. Make sure D: exists and is writable.
  pause
  exit /b 1
)

echo.
echo Copied to %DST%
pause
