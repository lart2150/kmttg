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
   wscript.stderr.writeline( "? Unable to open file/project: " + sourceFile )
   wscript.quit 3
end if

' Open output file and start processing.
outputFlag = false
outputXML = VideoReDo.FileSaveProfile( destFile, profileName )
if ( left(outputXML,1) = "*" ) then
   wscript.stderr.writeline("? Problem opening output file: " + outputXML )
   wscript.stderr.writeline(outputXML)
   wscript.quit 4
else
   outputFlag = true
end if

if outputFlag = false then
   wscript.stderr.writeline("? Problem opening output file: " + destFile )
   wscript.quit 4
end if

' Wait until output done and output % complete to stdout
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

' Close VRD
VideoReDo.Close()

' Exit with status 0
wscript.echo( "   Output complete to: " + destFile )
wscript.quit 0
