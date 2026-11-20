"""Pruebas del filtro que protege la exportación pública."""
import contextlib
import io
import importlib.util
import json
import tempfile
import unittest
import zipfile
from pathlib import Path

especificacion = importlib.util.spec_from_file_location(
    "preparar_publicacion", Path(__file__).with_name("preparar-publicacion.py"))
publicacion = importlib.util.module_from_spec(especificacion)
especificacion.loader.exec_module(publicacion)
ARCHIVOS = publicacion.ARCHIVOS
archivos_publicos = publicacion.archivos_publicos
preparar = publicacion.preparar
revisar = publicacion.revisar
valores_privados = publicacion.valores_privados


class PublicacionTest(unittest.TestCase):
    def setUp(self):
        self.temporal = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporal.cleanup)
        self.raiz = Path(self.temporal.name)
        for nombre in ARCHIVOS:
            self.escribir(nombre, "ejemplo\n")
        self.escribir("src/main/resources/static/index.html", '<script src="/assets/actual.js"></script>')
        self.escribir("src/main/resources/static/assets/actual.js", "console.log('demo');")

    def escribir(self, nombre, contenido):
        ruta = self.raiz / nombre
        ruta.parent.mkdir(parents=True, exist_ok=True)
        ruta.write_text(contenido, encoding="utf-8")
        return ruta

    def test_excluye_privados_binarios_ocultos_y_assets_anteriores(self):
        for nombre in (".local/conexion.ps1", "src/main/resources/clave.pem",
                       "src/.env", "docs/factura.pdf", "docs/captura.png",
                       "docs/respuesta.json", "dist/app.jar", "target/app.jar",
                       "scripts/__pycache__/cache.py", "frontend/node_modules/paquete.js",
                       "src/main/resources/static/assets/anterior.js"):
            self.escribir(nombre, "privado")
        nombres = {p.relative_to(self.raiz).as_posix() for p in archivos_publicos(self.raiz)}
        self.assertEqual(nombres, set(ARCHIVOS) | {
            "src/main/resources/static/index.html", "src/main/resources/static/assets/actual.js"})

    def test_bloquea_password_cuit_cae_y_ticket_sin_mostrarlos(self):
        password = "password-ficticio-para-test"
        cuit = "20999999991"
        cae = "99999999999991"
        token = "ticket-ficticio-para-test"
        self.escribir(".local/conexion.ps1", f"$env:DB_PASSWORD = '{password}'")
        self.escribir(".local/arca-homologacion/ticket-wsfe.json", json.dumps({
            "cuit": cuit, "token": token, "datos": {"codigoAutorizacion": cae}}))
        privados = valores_privados(self.raiz)
        self.assertEqual(privados, {password, cuit, cae, token})
        for valor in privados:
            with self.subTest(valor=valor):
                ruta = self.escribir("docs/ejemplo.md", f"Dato: {valor}")
                with self.assertRaises(ValueError) as error:
                    revisar(self.raiz, [ruta], privados)
                self.assertNotIn(valor, str(error.exception))

    def test_clave_incrustada_se_bloquea_pero_literal_del_parser_se_admite(self):
        cabecera = "-----BEGIN " + "PRIVATE KEY-----"
        ruta = self.escribir("src/parser.java", f'var header = "{cabecera}";')
        revisar(self.raiz, [ruta], set())
        ruta.write_text(cabecera + "\n" + "A" * 64 + "\n", encoding="utf-8")
        with self.assertRaises(ValueError):
            revisar(self.raiz, [ruta], set())

    def test_no_confunde_identificador_parcial(self):
        ruta = self.escribir("docs/fixture.md", "1209999999912")
        revisar(self.raiz, [ruta], {"20999999991"})

    def test_rechaza_ruta_personal(self):
        ruta = self.escribir("docs/ejemplo.md", "C:" + "/Users/NombrePrivado/Downloads")
        with self.assertRaises(ValueError):
            revisar(self.raiz, [ruta], set())

    def test_zip_coincide_con_carpeta_y_no_sobrescribe(self):
        with contextlib.redirect_stdout(io.StringIO()):
            preparar(self.raiz)
        carpeta = self.raiz / "entrega/github-publico"
        with zipfile.ZipFile(self.raiz / "entrega/TP-Facturacion-publico.zip") as paquete:
            self.assertIsNone(paquete.testzip())
            self.assertEqual(set(paquete.namelist()), {
                p.relative_to(carpeta).as_posix() for p in carpeta.rglob("*") if p.is_file()})
            for nombre in paquete.namelist():
                self.assertEqual(paquete.read(nombre), (carpeta / nombre).read_bytes())
        with self.assertRaises(ValueError):
            preparar(self.raiz)

    def test_verificacion_no_genera_entrega(self):
        with contextlib.redirect_stdout(io.StringIO()):
            preparar(self.raiz, solo_verificar=True)
        self.assertFalse((self.raiz / "entrega").exists())


if __name__ == "__main__":
    unittest.main()
