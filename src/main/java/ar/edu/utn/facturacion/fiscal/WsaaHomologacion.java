package ar.edu.utn.facturacion.fiscal;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.*;
import java.util.*;
import javax.naming.ldap.LdapName;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.*;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.jcajce.*;
import tools.jackson.databind.json.JsonMapper;
import static ar.edu.utn.facturacion.fiscal.SoapArca.*;

/** Firma CMS equivalente al ejemplo docente, con caché ligada al certificado. */
public class WsaaHomologacion {
    private final Path directorio;
    private final SoapArca soap;
    private final JsonMapper json = JsonMapper.builder().build();
    public record Ticket(String token, String sign, long cuit) {}
    public WsaaHomologacion(Path directorio, SoapArca soap) { this.directorio = directorio; this.soap = soap; }
    public X509Certificate certificado() {
        try (var in = Files.newInputStream(directorio.resolve("certificado-homo.pem"))) {
            var cert = (X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(in);
            cert.checkValidity();
            if (!cert.getIssuerX500Principal().getName().contains("CN=Computadores Test"))
                throw new IllegalStateException("Se requiere el certificado de homologación.");
            return cert;
        } catch (Exception e) { throw new IllegalStateException("Revisá el certificado local de homologación y su vigencia.", e); }
    }
    public long cuit() {
        try {
            for (var rdn : new LdapName(certificado().getSubjectX500Principal().getName()).getRdns()) {
                if (rdn.getType().equalsIgnoreCase("SERIALNUMBER") || rdn.getType().equals("2.5.4.5")) {
                    Object raw = rdn.getValue();
                    String value = raw instanceof byte[] b
                        ? ((org.bouncycastle.asn1.ASN1String)org.bouncycastle.asn1.ASN1Primitive.fromByteArray(b)).getString()
                        : raw.toString();
                    if (value.matches("CUIT [0-9]{11}")) return Long.parseLong(value.substring(5));
                }
            }
            throw new IllegalStateException("El certificado no contiene CUIT.");
        } catch (Exception e) { throw new IllegalStateException("No se pudo leer el CUIT del certificado.", e); }
    }
    public synchronized Ticket ticket() {
        try {
            var cert = certificado(); long cuit = cuit();
            String huella = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(cert.getEncoded()));
            Path cache = directorio.resolve("ticket-wsfe.json");
            if (Files.exists(cache)) {
                Map<?,?> datos = json.readValue(Files.readString(cache), Map.class);
                if ("homologacion".equals(datos.get("entorno")) && "wsfe".equals(datos.get("servicio"))
                    && huella.equals(datos.get("certificado_sha256"))
                    && OffsetDateTime.parse(String.valueOf(datos.get("expirationTime"))).toInstant().isAfter(Instant.now().plusSeconds(300))
                    && datos.get("token") instanceof String token && !token.isBlank()
                    && datos.get("sign") instanceof String sign && !sign.isBlank()) return new Ticket(token, sign, cuit);
            }
            var now = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
            String tra = "<loginTicketRequest version=\"1.0\"><header>" + campo("uniqueId", now.toEpochSecond())
                + campo("generationTime",now.minusMinutes(5)) + campo("expirationTime",now.plusMinutes(10)) + "</header><service>wsfe</service></loginTicketRequest>";
            String pem = Files.readString(directorio.resolve("privada-homo.pem")).replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", "");
            PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
            var generator = new CMSSignedDataGenerator();
            generator.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(new JcaDigestCalculatorProviderBuilder().build())
                .build(new JcaContentSignerBuilder("SHA256withRSA").build(key),cert));
            generator.addCertificates(new JcaCertStore(List.of(cert)));
            String cms = Base64.getEncoder().encodeToString(generator.generate(new CMSProcessableByteArray(tra.getBytes(StandardCharsets.UTF_8)),true).getEncoded());
            var wsdl = soap.wsdlWsaa();
            var bindings = wsdl.getElementsByTagNameNS("http://schemas.xmlsoap.org/wsdl/", "binding");
            org.w3c.dom.Element operacion = null;
            for (int i=0;i<bindings.getLength() && operacion==null;i++) {
                var ops = ((org.w3c.dom.Element)bindings.item(i)).getElementsByTagNameNS("http://schemas.xmlsoap.org/wsdl/", "operation");
                for (int j=0;j<ops.getLength();j++) if (((org.w3c.dom.Element)ops.item(j)).getAttribute("name").equals("loginCms")) operacion=(org.w3c.dom.Element)ops.item(j);
            }
            if (operacion==null) throw new IllegalStateException("Contrato WSAA sin loginCms.");
            var body = operacion.getElementsByTagNameNS("http://schemas.xmlsoap.org/wsdl/soap/", "body");
            String ns = body.getLength()>0 ? ((org.w3c.dom.Element)body.item(0)).getAttribute("namespace") : "";
            if (ns.isBlank()) ns=wsdl.getDocumentElement().getAttribute("targetNamespace");
            var action = operacion.getElementsByTagNameNS("http://schemas.xmlsoap.org/wsdl/soap/", "operation");
            String soapAction = action.getLength()>0 ? ((org.w3c.dom.Element)action.item(0)).getAttribute("soapAction") : "";
            var response = soap.llamar(true,soapAction,"<loginCms xmlns=\""+e(ns)+"\"><in0 xmlns=\"\">"+cms+"</in0></loginCms>");
            var ticket = xml(texto(response.documento(),"loginCmsReturn"));
            String token = texto(ticket,"token"), sign=texto(ticket,"sign"), expiry=texto(ticket,"expirationTime");
            if (token.isBlank() || sign.isBlank() || !OffsetDateTime.parse(expiry).toInstant().isAfter(Instant.now())) throw new IllegalStateException("Ticket WSAA incompleto o vencido.");
            var datos=Map.of("entorno","homologacion","servicio","wsfe","certificado_sha256",huella,"token",token,"sign",sign,"expirationTime",expiry);
            Path temporal=directorio.resolve("ticket-wsfe-java.tmp"); Files.writeString(temporal,json.writeValueAsString(datos)); Files.move(temporal,cache,StandardCopyOption.REPLACE_EXISTING);
            return new Ticket(token,sign,cuit);
        } catch (Exception e) { throw new IllegalStateException("No se pudo autenticar con WSAA de homologación.",e); }
    }
}
