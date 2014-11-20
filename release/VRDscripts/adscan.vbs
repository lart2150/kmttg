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

' Create VideoReDo object and open the source project / file.
ver = 5
if (VrdAllowMultiple) then
   ' Try VRD 5 1st then older VRD
   On Error Resume Next
   Set VideoReDo = WScript.CreateObject( "VideoReDo5.Application" )
   On Error Goto 0
   if ( not IsObject(VideoReDo) ) then
      Set VideoReDo = wscript.CreateObject( "VideoReDo.Application" )
      VideoReDo.SetQuietMode(true)
      ver = 4
   end if
else
   ' Try VRD 5 1st then older VRD
   On Error Resume Next
   set VideoReDoSilent = wscript.CreateObject( "VideoReDo5.VideoReDoSilent" )
   On Error Goto 0
   if ( not IsObject(VideoReDoSilent) ) then
      ver = 4
      set VideoReDoSilent = wscript.CreateObject( "VideoReDo.VideoReDoSilent" )
   end if
   set VideoReDo = VideoReDoSilent.VRDInterface
end if

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
else
   'VideoReDo.AdScanSetParameter 0, false
   'VideoReDo.AdScanSetParameter 1, false
   VideoReDo.AdScanSetParameter 2, true
   VideoReDo.AdScanToggleScan()
   scanStarted = VideoReDo.AdScanIsScanning()
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
else
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
