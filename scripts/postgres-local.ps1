param([ValidateSet('iniciar','detener','estado')][string]$Accion = 'iniciar', [ValidateRange(1024,65535)][int]$Puerto = 55432)
. "$PSScriptRoot/herramientas.ps1"
$pgBin = Get-PostgresBin
$localRoot = Join-Path $ProjectRoot '.local'
$dataDir = Join-Path $localRoot 'pgdata'
$configFile = Join-Path $localRoot 'conexion.ps1'
New-Item -ItemType Directory -Force -Path $localRoot | Out-Null
if ($Accion -eq 'estado') { & "$pgBin/pg_ctl.exe" -D $dataDir status; exit $LASTEXITCODE }
if ($Accion -eq 'detener') { & "$pgBin/pg_ctl.exe" -D $dataDir -m fast -w stop; exit $LASTEXITCODE }
if (-not (Test-Path (Join-Path $dataDir 'PG_VERSION'))) {
    if (Test-Path $configFile) { throw 'Ya hay una conexión configurada. Revise .local antes de crear una instancia.' }
    $password = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
    $passwordFile = Join-Path $localRoot 'init-password.tmp'
    [IO.File]::WriteAllText($passwordFile, $password)
    try {
        & "$pgBin/initdb.exe" -D $dataDir -U facturacion_app --encoding=UTF8 --locale=C --auth-host=scram-sha-256 --auth-local=scram-sha-256 "--pwfile=$passwordFile"
        if ($LASTEXITCODE -ne 0) { throw 'No se pudo inicializar PostgreSQL local.' }
    } finally { Remove-Item -LiteralPath $passwordFile -ErrorAction SilentlyContinue }
    $lines = @(
        ('$env:DB_URL = ''jdbc:postgresql://127.0.0.1:{0}/facturacion''' -f $Puerto),
        '$env:DB_USER = ''facturacion_app''',
        ('$env:DB_PASSWORD = ''{0}''' -f $password),
        ('$env:TEST_DB_URL = ''jdbc:postgresql://127.0.0.1:{0}/facturacion_test''' -f $Puerto),
        ('$env:PGPORT = ''{0}''' -f $Puerto)
    )
    $lines | Set-Content -LiteralPath $configFile -Encoding UTF8
    & "$pgBin/pg_ctl.exe" -D $dataDir -l (Join-Path $localRoot 'postgres.log') -o "-h 127.0.0.1 -p $Puerto" -w start
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo iniciar PostgreSQL; revise puerto y .local/postgres.log.' }
    $previousPassword = $env:PGPASSWORD
    $env:PGPASSWORD = $password
    try {
        foreach ($database in @('facturacion','facturacion_test')) {
            & "$pgBin/createdb.exe" -h 127.0.0.1 -p $Puerto -U facturacion_app $database
            if ($LASTEXITCODE -ne 0) { throw "No se pudo crear $database." }
        }
    } finally { $env:PGPASSWORD = $previousPassword }
    Write-Host "Bases facturacion y facturacion_test creadas en puerto $Puerto."
} else {
    # Una base existente debe reiniciarse en su puerto guardado, nunca en el 5432 por defecto.
    if (-not $PSBoundParameters.ContainsKey('Puerto') -and (Test-Path $configFile)) {
        . $configFile
        if ($env:PGPORT -notmatch '^\d+$' -or [int]$env:PGPORT -lt 1024 -or [int]$env:PGPORT -gt 65535) {
            throw 'PGPORT no es válido en .local/conexion.ps1.'
        }
        $Puerto = [int]$env:PGPORT
    }
    & "$pgBin/pg_ctl.exe" -D $dataDir status
    if ($LASTEXITCODE -ne 0) {
        & "$pgBin/pg_ctl.exe" -D $dataDir -l (Join-Path $localRoot 'postgres.log') -o "-h 127.0.0.1 -p $Puerto" -w start
        if ($LASTEXITCODE -ne 0) { throw 'No se pudo iniciar PostgreSQL.' }
    }
}
Write-Host 'Conexion en .local/conexion.ps1. No compartir esa carpeta.'
