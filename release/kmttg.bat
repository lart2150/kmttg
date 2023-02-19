@echo off
SET KMTTG_BAT=%~dp0
SET KMTTG_DIR=%KMTTG_BAT:~0,-1%
if exist %KMTTG_DIR%\javafx-sdk\ (
	FOR /D %%D IN (%KMTTG_DIR%\javafx-sdk\*) DO (
		java --module-path "%%D\lib" --add-modules javafx.web -jar "%KMTTG_BAT%\kmttg.jar" %*
		exit
	)
)

java -jar "%KMTTG_BAT%\kmttg.jar" %*