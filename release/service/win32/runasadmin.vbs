set Args = wscript.Arguments
Set UAC = CreateObject("Shell.Application")
UAC.ShellExecute "cmd", "/c " + """" + Args(0) + """", "", "runas", 1