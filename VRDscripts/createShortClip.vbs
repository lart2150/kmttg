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
if (VrdAllowMultiple) then
   Set VideoReDo = wscript.CreateObject( "VideoReDo.Application" )
   VideoReDo.SetQuietMode(true)
else
   Set VideoReDoSilent = wscript.CreateObject( "VideoReDo.VideoReDoSilent" )
   set VideoReDo = VideoReDoSilent.VRDInterface
end if

'Hard code no audio alert
VideoReDo.AudioAlert = false

' Open source file
openFlag = VideoReDo.FileOpen( sourceFile )

if openFlag = false then
   wscript.stderr.writeline( "? Unable to open file: " + sourceFile )
   wscript.quit 2
end if

' Open output file and start processing.
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
else
   outputFlag = true
   profileName = "MPEG2 Program Stream"
   outputXML = VideoReDo.SaveJoinerWithProfile( destFile, profileName )
   if ( left(outputXML,1) = "*" ) then
      outputFlag = false
   end if
end if

if outputFlag = false then
   wscript.stderr.writeline("? Problem opening output file: " + destFile )
   wscript.quit 4
end if

' Wait until output done
while( VideoRedo.IsOutputInProgress() )
   wscript.sleep 1000
wend

' Close VRD
VideoReDo.Close()
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
