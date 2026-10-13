@echo off
REM ---------------------------------------------------------------
REM Build the APK for "Ema's To-Do" using the toolchain inside this project.
REM No Android Studio required.
REM
REM   build.bat                  build debug APK (installable)
REM   build.bat assembleRelease  build release APK (signed with the debug key)
REM   build.bat clean            clean build outputs
REM
REM JDK: this script intentionally hardcodes no local path. It resolves the JDK in this order:
REM   1) build.local.bat in the same folder (machine-local, git-ignored)
REM   2) the JAVA_HOME environment variable
REM For a first run, copy build.local.bat.example to build.local.bat and set your JDK path.
REM ---------------------------------------------------------------
setlocal
set ROOT=%~dp0

REM %~dp0 always ends with a backslash. Strip it: a trailing backslash inside
REM quotes would escape the closing quote ("...\folder\") and make the argument
REM parser swallow the rest of the command line.
if "%ROOT:~-1%"=="\" set ROOT=%ROOT:~0,-1%

REM 1) machine-local config (skipped when absent)
if exist "%ROOT%\build.local.bat" call "%ROOT%\build.local.bat"

REM 2) fall back to the environment
if "%JAVA_HOME%"=="" (
  echo.
  echo [build] JDK not found. Pick one of these:
  echo [build]   1. copy build.local.bat.example to build.local.bat and set your JDK path
  echo [build]   2. set the JAVA_HOME environment variable to a JDK 17
  echo.
  exit /b 1
)
if not exist "%JAVA_HOME%\bin\javac.exe" (
  echo [build] JAVA_HOME does not contain bin\javac.exe: %JAVA_HOME%
  exit /b 1
)

set ANDROID_HOME=%ROOT%\.buildtools\sdk
set ANDROID_SDK_ROOT=%ANDROID_HOME%
set GRADLE_USER_HOME=%ROOT%\.gradle
set PATH=%JAVA_HOME%\bin;%PATH%

set TASK=%1
if "%TASK%"=="" set TASK=assembleDebug

echo [build] ROOT=%ROOT%
echo [build] JAVA_HOME=%JAVA_HOME%
echo [build] ANDROID_HOME=%ANDROID_HOME%
echo [build] task=%TASK%
echo.

call "%ROOT%\.buildtools\gradle-8.7\bin\gradle.bat" -p "%ROOT%" --no-daemon %TASK% %2 %3 %4
set RC=%ERRORLEVEL%
echo.
if "%RC%"=="0" (
  echo [build] OK. APK:
  dir /b /s "%ROOT%\app\build\outputs\apk\*.apk"
) else (
  echo [build] FAILED, exit code %RC%
)
endlocal & exit /b %RC%
