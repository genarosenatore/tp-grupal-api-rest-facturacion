package ar.edu.utn.facturacion.fiscal;

import org.w3c.dom.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Transporte exclusivo de homologación. Nunca registra requests ni credenciales. */
public class SoapArca {
    public static final String WSAA = "https://wsaahomo.afip.gov.ar/ws/services/LoginCms";
    public static final String WSFE = "https://wswhomo.afip.gov.ar/wsfev1/service.asmx";
    public static final String NS = "http://ar.gov.afip.dif.FEV1/";
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NEVER).build();
    public record Respuesta(Document documento, String auditoria) {}
    public Respuesta llamar(boolean wsaa, String accion, String contenido) {
        String envelope = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body>"
            + contenido + "</soap:Body></soap:Envelope>";
        return enviar(HttpRequest.newBuilder(URI.create(wsaa ? WSAA : WSFE)).timeout(Duration.ofSeconds(25))
            .header("Content-Type", "text/xml; charset=utf-8").header("SOAPAction", "\"" + accion + "\"")
            .POST(HttpRequest.BodyPublishers.ofString(envelope, StandardCharsets.UTF_8)).build());
    }
    public Document wsdlWsaa() {
        return enviar(HttpRequest.newBuilder(URI.create(WSAA + "?WSDL")).timeout(Duration.ofSeconds(20)).GET().build()).documento();
    }
    private Respuesta enviar(HttpRequest request) {
        try {
            var response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            byte[] bytes;
            try (var stream = response.body()) { bytes = stream.readNBytes(512001); }
            if (bytes.length > 512000) throw new IllegalStateException("Respuesta ARCA demasiado extensa.");
            var doc = xml(new String(bytes, StandardCharsets.UTF_8));
            String fault = texto(doc, "faultstring");
            if (!fault.isBlank()) throw new IllegalStateException("ARCA homologación: " + seguro(fault));
            if (response.statusCode() != 200) throw new IllegalStateException("ARCA homologación respondió HTTP " + response.statusCode());
            return new Respuesta(doc, auditar(doc));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new IllegalStateException("Consulta a ARCA interrumpida.", e);
        } catch (IOException e) { throw new IllegalStateException("No se confirmó la respuesta de ARCA homologación.", e); }
    }
    public static Document xml(String value) {
        try {
            var f = DocumentBuilderFactory.newInstance(); f.setNamespaceAware(true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return f.newDocumentBuilder().parse(new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalArgumentException("Respuesta XML inválida de ARCA.", e); }
    }
    public static Element elemento(Node root, String nombre) {
        var nodes = root instanceof Document d ? d.getElementsByTagNameNS("*", nombre) : ((Element)root).getElementsByTagNameNS("*", nombre);
        return nodes.getLength() == 0 ? null : (Element)nodes.item(0);
    }
    public static String texto(Node root, String nombre) {
        Element e = elemento(root, nombre); return e == null ? "" : e.getTextContent().trim();
    }
    public static String e(Object value) {
        return String.valueOf(value).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }
    public static String campo(String nombre, Object valor) { return "<" + nombre + ">" + e(valor) + "</" + nombre + ">"; }
    public static String seguro(String mensaje) {
        String result = mensaje.replaceAll("[A-Za-z0-9+/=]{40,}", "[dato omitido]");
        return result.substring(0, Math.min(1500, result.length()));
    }
    public static String auditar(Document original) {
        try {
            Document doc = (Document)original.cloneNode(true);
            for (String nombre : new String[]{"Token", "Sign", "token", "sign", "loginCmsReturn"}) {
                NodeList nodes = doc.getElementsByTagNameNS("*", nombre);
                for (int i = nodes.getLength()-1; i >= 0; i--) nodes.item(i).setTextContent("[omitido]");
            }
            var f = TransformerFactory.newInstance(); f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); f.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            var out = new StringWriter(); f.newTransformer().transform(new DOMSource(doc), new StreamResult(out)); return out.toString();
        } catch (Exception e) { throw new IllegalStateException("No se pudo sanear la respuesta fiscal.", e); }
    }
}
