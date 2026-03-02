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
    rem Then JAVA_HOME
    if defined JAVA_HOME (
        set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
    ) else (
        rem Fallback to PATH
        set "JAVA_CMD=java.exe"
    )
)

rem ##########################################################################
rem # EXECUTION                                                              #
rem ##########################################################################

rem This script is concatenated with a JAR file
rem The JAR starts after the :JAR_BOUNDARY label
"%JAVA_CMD%" %LUCLI_JAVA_ARGS% -jar "%~f0" %*
exit /b %ERRORLEVEL%

:JAR_BOUNDARY
