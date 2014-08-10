' This script allows me to run VRD GUI for vrdreview task without having to know executable path
set Args = wscript.Arguments
if Args.Count < 1 then
   wscript.stderr.writeline( "? Invalid number of arguments")
   wscript.quit 1
end if

' Check for flags.
lockFile = ""
for i = 1 to args.Count
   p = args(i-1)
   if left(p,3)="/l:" then lockFile = mid(p,4)
next

' Check that a lock file name was given
if ( lockFile = "" ) then
   wscript.stderr.writeline( "? Lock file (/l:) not given" )
   wscript.quit 2
end if

Set fso = CreateObject("Scripting.FileSystemObject")
sourceFile = args(0)

' Create VideoReDo object
ver = 5
' Try VRD 5 1st then older VRD
On Error Resume Next
Set VideoReDo = WScript.CreateObject( "VideoReDo5.Application" )
On Error Goto 0
if ( not IsObject(VideoReDo) ) then
   Set VideoReDo = wscript.CreateObject( "VideoReDo.Application" )
   ver = 4
end if

' Open source project file
if (ver = 4) then
   openFlag = VideoReDo.FileOpen( sourceFile )
else
   openFlag = VideoReDo.FileOpen(sourceFile, false)
end if

if openFlag = false then
   wscript.stderr.writeline( "? Unable to open file/project: " + sourceFile )
   wscript.quit 3
end if

' Wait until VideoReDo terminates
On Error Resume Next
if (ver = 4) then
   while (TypeName(VideoReDo.VersionNumber) = "String")
      if Err then
         wscript.quit 0
      end if
      if not fso.FileExists(lockFile) then
         VideoReDo.Close()
         wscript.quit 0
      end if
      wscript.sleep 1000
   wend
else
   while (TypeName(VideoReDo.ProgramGetVersionNumber()) = "String")
      if Err then
         wscript.quit 0
      end if
      if not fso.FileExists(lockFile) then
         VideoReDo.ProgramExit()
         wscript.quit 0
      end if
      wscript.sleep 1000
   wend
end if

wscript.quit 0