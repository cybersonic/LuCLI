@echo off
rem ##########################################################################
rem #                                                                        #
rem # LuCLI JVM Bootstrap for Windows                                        #
rem #                                                                        #
rem ##########################################################################

rem Set default Java arguments
if not defined LUCLI_JAVA_ARGS set LUCLI_JAVA_ARGS=-client

rem ##########################################################################
rem # JAVA DETERMINATION                                                     #
rem ##########################################################################

rem Default Java command
set JAVA_CMD=java

rem Check if JAVA_HOME is set
if defined JAVA_HOME (
    set JAVA_CMD=%JAVA_HOME%\bin\java
)

rem Check for embedded JRE (same directory as this script)
for %%I in ("%~dp0.") do set SCRIPT_DIR=%%~fI
if exist "%SCRIPT_DIR%\jre\bin\java.exe" (
    set JAVA_CMD=%SCRIPT_DIR%\jre\bin\java
)

rem ##########################################################################
rem # EXECUTION                                                              #
rem ##########################################################################

rem This script is concatenated with a JAR file
rem The JAR starts after the :JAR_BOUNDARY label
"%JAVA_CMD%" %LUCLI_JAVA_ARGS% -jar "%~f0" %*
exit /b %ERRORLEVEL%

:JAR_BOUNDARY
