@echo off
setlocal enabledelayedexpansion
SET KMTTG_BAT=%~dp0
SET KMTTG_DIR=%KMTTG_BAT:~0,-1%

REM Read optional JVM parameters, one per line, from kmttg.vmoptions
SET VMOPTIONS=
IF EXIST "%KMTTG_BAT%kmttg.vmoptions" (
    FOR /F "usebackq eol=# delims=" %%a IN ("%KMTTG_BAT%kmttg.vmoptions") DO (
        SET VMOPTIONS=!VMOPTIONS! %%a
    )
)

java !VMOPTIONS! -jar "%KMTTG_BAT%\kmttg.jar" %*
