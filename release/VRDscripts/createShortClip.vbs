set Args = wscript.Arguments
if Args.Count < 2 then
   wscript.stderr.writeline( "? Invalid number of arguments")
   wscript.quit 1
end if

' Check for flags.
VrdAllowMultiple = false
for i = 1 to args.Count
   p = args(i-1)
   if p = "/m" then VrdAllowMultiple = true
next

sourceFile = args(0)
destFile   = args(1)

'Create VideoReDo object and open the source project / file.
iver = 5
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
   wscript.stderr.writeline( "? Unable to open file: " + sourceFile )
   wscript.quit 2
end if

' Open output file and start processing.
if (ver = 4) then
   VideoReDo.SetCutMode(false)
   if NOT VideoReDo.SelectScene(0, 2000) then
      wscript.stderr.writeline("Failed to select scene")
      wscript.quit 3
   end if
   VideoReDo.AddToJoiner()
   ' Save selection to mpeg2 program stream.
   'NOTE: NEWER VRD TVSUITE4 NO LONGER SUPPORTS SaveJoinerAsEx so have to use SaveJoinerWithProfile
   version = GetVersion(VideoReDo.VersionNumber)
   if version < 4205604 then
      outputFlag = VideoReDo.SaveJoinerAs( destFile )
      outputXML = ""
   else
      outputFlag = true
      profileName = "MPEG2 Program Stream"
      outputXML = VideoReDo.SaveJoinerWithProfile( destFile, profileName )
      if ( left(outputXML,1) = "*" ) then
         outputFlag = false
      end if
   end if
else
   ' TODO
   VideoReDo.EditSetMode(1)
   VideoReDo.EditSetSelectionStart(0)
   VideoReDo.EditSetSelectionEnd(2000)
   VideoReDo.EditAddSelection()
   VideoReDo.FileAddToJoiner()
   ' Save selection to mpeg2 program stream.
   profileName = "MPEG-2 Program Stream"
   outputFlag = VideoReDo.FileSaveJoinerAs( destFile, profileName )
   outputXML = ""
end if

if outputFlag = false then
   wscript.stderr.writeline("? Problem opening output file: " + destFile )
   wscript.stderr.writeline(outputXML)
   wscript.quit 4
end if

' Wait until output done
if (ver = 4) then
   while( VideoRedo.IsOutputInProgress() )
      wscript.sleep 1000
   wend
else
   while( VideoRedo.OutputGetState <> 0 )
      wscript.sleep 1000
   wend
end if

' Close VRD
if (ver = 4) then
   VideoReDo.Close()
else
   VideoReDo.ProgramExit()
end if

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
