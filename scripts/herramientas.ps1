$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path $PSScriptRoot -Parent
function Get-JarDelProyecto {
    foreach ($relativo in @('target/facturacion-1.0.0.jar','dist/facturacion-homologacion-1.0.0.jar','dist/facturacion-1.0.0.jar')) {
        $ruta = Join-Path $ProjectRoot $relativo
        if (Test-Path -LiteralPath $ruta -PathType Leaf) { return $ruta }
    }
}
function Set-JavaDelProyecto {
    if (-not $env:JAVA_HOME -and (Test-Path 'C:/Program Files/Java/jdk-26/bin/java.exe')) {
        $env:JAVA_HOME = 'C:/Program Files/Java/jdk-26'
    }
    if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME 'bin/javac.exe'))) {
        throw 'Defina JAVA_HOME con la carpeta de su JDK 26.'
    }
}
function Get-MavenDelProyecto {
    $mavenCommand = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($mavenCommand) { return $mavenCommand.Source }
    $jetbrains = Join-Path $env:ProgramFiles 'JetBrains'
    if (Test-Path $jetbrains) {
        foreach ($installation in Get-ChildItem -LiteralPath $jetbrains -Directory) {
            $candidate = Join-Path $installation.FullName 'plugins/maven/lib/maven3/bin/mvn.cmd'
            if (Test-Path $candidate) { return $candidate }
        }
    }
    throw 'Configure mvn en el PATH o use el Maven incluido en IntelliJ.'
}
function Get-PostgresBin {
    if ($env:PG_BIN) { return $env:PG_BIN }
    $command = Get-Command pg_ctl.exe -ErrorAction SilentlyContinue
    if ($command) { return Split-Path $command.Source -Parent }
    $candidate = 'C:/Program Files/PostgreSQL/18/bin'
    if (Test-Path $candidate) { return $candidate }
    throw 'Defina PG_BIN con la carpeta bin de PostgreSQL.'
}
function Import-Conexion {
    $config = Join-Path $ProjectRoot '.local/conexion.ps1'
    if (Test-Path $config) { . $config }
    if (-not $env:DB_PASSWORD) { throw 'Configure DB_URL, DB_USER y DB_PASSWORD; vea README.md.' }
}
