set Args = wscript.Arguments
if Args.Count < 2 then
    wscript.stderr.writeline( "? Invalid number of arguments")
    wscript.quit 1
end if

' Check for flags.
VrdAllowMultiple = false
lockFile = ""
for i = 1 to args.Count
    p = args(i-1)
    if left(p,3)="/l:" then lockFile = mid(p,4)
    if p = "/m" then VrdAllowMultiple = true
next

' Check that a lock file name was given
if ( lockFile = "" ) then
    wscript.stderr.writeline( "? Lock file (/l:) not given" )
    wscript.quit 2
end if

Set fso = CreateObject("Scripting.FileSystemObject")
sourceFile = args(0)
destFile   = args(1)

'Create VideoReDo object and open the source project / file.
' Try VRD 6 1st then older VRDs
ver = 6
if (VrdAllowMultiple) then
    On Error Resume Next
    Set VideoReDo = WScript.CreateObject( "VideoReDo6.Application" )
    if ( not IsObject(VideoReDo) ) then
        ver = 5
        Set VideoReDo = wscript.CreateObject( "VideoReDo5.Application" )
        if ( not IsObject(VideoReDo) ) then
            ver = 4
            Set VideoReDo = wscript.CreateObject( "VideoReDo.Application" )
            if ( not IsObject(VideoReDo) ) then
                wscript.stderr.writeline("VRD version 4 or later was not detected.")
                wscript.quit 1
            end if
            VideoReDo.SetQuietMode(true)
        end if
    end if
else
    On Error Resume Next
    set VideoReDoSilent = wscript.CreateObject( "VideoReDo6.VideoReDoSilent" )
    if ( not IsObject(VideoReDoSilent) ) then
       ver = 5
       set VideoReDoSilent = wscript.CreateObject( "VideoReDo5.VideoReDoSilent" )
        if ( not IsObject(VideoReDoSilent) ) then
           ver = 4
           set VideoReDoSilent = wscript.CreateObject( "VideoReDo.VideoReDoSilent" )
            if ( not IsObject(VideoReDoSilent) ) then
                wscript.stderr.writeline("VRD version 4 or later was not detected.")
                wscript.quit 1
            end if
        end if
    end if
    set VideoReDo = VideoReDoSilent.VRDInterface
end if
On Error Goto 0

'Hard code no audio alert
if (ver = 4) then
    VideoReDo.AudioAlert = false
else
    VideoReDo.ProgramSetAudioAlert( false )
end if

' Open source file
if (ver = 4) then
    openFlag = VideoReDo.FileOpen( sourceFile )
else
    openFlag = VideoReDo.FileOpen(sourceFile, false)
end if

if openFlag = false then
    wscript.stderr.writeline( "? Unable to open file/project: " + sourceFile )
    wscript.quit 3
end if

' Start Ad Scan
if (ver = 4) then
    scanStarted = VideoReDo.StartAdScan( 0, 0, 1 )
elseif( ver = 5 ) then
    'VideoReDo.AdScanSetParameter 0, false
    'VideoReDo.AdScanSetParameter 1, false
    VideoReDo.AdScanSetParameter 2, true
    VideoReDo.AdScanToggleScan()
    scanStarted = VideoReDo.AdScanIsScanning()
else    ' ver = 6
    VideoReDo.InteractiveAdScanSetParameter 2, true
    VideoReDo.InteractiveAdScanToggleScan()
    scanStarted = VideoReDo.InteractiveAdScanIsScanning()
end if

if scanStarted = false then
    wscript.stderr.writeline("? Unable to start Ad Scan on file: " + sourceFile )
    wscript.quit 4
end if

' Wait until scan done and output % complete to stdout
if (ver = 4) then
    fileDuration = VideoRedo.GetProgramDuration()*1000
    while( VideoRedo.IsScanInProgress() )
        percent = "Progress: " & Int(VideoReDo.GetCursorTimeMsec()*100/fileDuration) & "%"
        wscript.echo(percent)
        if not fso.FileExists(lockFile) then
            VideoReDo.AbortOutput()
            endtime = DateAdd("s", 15, Now)
            while( VideoReDo.IsScanInProgress() And (Now < endtime) )
                wscript.sleep 500
            wend
            VideoReDo.Close()
            wscript.quit 5
        end if
        wscript.sleep 2000
    wend
elseif( ver = 5 ) then
    fileDuration = VideoRedo.FileGetOpenedFileDuration()
    while( VideoRedo.AdScanIsScanning() )
        percent = "Progress: " & Int(VideoReDo.NavigationGetCursorTime()*100/fileDuration) & "%"
        wscript.echo(percent)
        if not fso.FileExists(lockFile) then
            VideoReDo.OutputAbort()
            endtime = DateAdd("s", 15, Now)
            while( VideoRedo.AdScanIsScanning() And (Now < endtime) )
                wscript.sleep 500
            wend
            VideoReDo.ProgramExit()
            wscript.quit 5
        end if
        wscript.sleep 2000
    wend
else    ' ver = 6
    fileDuration = VideoRedo.FileGetOpenedFileDuration()
    navigationCursorTime = 0
    while( VideoRedo.InteractiveAdScanIsScanning() and navigationCursorTime < fileDuration)
        navigationCursorTime = VideoReDo.NavigationGetCursorTime()
        percent = "Progress: " & Int(navigationCursorTime*100/fileDuration) & "%"
        wscript.echo(percent)
        if not fso.FileExists(lockFile) then
            VideoReDo.OutputAbort()
            endtime = DateAdd("s", 15, Now)
            while( VideoRedo.InteractiveAdScanIsScanning() And (Now < endtime) )
                wscript.sleep 500
            wend
            VideoReDo.ProgramExit()
            wscript.quit 5
        end if
       wscript.sleep 2000
    wend
end if

' Write output project file
if (ver = 4) then
    VideoReDo.WriteProjectFile(destFile)
else
    VideoReDo.FileSaveProjectAs(destFile)
end if

' Close VRD
if (ver = 4) then
    VideoReDo.Close()
else
    VideoReDo.ProgramExit()
end if

' Exit with status 0
wscript.echo( "   Output complete to: " + destFile )
wscript.quit 0
