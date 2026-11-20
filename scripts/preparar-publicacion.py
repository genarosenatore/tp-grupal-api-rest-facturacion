"""Exporta fuentes revisadas para GitHub, sin dependencias externas (Python 3.10+)."""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import tempfile
import zipfile
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[1]
CARPETAS = {
    "src": {".java", ".properties", ".sql", ".html", ".css", ".js", ".mjs", ".svg"},
    "scripts": {".ps1", ".py"},
    "sql": {".sql"},
    "examples": {".json"},
    "docs": {".md"},
    "frontend/src": {".ts", ".css"},
    "frontend/tests": {".ts"},
    "frontend/scripts": {".mjs"},
    "frontend/public": {".svg"},
}
ARCHIVOS = (
    "README.md", "AUDITORIA_TP.md", "pom.xml", ".gitignore", ".gitattributes",
    "frontend/index.html", "frontend/package.json", "frontend/package-lock.json",
    "frontend/tsconfig.json", "frontend/vite.config.ts",
    "frontend/playwright.config.ts", "frontend/.env.example",
)
PATRONES = (
    ("clave o certificado incorporado", re.compile(
        r"-----BEGIN (?:[A-Z ]*PRIVATE KEY|CERTIFICATE)-----\s+[A-Za-z0-9+/=]{40,}")),
    ("token de proveedor", re.compile(
        r"\b(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,}|AKIA[A-Z0-9]{16}|sk-[A-Za-z0-9_-]{30,})\b")),
    ("ruta personal", re.compile(r"[A-Z]:[/\\]+Users[/\\]+(?!Public\b|usuario\b)[^/\\\s`]+", re.I)),
    ("credencial literal", re.compile(
        r'''(?im)^\s*(?:\$env:)?(?:DB_PASSWORD|TEST_DB_PASSWORD|ARCA_TOKEN|API_KEY|CLIENT_SECRET)\s*[:=]\s*["'](?!\$|<|\{|cambiar|ejemplo)([^"'\r\n]{8,})["']''')),
)


def archivos_publicos(raiz: Path) -> list[Path]:
    archivos = {raiz / nombre for nombre in ARCHIVOS}
    indice = raiz / "src/main/resources/static/index.html"
    activos = set(re.findall(r'''(?:src|href)=["']/assets/([^"']+)["']''', indice.read_text(encoding="utf-8")))
    for carpeta, extensiones in CARPETAS.items():
        for ruta in (raiz / carpeta).rglob("*"):
            if any(parte.startswith(".") or parte in {"__pycache__", "node_modules", "test-results"}
                   for parte in ruta.relative_to(raiz / carpeta).parts):
                continue
            if ruta.suffix.lower() not in extensiones:
                continue
            if "src/main/resources/static/assets/" in ruta.as_posix() and ruta.name not in activos:
                continue
            archivos.add(ruta)
    for ruta in sorted(archivos):
        if ruta.is_symlink() or not ruta.is_file() or not ruta.resolve().is_relative_to(raiz.resolve()):
            raise ValueError(f"Archivo ausente o enlace no admitido: {ruta.relative_to(raiz)}")
    return sorted(archivos)


def valores_privados(raiz: Path) -> set[str]:
    """Lee solo claves relevantes; nunca ejecuta la configuración ni muestra valores."""
    valores: set[str] = set()
    conexion = raiz / ".local/conexion.ps1"
    if conexion.exists():
        for valor in re.findall(r"(?im)^\s*\$env:(?:DB_PASSWORD|TEST_DB_PASSWORD)\s*=\s*'((?:''|[^'])*)'", conexion.read_text(encoding="utf-8-sig")):
            if len(valor) >= 8:
                valores.add(valor.replace("''", "'"))

    def recorrer(objeto: object) -> None:
        if isinstance(objeto, dict):
            for clave, valor in objeto.items():
                if clave.lower() in {"cuit", "cuit_emisor", "cuitemisor", "token", "sign", "cae", "codigoautorizacion"} and isinstance(valor, (str, int)) and len(str(valor)) >= 8:
                    valores.add(str(valor))
                recorrer(valor)
        elif isinstance(objeto, list):
            for valor in objeto:
                recorrer(valor)

    laboratorio = raiz / ".local/arca-homologacion"
    for ruta in laboratorio.glob("*.json"):
        recorrer(json.loads(ruta.read_text(encoding="utf-8-sig")))
    perfil = laboratorio / "emisor.properties"
    if perfil.exists():
        for valor in re.findall(r"(?m)^facturacion\.emisor\.cuit\s*=\s*(\S+)", perfil.read_text(encoding="utf-8-sig")):
            valores.add(valor)
    return valores


def revisar(raiz: Path, archivos: list[Path], privados: set[str]) -> None:
    problemas = []
    for ruta in archivos:
        contenido = ruta.read_text(encoding="utf-8-sig")
        for motivo, patron in PATRONES:
            if patron.search(contenido):
                problemas.append(f"{ruta.relative_to(raiz)}: {motivo}")
        for valor in privados:
            # Los límites evitan confundir un identificador con parte de otro número.
            patron = rf"(?<!\d){re.escape(valor)}(?!\d)" if valor.isdigit() else re.escape(valor)
            if re.search(patron, contenido):
                problemas.append(f"{ruta.relative_to(raiz)}: dato de la configuración privada")
                break
    if problemas:
        raise ValueError("Publicación bloqueada (valores omitidos):\n" + "\n".join(problemas))


def preparar(raiz: Path, solo_verificar: bool = False) -> None:
    archivos = archivos_publicos(raiz)
    privados = valores_privados(raiz)
    revisar(raiz, archivos, privados)
    if solo_verificar:
        print(f"Revisión correcta: {len(archivos)} archivos de texto; sin coincidencias con datos privados conocidos.")
        return
    salida = raiz / "entrega"
    salida.mkdir(exist_ok=True)
    destino = salida / "github-publico"
    # No sobrescribir una copia que el usuario pudiera haber editado o convertido en repositorio.
    if destino.exists():
        raise ValueError("entrega/github-publico ya existe. Consérvela o renómbrela antes de generar otra copia.")
    zip_final = salida / "TP-Facturacion-publico.zip"
    if zip_final.exists():
        raise ValueError("El ZIP público ya existe. Consérvelo o renómbrelo antes de generar otro.")
    with tempfile.TemporaryDirectory(prefix="publicacion-", dir=salida) as temporal:
        carpeta = Path(temporal) / "github-publico"
        for ruta in archivos:
            copia = carpeta / ruta.relative_to(raiz)
            copia.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(ruta, copia)
        copias = sorted(ruta for ruta in carpeta.rglob("*") if ruta.is_file())
        revisar(carpeta, copias, privados)
        archivo_zip = Path(temporal) / zip_final.name
        with zipfile.ZipFile(archivo_zip, "w", zipfile.ZIP_DEFLATED) as paquete:
            for ruta in copias:
                paquete.write(ruta, ruta.relative_to(carpeta).as_posix())
        with zipfile.ZipFile(archivo_zip) as paquete:
            if paquete.testzip() is not None or len(paquete.infolist()) != len(archivos):
                raise ValueError("Falló la verificación del ZIP.")
        carpeta.rename(destino)
        archivo_zip.rename(zip_final)
    hash_zip = hashlib.sha256(zip_final.read_bytes()).hexdigest()
    zip_final.with_suffix(".zip.sha256").write_text(f"{hash_zip}  {zip_final.name}\n", encoding="utf-8")
    print(f"Carpeta para GitHub: {destino}\nZIP de fuentes: {zip_final}\nArchivos revisados: {len(archivos)}")


if __name__ == "__main__":
    argumentos = argparse.ArgumentParser(description=__doc__)
    argumentos.add_argument("--verificar", action="store_true", help="Revisa sin generar archivos.")
    opciones = argumentos.parse_args()
    try:
        preparar(RAIZ, opciones.verificar)
    except (ValueError, OSError) as error:
        argumentos.exit(1, f"{error}\n")
