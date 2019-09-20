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
end if
On Error Goto 0

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