set Args = wscript.Arguments
if Args.Count < 2 then
   wscript.stderr.writeline( "? Invalid number of arguments")
   wscript.quit 1
end if

' Check for flags.
VrdAllowMultiple = false
lockFile = ""
profileName = "MPEG2 Program Stream"
profileNum = 1
c = "mpeg"
v = "mpeg2video"
for i = 1 to args.Count
   p = args(i-1)
   if left(p,3)="/l:" then lockFile = mid(p,4)
   if left(p,3)="/c:" then c = mid(p,4)
   if left(p,3)="/v:" then v = mid(p,4)
   if p = "/m" then VrdAllowMultiple = true
next

' Check that a lock file name was given
if ( lockFile = "" ) then
   wscript.stderr.writeline( "? Lock file (/l:) not given" )
   wscript.quit 2
end if

'  Decide on output types
if ( c = "mpegts" ) then
   profileName = "MPEG2 Transport Stream"
   profileNum = 4
   if ( v = "h264" ) then
      profileName = "H.264 Transport Stream"
   end if
end if
if ( c = "mp4" ) then
   profileName = "H.264 MP4"
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
   openFlag = VideoReDo.FileOpenBatch( sourceFile )
else
   openFlag = VideoReDo.FileOpen(sourceFile, false)
end if

if openFlag = false then
   wscript.stderr.writeline( "? Unable to open file/project: " + sourceFile )
   wscript.quit 3
end if

' Open output file and start processing.
'NOTE: NEWER VRD TVSUITE4 NO LONGER SUPPORTS FileSaveAsEx so have to use FileSaveProfile
if (ver = 4) then
   version = GetVersion(VideoReDo.VersionNumber)
   if version < 4205604 then
      outputFlag = VideoReDo.FileSaveAsEx( destFile, profileNum )
      outputXML = ""
   else
      outputFlag = true
      outputXML = VideoReDo.FileSaveProfile( destFile, profileName )
      if ( left(outputXML,1) = "*" ) then
         outputFlag = false
      end if
   end if
else
   ' V5 VRD has dash in MPEG2 profile names
   if (profileName = "MPEG2 Program Stream") then
      profileName = "MPEG-2 Program Stream"
   end if
   if (profileName = "MPEG2 Transport Stream") then
      profileName = "MPEG-2 Transport Stream"
   end if
   outputFlag = VideoReDo.FileSaveAs( destFile, profileName )
   outputXML = ""
end if

if outputFlag = false then
   wscript.stderr.writeline("? Problem opening output file: " + destFile )
   wscript.stderr.writeline(outputXML)
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

function GetVersion(string)
   version = 0
   Set objRE = New RegExp
   With objRE
      .Pattern = "^(\S+).+$"
      .IgnoreCase = True
      .Global = False
   End With
   Set objMatch = objRE.Execute( string )
   If objMatch.Count = 1 Then
      v = objMatch.Item(0).Submatches(0)
      version = Replace(v, ".", "")
   End If
   Set objRE = Nothing
   Set objMatch = Nothing
   GetVersion = version
end function
