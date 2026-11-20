param([switch]$Anular)
$ErrorActionPreference = 'Stop'
$raizPruebaHomo = Split-Path $PSScriptRoot -Parent
$apiPruebaHomo = 'http://127.0.0.1:8081/api/facturas'
$carpetaPruebaHomo = Join-Path $raizPruebaHomo '.local/arca-homologacion'
$registroPruebaHomo = Join-Path $carpetaPruebaHomo 'prueba-factura-c.json'
if (Test-Path -LiteralPath $registroPruebaHomo) {
    $idPruebaHomo = (Get-Content -LiteralPath $registroPruebaHomo -Raw | ConvertFrom-Json).facturaId
    $facturaPruebaHomo = Invoke-RestMethod -Uri "$apiPruebaHomo/$idPruebaHomo" -MaximumRedirection 0 -TimeoutSec 15
} else {
    $cuerpoPruebaHomo = @{
        fecha = (Get-Date -Format 'yyyy-MM-dd'); clienteId = 1; condicionIvaId = 1;
        puntoVentaId = 2; tipoMonedaId = 1; tipoComprobante = '11';
        tipoDocumentoReceptor = 96; condicionIvaReceptorId = 5;
        observaciones = 'Prueba controlada de integracion ARCA - HOMOLOGACION';
        detalles = @(@{ articuloId = 1; cantidad = 1 })
    } | ConvertTo-Json -Depth 5
    $facturaPruebaHomo = Invoke-RestMethod -Method Post -Uri $apiPruebaHomo -ContentType 'application/json' -Body $cuerpoPruebaHomo -MaximumRedirection 0 -TimeoutSec 15
    $idPruebaHomo = $facturaPruebaHomo.id
    @{ facturaId = $idPruebaHomo; entorno = 'HOMOLOGACION'; etapa = 'BORRADOR' } | ConvertTo-Json | Set-Content -LiteralPath $registroPruebaHomo -Encoding utf8
}
if ($facturaPruebaHomo.estadoArca -in @('INCIERTA','EN_PROCESO')) {
    $facturaPruebaHomo = Invoke-RestMethod -Method Post -Uri "$apiPruebaHomo/$idPruebaHomo/conciliar" -MaximumRedirection 0 -TimeoutSec 90
} elseif (-not $facturaPruebaHomo.informadaArca) {
    if ($facturaPruebaHomo.estadoArca -eq 'RECHAZADA') { throw 'La prueba anterior fue rechazada. Revisar su respuesta antes de intentar otro envio.' }
    $facturaPruebaHomo = Invoke-RestMethod -Method Post -Uri "$apiPruebaHomo/$idPruebaHomo/autorizar" -MaximumRedirection 0 -TimeoutSec 90
}
if (-not $facturaPruebaHomo.informadaArca -or $facturaPruebaHomo.ambienteArca -ne 'HOMOLOGACION') { throw 'No se obtuvo una autorizacion de homologacion confirmada.' }
$facturaPruebaHomo | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $carpetaPruebaHomo 'factura-c-autorizada.json') -Encoding utf8
$repetidaPruebaHomo = Invoke-RestMethod -Method Post -Uri "$apiPruebaHomo/$idPruebaHomo/autorizar" -MaximumRedirection 0 -TimeoutSec 15
if ($repetidaPruebaHomo.numero -ne $facturaPruebaHomo.numero -or $repetidaPruebaHomo.codigoAutorizacion -ne $facturaPruebaHomo.codigoAutorizacion) { throw 'La autorizacion repetida no conservo el comprobante original.' }
foreach ($metodoPruebaHomo in @('Delete','Put')) {
    try {
        $opcionesPruebaHomo = @{ Method=$metodoPruebaHomo; Uri="$apiPruebaHomo/$idPruebaHomo"; MaximumRedirection=0; TimeoutSec=15 }
        if ($metodoPruebaHomo -eq 'Put') { $opcionesPruebaHomo.ContentType='application/json'; $opcionesPruebaHomo.Body='{"estado":"EMITIDA","observaciones":"Intento bloqueado de prueba"}' }
        Invoke-RestMethod @opcionesPruebaHomo | Out-Null
        throw "El servidor debio bloquear $metodoPruebaHomo."
    } catch {
        if (-not $_.Exception.Response -or [int]$_.Exception.Response.StatusCode -ne 409) { throw }
    }
}
$salidaPdfPruebaHomo = Join-Path $raizPruebaHomo 'tmp/pdfs'
New-Item -ItemType Directory -Path $salidaPdfPruebaHomo -Force | Out-Null
$nombrePdfPruebaHomo = "factura-c-homologacion-$idPruebaHomo.pdf"
if ($Anular -and $facturaPruebaHomo.estado -ne 'ANULADA') {
    $caeOriginalPruebaHomo = $facturaPruebaHomo.codigoAutorizacion
    $facturaPruebaHomo = Invoke-RestMethod -Method Post -Uri "$apiPruebaHomo/$idPruebaHomo/anular" -ContentType 'application/json' -Body '{"motivo":"Anulacion controlada de homologacion del TP"}' -MaximumRedirection 0 -TimeoutSec 90
    if ($facturaPruebaHomo.estado -ne 'ANULADA' -or $facturaPruebaHomo.codigoAutorizacion -ne $caeOriginalPruebaHomo) { throw 'No se confirmo la anulacion conservando el CAE original.' }
    $facturaPruebaHomo | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $carpetaPruebaHomo 'factura-c-anulada.json') -Encoding utf8
}
if ($facturaPruebaHomo.estado -eq 'ANULADA') { $nombrePdfPruebaHomo="factura-c-homologacion-$idPruebaHomo-anulada.pdf" }
Invoke-WebRequest -Uri "$apiPruebaHomo/$idPruebaHomo/pdf" -OutFile (Join-Path $salidaPdfPruebaHomo $nombrePdfPruebaHomo) -MaximumRedirection 0 -TimeoutSec 20 | Out-Null
@{ facturaId=$idPruebaHomo; entorno='HOMOLOGACION'; etapa=$facturaPruebaHomo.estado; numero=$facturaPruebaHomo.numero; cae=$facturaPruebaHomo.codigoAutorizacion; vencimiento=$facturaPruebaHomo.vencimientoAutorizacion } | ConvertTo-Json | Set-Content -LiteralPath $registroPruebaHomo -Encoding utf8
Write-Output ("Factura C de homologacion: ID {0}; numero {1}; CAE {2}; vencimiento {3}; estado {4}; total {5}." -f $idPruebaHomo,$facturaPruebaHomo.numero,$facturaPruebaHomo.codigoAutorizacion,$facturaPruebaHomo.vencimientoAutorizacion,$facturaPruebaHomo.estado,$facturaPruebaHomo.total)
Write-Output 'Idempotencia y bloqueos PUT/DELETE verificados.'
Write-Output ('PDF: ' + (Join-Path $salidaPdfPruebaHomo $nombrePdfPruebaHomo))
