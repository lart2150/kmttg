
$kttgDir = Split-Path $MyInvocation.MyCommand.Path -Parent

java -jar "$kttgDir\kmttg.jar" $args
