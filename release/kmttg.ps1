
$kttgDir = Split-Path $MyInvocation.MyCommand.Path -Parent

# Read optional JVM parameters, one per line, from kmttg.vmoptions
$vmOptions = @()
$vmOptionsFile = Join-Path $kttgDir "kmttg.vmoptions"
if (Test-Path $vmOptionsFile) {
    $vmOptions = Get-Content $vmOptionsFile | Where-Object { $_.Trim() -ne "" -and -not $_.Trim().StartsWith("#") }
}

java @vmOptions -jar "$kttgDir\kmttg.jar" $args
