param([Parameter(ValueFromRemainingArguments=$true)][string[]]$MavenArgs)
. "$PSScriptRoot/herramientas.ps1"
Set-JavaDelProyecto
$mavenExe = Get-MavenDelProyecto
Push-Location $ProjectRoot
try {
    & $mavenExe @MavenArgs
    $result = $LASTEXITCODE
} finally { Pop-Location }
exit $result
