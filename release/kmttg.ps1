
$kttgDir = Split-Path $MyInvocation.MyCommand.Path -Parent
$javaFXweb = Get-Childitem –Path $kttgDir -Include "javafx.web.jar" -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1

if ($javaFXweb) {
    $javaFX = $javaFXweb.DirectoryName;
    java --module-path "$javaFX" --add-modules javafx.web -jar "$kttgDir\kmttg.jar" $args
} else {
    java -jar "$kttgDir\kmttg.jar" $args
}