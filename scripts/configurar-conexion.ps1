param([string]$DatabaseHost='127.0.0.1',[int]$Puerto=5432,[string]$Usuario='facturacion_app')
. "$PSScriptRoot/herramientas.ps1"
if ($DatabaseHost -notmatch '^[a-zA-Z0-9.-]+$') { throw 'Host no valido.' }
$secret = Read-Host 'Contrasena del usuario de la aplicacion (no se muestra)' -AsSecureString
$credential = [PSCredential]::new($Usuario,$secret)
$password = $credential.GetNetworkCredential().Password.Replace("'","''")
$localRoot = Join-Path $ProjectRoot '.local'
New-Item -ItemType Directory -Force -Path $localRoot | Out-Null
@(
    ('$env:DB_URL = ''jdbc:postgresql://{0}:{1}/facturacion''' -f $DatabaseHost,$Puerto),
    ('$env:DB_USER = ''{0}''' -f $Usuario.Replace("'","''")),
    ('$env:DB_PASSWORD = ''{0}''' -f $password),
    ('$env:TEST_DB_URL = ''jdbc:postgresql://{0}:{1}/facturacion_test''' -f $DatabaseHost,$Puerto),
    ('$env:PGPORT = ''{0}''' -f $Puerto)
) | Set-Content -LiteralPath (Join-Path $localRoot 'conexion.ps1') -Encoding UTF8
Write-Host 'Conexion guardada localmente. No comparta .local.'
