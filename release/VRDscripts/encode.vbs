set Args = wscript.Arguments
if Args.Count < 2 then
   wscript.stderr.writeline( "? Invalid number of arguments")
   wscript.quit 1
end if

' Check for flags.
VrdAllowMultiple = false
lockFile = ""
profileName  = ""
for i = 1 to args.Count
   p = args(i-1)
   if left(p,3)="/l:" then lockFile = mid(p,4)
   if left(p,3)="/p:" then profileName = mid(p,4)
   if p = "/m" then VrdAllowMultiple = true
next

' Check that a lock file name was given
if ( lockFile = "" ) then
   wscript.stderr.writeline( "? Lock file (/l:) not given" )
   wscript.quit 2
end if

' Check that a profile name was given
if ( profileName = "" ) then
   wscript.stderr.writeline( "? Profile name not given" )
   wscript.quit 2
end if

Set fso = CreateObject("Scripting.FileSystemObject")
sourceFile = args(0)
destFile   = args(1)

'Create VideoReDo object and open the source project / file.
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

' Open output file and start processing.
if (ver = 4) then
   outputFlag = false
   outputXML = VideoReDo.FileSaveProfile( destFile, profileName )
   if ( left(outputXML,1) = "*" ) then
      wscript.stderr.writeline("? Problem opening output file: " + outputXML )
      wscript.stderr.writeline(outputXML)
      wscript.quit 4
   else
      outputFlag = true
   end if
else
   outputFlag = VideoReDo.FileSaveAs( destFile, profileName )
end if

if outputFlag = false then
   wscript.stderr.writeline("? Problem opening output file: " + destFile )
   wscript.quit 4
end if

' Wait until output done and output % complete to stdout
if (ver = 4) then
   while( VideoRedo.IsOutputInProgress() )
      percent = "Progress: " & Int(VideoReDo.OutputPercentComplete) & "%"
      wscript.echo(percent)
      if not fso.FileExists(lockFile) then
         VideoReDo.AbortOutput()
         endtime = DateAdd("s", 15, Now)
         while( VideoReDo.IsOutputInProgress() And (Now < endtime) )
            wscript.sleep 500
         wend
         VideoReDo.Close()
         wscript.quit 5
      end if
      wscript.sleep 2000
   wend
else
   while( VideoRedo.OutputGetState <> 0 )
      percent = "Progress: " & Int(VideoReDo.OutputGetPercentComplete()) & "%"
      wscript.echo(percent)
      if not fso.FileExists(lockFile) then
         VideoReDo.OutputAbort()
		   endtime = DateAdd("s", 15, Now)
		   while( VideoReDo.OutputGetState <> 0 And (Now < endtime) )
			   wscript.sleep 500
		   wend
         VideoReDo.ProgramExit()
         wscript.quit 5
      end if
      wscript.sleep 2000
   wend
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
