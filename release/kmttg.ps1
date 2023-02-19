
$kttgDir = Split-Path $MyInvocation.MyCommand.Path -Parent
$javaFXweb = Get-Childitem –Path $kttgDir -Include "javafx.web.jar" -Recurse -ErrorAction SilentlyContinue

if ($javaFXweb) {
    $javaFX = Split-Path $javaFXweb -Parent;
    java --module-path "$javaFX" --add-modules javafx.web -jar "$kttgDir\kmttg.jar" $args
} else {
    java -jar "$kttgDir\kmttg.jar" $args
}