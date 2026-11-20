param([switch]$DesdeFuentes)
. "$PSScriptRoot/herramientas.ps1"
Import-Conexion
Set-JavaDelProyecto
$jar = Get-JarDelProyecto
if (-not $DesdeFuentes -and $jar) {
    & "$env:JAVA_HOME/bin/java.exe" -jar $jar
    exit $LASTEXITCODE
}
& "$PSScriptRoot/maven.ps1" spring-boot:run
exit $LASTEXITCODE
