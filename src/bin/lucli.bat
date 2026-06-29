@echo off
rem ##########################################################################
rem #                                                                        #
rem # LuCLI JVM Bootstrap for Windows                                        #
rem #                                                                        #
rem ##########################################################################

rem Set default Java arguments
if not defined LUCLI_JAVA_ARGS set "LUCLI_JAVA_ARGS=-client"

rem ##########################################################################
rem # JAVA DETERMINATION                                                     #
rem ##########################################################################


rem Resolve script directory
set "SCRIPT_DIR=%~dp0"

rem Prefer embedded JRE
if exist "%SCRIPT_DIR%\jre\bin\java.exe" (
    set "JAVA_CMD=%SCRIPT_DIR%\jre\bin\java.exe"
) else (
    rem Then JAVA_HOME (only when it points at a real java.exe)
    if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" (
        set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
    ) else (
        rem Fallback to PATH
        set "JAVA_CMD=java.exe"
    )
)

rem ##########################################################################
rem # JAVA VERSION CHECK                                                     #
rem ##########################################################################

"%JAVA_CMD%" -version >nul 2>&1
if errorlevel 1 (
    >&2 echo [ERROR] Unable to run Java command: "%JAVA_CMD%"
    >&2 echo LuCLI requires Java 21 or newer.
    exit /b 1
)

set "JAVA_VERSION_RAW="
for /f "tokens=2 delims=\" %%v in ('"%JAVA_CMD%" -version 2^>^&1 ^| findstr /i "version"') do (
    set "JAVA_VERSION_RAW=%%v"
    goto :JAVA_VERSION_FOUND
)

:JAVA_VERSION_FOUND
if not defined JAVA_VERSION_RAW (
    >&2 echo [ERROR] Unable to detect Java version.
    >&2 echo LuCLI requires Java 21 or newer.
    "%JAVA_CMD%" -version 1>&2
    exit /b 1
)

set "JAVA_VERSION_MAJOR="
for /f "tokens=1,2 delims=._-+" %%a in ("%JAVA_VERSION_RAW%") do (
    if "%%a"=="1" (
        set "JAVA_VERSION_MAJOR=%%b"
    ) else (
        set "JAVA_VERSION_MAJOR=%%a"
    )
)

if not defined JAVA_VERSION_MAJOR (
    >&2 echo [ERROR] Unable to parse Java major version from "%JAVA_VERSION_RAW%".
    >&2 echo LuCLI requires Java 21 or newer.
    exit /b 1
)

set /a JAVA_VERSION_MAJOR_NUM=JAVA_VERSION_MAJOR >nul 2>&1
if errorlevel 1 (
    >&2 echo [ERROR] Unable to parse Java major version from "%JAVA_VERSION_RAW%".
    >&2 echo LuCLI requires Java 21 or newer.
    exit /b 1
)

if %JAVA_VERSION_MAJOR_NUM% LSS 21 (
    >&2 echo [ERROR] Java 21 or newer is required. Detected: %JAVA_VERSION_RAW%
    >&2 echo Please install Java 21+ and set JAVA_HOME if needed.
    exit /b 1
)
rem ##########################################################################
rem # JAVA 24+ RUNTIME NOISE CONTROL                                         #
rem ##########################################################################

set "LUCLI_UNSAFE_MEMORY_ACCESS_ARG="
if %JAVA_VERSION_MAJOR_NUM% GEQ 24 (
    set "LUCLI_HAS_UNSAFE_MEMORY_ACCESS_FLAG="
    if defined LUCLI_JAVA_ARGS (
        echo %LUCLI_JAVA_ARGS% | findstr /i /c:"--sun-misc-unsafe-memory-access=" >nul && set "LUCLI_HAS_UNSAFE_MEMORY_ACCESS_FLAG=1"
    )
    if not defined LUCLI_HAS_UNSAFE_MEMORY_ACCESS_FLAG if defined JDK_JAVA_OPTIONS (
        echo %JDK_JAVA_OPTIONS% | findstr /i /c:"--sun-misc-unsafe-memory-access=" >nul && set "LUCLI_HAS_UNSAFE_MEMORY_ACCESS_FLAG=1"
    )
    if not defined LUCLI_HAS_UNSAFE_MEMORY_ACCESS_FLAG if defined JAVA_TOOL_OPTIONS (
        echo %JAVA_TOOL_OPTIONS% | findstr /i /c:"--sun-misc-unsafe-memory-access=" >nul && set "LUCLI_HAS_UNSAFE_MEMORY_ACCESS_FLAG=1"
    )
    if not defined LUCLI_HAS_UNSAFE_MEMORY_ACCESS_FLAG (
        set "LUCLI_UNSAFE_MEMORY_ACCESS_ARG=--sun-misc-unsafe-memory-access=allow"
    )
)

rem ##########################################################################
rem # EXECUTION                                                              #
rem ##########################################################################
set "LUCLI_JAR=%SCRIPT_DIR%lucli.jar"
if not exist "%LUCLI_JAR%" (
    >&2 echo [ERROR] Unable to find LuCLI JAR at "%LUCLI_JAR%".
    >&2 echo Please ensure lucli.jar is in the same directory as lucli.bat.
    exit /b 1
)

"%JAVA_CMD%" %LUCLI_JAVA_ARGS% %LUCLI_UNSAFE_MEMORY_ACCESS_ARG% -jar "%LUCLI_JAR%" %*
exit /b %ERRORLEVEL%
