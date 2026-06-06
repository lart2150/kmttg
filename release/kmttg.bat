@echo off
SET KMTTG_BAT=%~dp0
SET KMTTG_DIR=%KMTTG_BAT:~0,-1%

java -jar "%KMTTG_BAT%\kmttg.jar" %*
