'Create VideoReDo object.
' Try VRD 5 1st then older VRD
ver = 5
On Error Resume Next
set VideoReDoSilent = wscript.CreateObject( "VideoReDo5.VideoReDoSilent" )
On Error Goto 0
if ( not IsObject(VideoReDoSilent) ) then
   ver = 4
   set VideoReDoSilent = wscript.CreateObject( "VideoReDo.VideoReDoSilent" )
end if
set VideoReDo = VideoReDoSilent.VRDInterface

if (ver = 4) then
   ' Check for proper version
   version = GetVersion(VideoReDo.VersionNumber)
   If version < 4202595 Then
      wscript.stderr.writeline("Encoding support requires VRD version 4.20.2.595 or later")
      wscript.stderr.writeline("Version you are running is: " & VideoReDo.VersionNumber)
      wscript.quit 1
   End If
end if

' Get number of profiles available.
if (ver = 4) then
   numProfiles = VideoReDo.GetProfilesCount()
   if ( numProfiles > 0 ) then
      for i = 1 to numProfiles
         if (VideoReDo.IsProfileEnabled(i)) then
            wscript.echo(VideoReDo.GetProfileXML(i))
         end if
      next
   end if
else
   numProfiles = VideoReDo.ProfilesGetCount()
   if ( numProfiles > 0 ) then
      for i = 0 to numProfiles-1
         if (VideoReDo.ProfilesGetProfileEnabled(i)) then
            wscript.echo(VideoReDo.ProfilesGetProfileXML(i))
         end if
      next
   end if
end if

' Close VRD
if (ver = 4) then
   VideoReDo.Close()
else
   VideoReDo.ProgramExit()
end if

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
