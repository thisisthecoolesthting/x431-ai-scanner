@echo off
REM Installs the freshly built APK onto a connected Android device via ADB.
REM Run build.bat first.
set "ADB=%~dp0.build-cache\android-sdk\platform-tools\adb.exe"
set "APK=%~dp0app\build\outputs\apk\debug\app-debug.apk"

if not exist "%ADB%" (
  echo ADB not found. Run build.bat first.
  pause
  exit /b 1
)
if not exist "%APK%" (
  echo APK not found at %APK%. Run build.bat first.
  pause
  exit /b 1
)

"%ADB%" devices
echo.
echo Installing %APK% ...
"%ADB%" install -r "%APK%"
pause
