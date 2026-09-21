@echo off
REM Captures TiVo test fixtures for kmttg. Put this and kmttg-fixture-capture.jar in
REM your kmttg folder - the one holding config.ini - and double click this file.
SETLOCAL
SET HERE=%~dp0

where java >nul 2>nul
IF ERRORLEVEL 1 (
    echo Java was not found.
    echo.
    echo This needs Java 11 or newer, the same as kmttg itself. If kmttg runs on
    echo this machine, run this from the folder kmttg is installed in.
    echo.
    pause
    EXIT /B 1
)

java -Djava.net.preferIPv4Stack=true -jar "%HERE%kmttg-fixture-capture.jar" %*

REM Double clicked, the window closes the moment this ends and takes the summary
REM - including where the zip was written - with it.
echo.
pause
