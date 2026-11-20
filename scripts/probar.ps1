. "$PSScriptRoot/herramientas.ps1"
Import-Conexion
if (-not $env:TEST_DB_URL) { throw 'Configure TEST_DB_URL con una base exclusiva terminada en _test.' }
$env:TEST_DB_USER = $env:DB_USER
$env:TEST_DB_PASSWORD = $env:DB_PASSWORD
& "$PSScriptRoot/maven.ps1" test
exit $LASTEXITCODE
