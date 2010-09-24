'Create VideoReDo object.
Set VideoReDoSilent = wscript.CreateObject( "VideoReDo.VideoReDoSilent" )
set VideoReDo = VideoReDoSilent.VRDInterface

' Check for proper version
version = GetVersion(VideoReDo.VersionNumber)
If version < 4202595 Then
   wscript.stderr.writeline("Encoding support requires VRD version 4.20.2.595 or later")
   wscript.stderr.writeline("Version you are running is: " & VideoReDo.VersionNumber)
   wscript.quit 1
End If

' Get number of profiles available.
numProfiles = VideoReDo.GetProfilesCount()
if ( numProfiles > 0 ) then
   for i = 1 to numProfiles
      if (VideoReDo.IsProfileEnabled(i)) then
         wscript.echo(VideoReDo.GetProfileXML(i))
      end if
   next
end if

' Close VRD
VideoReDo.Close()

' Exit with status 0
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
