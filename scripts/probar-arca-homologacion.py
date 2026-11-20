"""Comprueba WSAA con el certificado local de homologación y conserva el ticket.

Requiere cryptography. No solicita CAE ni emite comprobantes.
"""
from __future__ import annotations

import base64
import json
import re
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlparse
from urllib.request import HTTPRedirectHandler, Request, build_opener
from xml.etree import ElementTree as ET

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.serialization import pkcs7
from cryptography.x509.oid import NameOID

RAIZ = Path(__file__).resolve().parents[1]
LOCAL = RAIZ / ".local" / "arca-homologacion"
WSAA = "https://wsaahomo.afip.gov.ar/ws/services/LoginCms"
WSFE = "https://wswhomo.afip.gov.ar/wsfev1/service.asmx"
WSFE_NS = "http://ar.gov.afip.dif.FEV1/"
HOSTS = {"wsaahomo.afip.gov.ar", "wswhomo.afip.gov.ar"}
SOAP = "http://schemas.xmlsoap.org/soap/envelope/"
WSDL = "http://schemas.xmlsoap.org/wsdl/"
SOAP_BINDING = "http://schemas.xmlsoap.org/wsdl/soap/"


class SoloHomologacion(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        validar_url(newurl)
        return super().redirect_request(req, fp, code, msg, headers, newurl)


def validar_url(url: str) -> None:
    parsed = urlparse(url)
    if parsed.scheme != "https" or parsed.hostname not in HOSTS:
        raise ValueError("El diagnóstico solo permite los endpoints HTTPS de WSAA/WSFE homologación.")


def xml_seguro(data: bytes) -> ET.Element:
    if len(data) > 512_000 or re.search(br"<!\s*(DOCTYPE|ENTITY)", data, re.I):
        raise ValueError("Respuesta XML no admitida.")
    return ET.fromstring(data)


def elemento(root: ET.Element, nombre: str) -> ET.Element | None:
    return next((node for node in root.iter() if node.tag.rsplit("}", 1)[-1] == nombre), None)


def texto(root: ET.Element, nombre: str) -> str:
    node = elemento(root, nombre)
    return "" if node is None else (node.text or "").strip()


def fecha(value: str) -> datetime:
    parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if parsed.tzinfo is None:
        raise ValueError("La fecha del ticket no incluye zona horaria.")
    return parsed.astimezone(timezone.utc)


def falla_soap(body: bytes) -> str:
    try:
        root = xml_seguro(body)
        message = texto(root, "faultstring")
        if message:
            return re.sub(r"[A-Za-z0-9+/=]{40,}", "[dato omitido]", message)[:500]
    except (ET.ParseError, ValueError):
        pass
    return "No se recibió un ticket de acceso válido."


def solicitar(req: Request) -> bytes:
    validar_url(req.full_url)
    try:
        with build_opener(SoloHomologacion()).open(req, timeout=20) as response:
            validar_url(response.url)
            return response.read(512_001)
    except HTTPError as error:
        message = falla_soap(error.read(512_001))
        raise RuntimeError(f"ARCA homologación respondió HTTP {error.code}: {message}") from None
    except URLError as error:
        raise RuntimeError(f"No se pudo conectar con ARCA homologación: {error.reason}") from None


def main() -> None:
    cert = x509.load_pem_x509_certificate((LOCAL / "certificado-homo.pem").read_bytes())
    key = serialization.load_pem_private_key((LOCAL / "privada-homo.pem").read_bytes(), password=None)
    ahora = datetime.now(timezone.utc)
    if not cert.not_valid_before_utc <= ahora <= cert.not_valid_after_utc:
        raise ValueError("El certificado local no está vigente.")
    if cert.issuer.get_attributes_for_oid(NameOID.COMMON_NAME)[0].value != "Computadores Test":
        raise ValueError("El certificado local no identifica al emisor de homologación esperado.")
    formato = serialization.PublicFormat.SubjectPublicKeyInfo
    if key.public_key().public_bytes(serialization.Encoding.DER, formato) != cert.public_key().public_bytes(serialization.Encoding.DER, formato):
        raise ValueError("El certificado y la clave privada no coinciden.")
    huella = cert.fingerprint(hashes.SHA256()).hex()
    cache_path = LOCAL / "ticket-wsfe.json"
    if cache_path.exists():
        cache = json.loads(cache_path.read_text(encoding="utf-8"))
        if (cache.get("entorno") == "homologacion" and cache.get("servicio") == "wsfe"
                and cache.get("certificado_sha256") == huella and cache.get("token") and cache.get("sign")
                and fecha(cache["expirationTime"]) > ahora + timedelta(minutes=5)):
            print("Ticket WSAA local vigente para wsfe. Vence: " + cache["expirationTime"])
            return

    # Obtener namespace y SOAPAction del contrato del endpoint de homologación.
    wsdl = xml_seguro(solicitar(Request(WSAA + "?WSDL")))
    operacion = wsdl.find(f"{{{WSDL}}}binding/{{{WSDL}}}operation[@name='loginCms']")
    if operacion is None:
        raise ValueError("El WSDL no contiene la operación loginCms esperada.")
    body = operacion.find(f"{{{WSDL}}}input/{{{SOAP_BINDING}}}body")
    action = operacion.find(f"{{{SOAP_BINDING}}}operation")
    namespace = (body.get("namespace") if body is not None else None) or wsdl.get("targetNamespace")
    if not namespace or action is None:
        raise ValueError("El contrato de loginCms está incompleto.")
    tra = ET.Element("loginTicketRequest", version="1.0")
    header = ET.SubElement(tra, "header")
    ET.SubElement(header, "uniqueId").text = str(int(ahora.timestamp()))
    ET.SubElement(header, "generationTime").text = (ahora - timedelta(minutes=5)).isoformat(timespec="seconds")
    ET.SubElement(header, "expirationTime").text = (ahora + timedelta(minutes=10)).isoformat(timespec="seconds")
    ET.SubElement(tra, "service").text = "wsfe"
    cms = (pkcs7.PKCS7SignatureBuilder().set_data(ET.tostring(tra, encoding="utf-8", xml_declaration=True))
        .add_signer(cert, key, hashes.SHA256()).sign(serialization.Encoding.DER, [pkcs7.PKCS7Options.Binary]))
    envelope = ET.Element(f"{{{SOAP}}}Envelope")
    soap_body = ET.SubElement(envelope, f"{{{SOAP}}}Body")
    login = ET.SubElement(soap_body, f"{{{namespace}}}loginCms")
    ET.SubElement(login, "in0").text = base64.b64encode(cms).decode("ascii")
    soap_action = action.get("soapAction", "")
    respuesta = solicitar(Request(WSAA, data=ET.tostring(envelope, encoding="utf-8", xml_declaration=True),
        headers={"Content-Type": "text/xml; charset=utf-8", "SOAPAction": '"' + soap_action + '"'}))
    root = xml_seguro(respuesta)
    if elemento(root, "Fault") is not None:
        raise RuntimeError(falla_soap(respuesta))
    ticket_xml = texto(root, "loginCmsReturn")
    if not ticket_xml:
        raise ValueError("WSAA no devolvió loginCmsReturn.")
    ticket = xml_seguro(ticket_xml.encode("utf-8"))
    token, sign, expiration = texto(ticket, "token"), texto(ticket, "sign"), texto(ticket, "expirationTime")
    if not token or not sign or not expiration or fecha(expiration) <= datetime.now(timezone.utc):
        raise ValueError("WSAA devolvió un ticket incompleto o vencido.")
    cache = {"entorno": "homologacion", "servicio": "wsfe", "certificado_sha256": huella,
        "expirationTime": expiration, "token": token, "sign": sign}
    temporal = cache_path.with_suffix(".tmp")
    temporal.write_text(json.dumps(cache, indent=2) + "\n", encoding="utf-8")
    temporal.replace(cache_path)
    estado_path = LOCAL / "estado-homologacion.json"
    estado = json.loads(estado_path.read_text(encoding="utf-8"))
    estado.update({"autorizacion_wsfe": "creada_en_wsass", "autenticacion_wsaa": "correcta",
        "ticket_vencimiento": expiration})
    estado_path.write_text(json.dumps(estado, indent=2) + "\n", encoding="utf-8")
    print("Autenticación WSAA de homologación correcta para wsfe.")
    print("Ticket guardado en la configuración local. Vencimiento: " + expiration)


def consultar_wsfe(operacion: str, parametros: tuple[tuple[str, str], ...] = ()) -> ET.Element:
    if operacion not in {"FEParamGetPtosVenta", "FECompUltimoAutorizado"}:
        raise ValueError("Este diagnóstico admite únicamente consultas de puntos y numeración.")
    cache = json.loads((LOCAL / "ticket-wsfe.json").read_text(encoding="utf-8"))
    cert = x509.load_pem_x509_certificate((LOCAL / "certificado-homo.pem").read_bytes())
    serial = cert.subject.get_attributes_for_oid(NameOID.SERIAL_NUMBER)[0].value
    match = re.fullmatch(r"CUIT (\d{11})", serial)
    if match is None:
        raise ValueError("El certificado no identifica el CUIT representado esperado.")
    envelope = ET.Element(f"{{{SOAP}}}Envelope")
    body = ET.SubElement(envelope, f"{{{SOAP}}}Body")
    operation = ET.SubElement(body, f"{{{WSFE_NS}}}{operacion}")
    auth = ET.SubElement(operation, f"{{{WSFE_NS}}}Auth")
    for nombre, valor in [("Token", cache["token"]), ("Sign", cache["sign"]), ("Cuit", match.group(1))]:
        ET.SubElement(auth, f"{{{WSFE_NS}}}{nombre}").text = valor
    for nombre, valor in parametros:
        ET.SubElement(operation, f"{{{WSFE_NS}}}{nombre}").text = valor
    respuesta = solicitar(Request(WSFE, data=ET.tostring(envelope, encoding="utf-8", xml_declaration=True),
        headers={"Content-Type": "text/xml; charset=utf-8", "SOAPAction": '"' + WSFE_NS + operacion + '"'}))
    root = xml_seguro(respuesta)
    if elemento(root, "Fault") is not None:
        raise RuntimeError(falla_soap(respuesta))
    result = elemento(root, operacion + "Result")
    if result is None:
        raise ValueError("WSFE no devolvió el resultado esperado de " + operacion)
    return result


def errores_wsfe(result: ET.Element) -> list[dict[str, str]]:
    return [{"codigo": texto(e, "Code"), "mensaje": texto(e, "Msg")} for e in result.iter()
        if e.tag.rsplit("}", 1)[-1] == "Err"]


def consultar_puntos() -> None:
    """FEParamGetPtosVenta es una consulta de catálogo; no solicita autorizaciones."""
    result = consultar_wsfe("FEParamGetPtosVenta")
    errores = errores_wsfe(result)
    puntos = [{"numero": int(texto(p, "Nro")), "emisionTipo": texto(p, "EmisionTipo"),
        "bloqueado": texto(p, "Bloqueado"), "fechaBaja": texto(p, "FchBaja")} for p in result.iter()
        if p.tag.rsplit("}", 1)[-1] == "PtoVenta"]
    resumen = {"entorno": "homologacion", "operacion": "FEParamGetPtosVenta",
        "consultada": datetime.now(timezone.utc).isoformat(), "puntos": puntos, "errores": errores}
    (LOCAL / "puntos-venta-wsfe.json").write_text(json.dumps(resumen, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(resumen, ensure_ascii=False))
    if errores:
        raise RuntimeError("WSFE devolvió errores al consultar los puntos de venta.")


def consultar_ultimo() -> None:
    # Valores del ejemplo docente, solo para consultar su numeración en homologación.
    result = consultar_wsfe("FECompUltimoAutorizado", (("PtoVta", "2"), ("CbteTipo", "11")))
    errores = errores_wsfe(result)
    nro = texto(result, "CbteNro")
    resumen = {"entorno": "homologacion", "operacion": "FECompUltimoAutorizado",
        "punto_venta": 2, "tipo_comprobante": 11, "ultimo_numero": int(nro) if nro else None,
        "consultada": datetime.now(timezone.utc).isoformat(), "errores": errores}
    (LOCAL / "ultimo-comprobante-wsfe.json").write_text(json.dumps(resumen, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(resumen, ensure_ascii=False))
    if errores or not nro:
        raise RuntimeError("WSFE no confirmó la consulta del último comprobante.")


if __name__ == "__main__":
    try:
        main()
        if "--consultar-puntos" in sys.argv:
            consultar_puntos()
        if "--consultar-ultimo" in sys.argv:
            consultar_ultimo()
    except Exception as error:
        print("No se completó la comprobación: " + str(error), file=sys.stderr)
        sys.exit(1)
