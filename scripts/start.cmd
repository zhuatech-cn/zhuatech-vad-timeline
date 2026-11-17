@echo off
setlocal
cd /d "%~dp0.."
if defined JAVA_HOME set "PATH=%JAVA_HOME%\bin;%PATH%"
set "SPEC="
for /f "tokens=3" %%v in ('java -XshowSettings:properties -version 2^>^&1 ^| findstr /c:"java.specification.version ="') do set "SPEC=%%v"
if not defined SPEC goto javaerror
if "%SPEC:~0,2%"=="1." goto javaerror
if %SPEC% LSS 21 goto javaerror
if "%~1"=="--no-build" goto launch
if "%~1"=="--build" goto build
if not "%~1"=="" exit /b 2
if exist .git goto build
if exist backend\target\app.jar goto launch
:build
call mvnw.cmd -B -f backend\pom.xml clean package
if errorlevel 1 exit /b 1
:launch
if not exist backend\target\app.jar exit /b 2
java -jar backend\target\app.jar
exit /b %errorlevel%
:javaerror
echo Java 21+ is required. For a source build, point JAVA_HOME at a full JDK. 1>&2
exit /b 2
