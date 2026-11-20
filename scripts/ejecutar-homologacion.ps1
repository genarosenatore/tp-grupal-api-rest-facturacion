param([ValidateRange(1024,65535)][int]$Puerto = 8081, [string]$RutaJar, [ValidatePattern('^[a-zA-Z0-9_-]+$')][string]$NombreBase = 'demo-h2')
. "$PSScriptRoot/herramientas.ps1"
Set-JavaDelProyecto
$jarHomologacion = if ($RutaJar) { (Resolve-Path -LiteralPath $RutaJar -ErrorAction Stop).Path } else { Get-JarDelProyecto }
if (-not $jarHomologacion) { throw 'Compile primero el frontend y ejecute scripts/maven.ps1 package.' }
$localHomologacion = Join-Path $ProjectRoot '.local/arca-homologacion'
foreach ($nombreHomologacion in @('certificado-homo.pem','privada-homo.pem')) {
    if (-not (Test-Path -LiteralPath (Join-Path $localHomologacion $nombreHomologacion))) { throw "Falta $nombreHomologacion de homologación." }
}
$h2Homologacion = Get-ChildItem -LiteralPath (Join-Path $env:USERPROFILE '.m2/repository/com/h2database/h2') -Filter 'h2-*.jar' -Recurse |
    Where-Object Name -NotMatch 'sources|javadoc' | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $h2Homologacion) { throw 'Ejecute las pruebas Maven para disponer de H2 de demostración.' }
$pruebaPuerto = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $Puerto)
try {
    $pruebaPuerto.ExclusiveAddressUse = $true
    $pruebaPuerto.Start()
} catch [Net.Sockets.SocketException] {
    throw "No se puede usar el puerto local $Puerto. Use otro con -Puerto."
} finally { $pruebaPuerto.Stop() }
# Base persistente exclusiva del laboratorio. No modifica la conexión PostgreSQL del proyecto.
$semillaHomologacion = (Get-Content -LiteralPath (Join-Path $ProjectRoot 'src/test/resources/datos-flujos.sql') -Encoding UTF8 -Raw) -replace '(?im)^INSERT INTO ([a-z_]+\([^)]*\)) VALUES', 'MERGE INTO $1 KEY(id) VALUES'
# El laboratorio sólo ofrece el punto configurado. Los puntos históricos permanecen
# en la base para conservar las referencias de comprobantes anteriores.
$semillaHomologacion = $semillaHomologacion -replace '(?im)^MERGE INTO punto_venta[^\r\n]*', ''
$semillaHomologacion += "`nMERGE INTO punto_venta(id,codigo,descripcion) KEY(id) VALUES (2,'0002','Entorno de prueba - Homologacion');`n"
# Receptor ficticio para cargar A y B con CUIT, sin modificar clientes existentes.
$semillaHomologacion += @'

ALTER TABLE cliente ALTER COLUMN id RESTART WITH (SELECT COALESCE(MAX(id),0)+1 FROM cliente);
INSERT INTO cliente(nombre,documento) SELECT 'Cliente con CUIT - prueba A/B','20123456786'
WHERE NOT EXISTS (SELECT 1 FROM cliente WHERE documento='20123456786');
'@
$archivoSemillaHomologacion = Join-Path $localHomologacion 'catalogos-demo-h2.sql'
[IO.File]::WriteAllText($archivoSemillaHomologacion,$semillaHomologacion,[Text.UTF8Encoding]::new($false))
$estadoHomologacion = Get-Content -LiteralPath (Join-Path $localHomologacion 'estado-homologacion.json') -Encoding UTF8 -Raw | ConvertFrom-Json
$cuitHomologacion = if ($estadoHomologacion.cuit) { [string]$estadoHomologacion.cuit } else { '' }
$argumentosHomologacion = @(
    ('-Dloader.path=' + $h2Homologacion.FullName), '-cp', $jarHomologacion,
    'org.springframework.boot.loader.launch.PropertiesLauncher',
    "--server.port=$Puerto", '--server.address=127.0.0.1',
    ('--spring.datasource.url=jdbc:h2:file:' + (Join-Path $localHomologacion $NombreBase).Replace('\','/') + ';MODE=PostgreSQL;DB_CLOSE_ON_EXIT=FALSE'),
    '--spring.datasource.username=sa', '--spring.datasource.password=', '--spring.datasource.driver-class-name=org.h2.Driver',
    '--spring.jpa.hibernate.ddl-auto=update', '--spring.flyway.enabled=false', '--spring.sql.init.mode=always',
    ('--spring.sql.init.data-locations=file:' + $archivoSemillaHomologacion.Replace('\','/')),
    '--spring.jpa.defer-datasource-initialization=true', '--arca.homologacion.enabled=true',
    ('--arca.homologacion.directorio=' + $localHomologacion), '--arca.homologacion.punto-venta=2',
    ("--facturacion.emisor.cuit=$cuitHomologacion")
)
# Perfil comercial de práctica; no se atribuyen estos datos a una inscripción real.
# Un archivo privado opcional puede reemplazarlo sin editar ni distribuir datos personales.
$perfilEmisorHomologacion = Join-Path $localHomologacion 'emisor.properties'
if (-not (Test-Path -LiteralPath $perfilEmisorHomologacion)) {
    $contenidoEmisorHomologacion = @'
facturacion.emisor.razon-social=Librería Aula TP
facturacion.emisor.domicilio=Av. del Aprendizaje 123, Ciudad de Prueba, Argentina
facturacion.emisor.condicion-iva=Responsable Monotributo (demostración)
facturacion.emisor.ingresos-brutos=No contribuyente (dato de demostración)
facturacion.emisor.inicio-actividades=2026-01-01
facturacion.emisor.datos-demostracion=true
'@
    # .properties usa escapes Unicode para conservar acentos en cualquier sistema.
    $contenidoEmisorHomologacion = [regex]::Replace($contenidoEmisorHomologacion,'[^\x00-\x7F]', { param($letraEmisor) '\u{0:x4}' -f [int][char]$letraEmisor.Value })
    [IO.File]::WriteAllText($perfilEmisorHomologacion,$contenidoEmisorHomologacion,[Text.UTF8Encoding]::new($false))
}
$argumentosHomologacion += ('--spring.config.import=file:' + $perfilEmisorHomologacion.Replace('\','/'))
# Start-Process une la lista en una línea de comandos. Las rutas pueden contener espacios.
$argumentosHomologacion = ($argumentosHomologacion | ForEach-Object { '"' + $_ + '"' }) -join ' '
New-Item -ItemType Directory -Force -Path (Join-Path $ProjectRoot 'tmp') | Out-Null
$procesoHomologacion = Start-Process -FilePath (Join-Path $env:JAVA_HOME 'bin/java.exe') -ArgumentList $argumentosHomologacion -WorkingDirectory $ProjectRoot -WindowStyle Hidden -PassThru `
    -RedirectStandardOutput (Join-Path $ProjectRoot 'tmp/homologacion-backend.log') -RedirectStandardError (Join-Path $ProjectRoot 'tmp/homologacion-backend-error.log')
$procesoHomologacion.Id | Set-Content -LiteralPath (Join-Path $ProjectRoot 'tmp/homologacion-backend.pid')
Write-Output "Homologación iniciada: http://127.0.0.1:$Puerto (PID $($procesoHomologacion.Id))."
Write-Output 'Base H2 persistente exclusiva de pruebas; endpoints fiscales de homologación.'
