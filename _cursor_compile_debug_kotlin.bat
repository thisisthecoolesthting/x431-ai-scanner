@echo off
cd /d " %~dp0\
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%
call gradlew.bat --no-daemon :app:compileDebugKotlin
exit /b %ERRORLEVEL%
