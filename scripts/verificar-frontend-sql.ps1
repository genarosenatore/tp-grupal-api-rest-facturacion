param([Parameter(Mandatory)][ValidateRange(1, [long]::MaxValue)][long]$FacturaId)
. "$PSScriptRoot/herramientas.ps1"
Import-Conexion
$pgBinVerificacion = Get-PostgresBin
$uriVerificacion = [Uri]($env:DB_URL -replace '^jdbc:', '')
$passwordAnterior = $env:PGPASSWORD
$env:PGPASSWORD = $env:DB_PASSWORD
try {
    $consultaVerificacion = "SELECT current_database() AS base, id, estado, fecha, neto, iva, total, observaciones, fecha_anulacion FROM factura_venta WHERE id = $FacturaId; SELECT factura_id, descripcion, cantidad, precio_unitario, neto, iva, total FROM factura_detalle WHERE factura_id = $FacturaId ORDER BY id;"
    & "$pgBinVerificacion/psql.exe" -X -h $uriVerificacion.Host -p $uriVerificacion.Port -U $env:DB_USER -d $uriVerificacion.AbsolutePath.TrimStart('/') -v ON_ERROR_STOP=1 -c $consultaVerificacion
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo consultar PostgreSQL.' }
} finally { $env:PGPASSWORD = $passwordAnterior }
