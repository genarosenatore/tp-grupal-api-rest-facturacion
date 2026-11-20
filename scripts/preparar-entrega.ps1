param([switch]$SoloVerificar)
$ErrorActionPreference = 'Stop'
$argumentosEntrega = @((Join-Path $PSScriptRoot 'preparar-publicacion.py'))
if ($SoloVerificar) { $argumentosEntrega += '--verificar' }
& python @argumentosEntrega
if ($LASTEXITCODE -ne 0) { throw 'No se pudo preparar la entrega pública. Revise los mensajes anteriores.' }
