@echo off
setlocal

set SERVICE_PATH=%~dp0
cd ..\..

if %OS%==64BIT (
	%SERVICE_PATH%apache\amd64\prunsrv //IS//kmttg --DisplayName="kmttg" --Startup=auto --Install=%SERVICE_PATH%apache\amd64\prunsrv.exe --Jvm=auto --StopTimeout=10 --LogPath=%SERVICE_PATH% --StdOutput=auto --StdError=auto --StartMode=java --Classpath=%%CLASSPATH%%;%cd%\kmttg.jar  --StartClass=com.tivo.kmttg.main.kmttg --StartParams=-a
) else (
	%SERVICE_PATH%apache\prunsrv //IS//kmttg --DisplayName="kmttg" --Startup=auto --Install=%SERVICE_PATH%apache\prunsrv.exe             --Jvm=auto --StopTimeout=10 --LogPath=%SERVICE_PATH% --StdOutput=auto --StdError=auto --StartMode=java --Classpath=%%CLASSPATH%%;%cd%\kmttg.jar  --StartClass=com.tivo.kmttg.main.kmttg --StartParams=-a
)
cd %SERVICE_PATH%