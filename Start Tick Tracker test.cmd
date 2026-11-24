@echo off
rem Double-click to start RuneLite with the Tick Tracker Bar plugin (Windows).
rem It runs the project's "run" task: RuneLite in developer mode, which you need to log in
rem with a Jagex account. Close RuneLite to stop it.
setlocal
cd /d "%~dp0"

rem Gradle 8.10 needs a JDK 17 or 21 (it can't run on JDK 24 or newer), so look for one in
rem the usual places: JDKs IntelliJ downloaded, then the Temurin, Oracle and Microsoft installers.
set "FOUND="
for /d %%J in ("%USERPROFILE%\.jdks\*-17*" "%USERPROFILE%\.jdks\*-21*" "%ProgramFiles%\Eclipse Adoptium\jdk-17*" "%ProgramFiles%\Eclipse Adoptium\jdk-21*" "%ProgramFiles%\Java\jdk-17*" "%ProgramFiles%\Java\jdk-21*" "%ProgramFiles%\Microsoft\jdk-17*" "%ProgramFiles%\Microsoft\jdk-21*") do if exist "%%~J\bin\java.exe" set "FOUND=%%~J"

if defined FOUND set "JAVA_HOME=%FOUND%"
if not defined JAVA_HOME (
	echo Couldn't find a JDK 17 or 21. README.md, "Running it", says where to get one.
	pause
	exit /b 1
)

echo Starting RuneLite with Tick Tracker Bar, using the JDK in %JAVA_HOME%
echo To log in, click the Jagex Accounts button in RuneLite's sidebar. Close RuneLite to stop.
echo.
call "%~dp0gradlew.bat" run --console=plain
if errorlevel 1 (
	echo.
	echo RuneLite didn't start. The messages above say why.
	pause
)
