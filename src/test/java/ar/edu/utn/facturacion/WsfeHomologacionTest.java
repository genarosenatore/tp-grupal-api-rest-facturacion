package ar.edu.utn.facturacion;

import ar.edu.utn.facturacion.dto.*;
import ar.edu.utn.facturacion.fiscal.*;
import ar.edu.utn.facturacion.repository.*;
import ar.edu.utn.facturacion.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import tools.jackson.databind.json.JsonMapper;
import java.math.*;
import java.nio.file.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static ar.edu.utn.facturacion.fiscal.SoapArca.*;
import static org.assertj.core.api.Assertions.*;

/** Doble SOAP controlado con reservas/JPA reales. Nunca accede a ARCA ni al certificado personal. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,
    properties="spring.datasource.url=jdbc:h2:mem:wsfe;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("flujos")
class WsfeHomologacionTest {
    @Autowired FacturaService facturas;
    @Autowired FacturaMapper mapper;
    @Autowired FacturaRepository repo;
    @Autowired OperacionFiscalRepository operaciones;
    @Autowired ReservaFiscalService reservas;
    @Autowired PlatformTransactionManager manager;
    @Autowired JdbcTemplate jdbc;
    @TempDir Path local;
    CircuitoFiscalService fiscal;
    WsfeHomologacionGateway gateway;
    DobleSoap soap;
    X509Certificate certificado;
    @BeforeEach void preparar() throws Exception {
        for(String tabla:List.of("operacion_fiscal","factura_detalle","factura_venta","reserva_fiscal","serie_fiscal")) jdbc.update("delete from "+tabla);
        jdbc.update("merge into punto_venta(id,codigo,descripcion) key(id) values (2,'0002','Homologación TP')");
        var generador=KeyPairGenerator.getInstance("RSA"); generador.initialize(2048); var par=generador.generateKeyPair();
        var ahora=Instant.now();
        var builder=new X509v3CertificateBuilder(new X500Name("C=AR,O=AFIP,CN=Computadores Test"),BigInteger.ONE,
            Date.from(ahora.minusSeconds(3600)),Date.from(ahora.plusSeconds(86400)),
            new X500Name("SERIALNUMBER=CUIT 20123456786,CN=PruebaLocal"),SubjectPublicKeyInfo.getInstance(par.getPublic().getEncoded()));
        certificado=new JcaX509CertificateConverter().getCertificate(builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(par.getPrivate())));
        Files.writeString(local.resolve("certificado-homo.pem"),pem("CERTIFICATE",certificado.getEncoded()));
        Files.writeString(local.resolve("privada-homo.pem"),pem("PRIVATE KEY",par.getPrivate().getEncoded()));
        var datos=Map.of("entorno","homologacion","servicio","wsfe","certificado_sha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(certificado.getEncoded())),
            "token","TOKEN_FICTICIO_SOLO_TEST","sign","SIGN_FICTICIA_SOLO_TEST","expirationTime",OffsetDateTime.now(ZoneOffset.UTC).plusHours(1).toString());
        Files.writeString(local.resolve("ticket-wsfe.json"),JsonMapper.builder().build().writeValueAsString(datos));
        soap=new DobleSoap(); gateway=new WsfeHomologacionGateway(reservas,soap,local,2);
        fiscal=new CircuitoFiscalService(repo,operaciones,mapper,gateway,manager,Clock.systemUTC());
    }
    static String pem(String tipo,byte[] datos) {
        return "-----BEGIN "+tipo+"-----\n"+Base64.getMimeEncoder(64,new byte[]{10}).encodeToString(datos)+"\n-----END "+tipo+"-----\n";
    }
    FacturaReporteDTO crearC() {
        return facturas.crear(new FacturaRequestDTO(LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")),1L,1L,2L,1L,
            "Prueba controlada sin valor fiscal",List.of(new FacturaDetalleRequestDTO(1L,new BigDecimal("2"))),null,"11",96,5));
    }
    @Test void facturaCPrecioFinalSinIvaYValidacionDeDocumento() {
        var f=crearC(); assertThat(f.iva()).isEqualByComparingTo("0"); assertThat(f.total()).isEqualByComparingTo("2000");
        assertThat(f.detalles().getFirst().alicuotaIva()).isEqualByComparingTo("0");
        assertThatThrownBy(()->facturas.crear(new FacturaRequestDTO(f.fecha(),1L,1L,2L,1L,"",
            List.of(new FacturaDetalleRequestDTO(1L,BigDecimal.ONE)),null,"11",80,5))).hasMessageContaining("documento");
    }
    @ParameterizedTest @ValueSource(ints={1,4,6})
    void facturaCAdmiteClientesInscriptosExentosYMonotributistasConCuit(int condicion) {
        jdbc.update("merge into cliente(id,nombre,documento,tipo_documento,condicion_iva_receptor_id) key(id) values (98,'Cliente C con CUIT','20999999995',80,?)", condicion);
        var f = facturas.crear(new FacturaRequestDTO(LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")),98L,1L,2L,1L,
            "C a receptor identificado",List.of(new FacturaDetalleRequestDTO(1L,BigDecimal.ONE)),null,"11",80,condicion));
        assertThat(f.condicionIvaReceptorId()).isEqualTo(condicion);
        assertThat(f.total()).isEqualByComparingTo("1000");
        assertThat(f.iva()).isEqualByComparingTo("0");
        assertThatThrownBy(() -> ReglasHomologacion.validarReceptor("11",96,condicion,"12345678")).hasMessageContaining("CUIT");
    }
    @Test void servidorRechazaFacturaAAConsumidorFinalAunqueSeEnviePorFueraDelFormulario() {
        assertThatThrownBy(() -> facturas.crear(new FacturaRequestDTO(LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")),1L,1L,2L,1L,
            "A incompatible",List.of(new FacturaDetalleRequestDTO(1L,BigDecimal.ONE)),null,"1",null,null)))
            .hasMessageContaining("condición fiscal");
    }
    @Test void autorizaConDatosRealesDelDobleYNoReenvia() {
        var f=fiscal.autorizar(crearC().id());
        assertThat(f.numero()).isEqualTo(1); assertThat(f.tipoComprobante()).isEqualTo("11");
        assertThat(f.letraComprobante().name()).isEqualTo("C");
        assertThat(f.ambienteArca()).isEqualTo("HOMOLOGACION"); assertThat(f.informadaArca()).isTrue();
        fiscal.autorizar(f.id()); assertThat(soap.envios.get()).isEqualTo(1);
        String respuesta=jdbc.queryForObject("select respuesta from operacion_fiscal where factura_id=?",String.class,f.id());
        assertThat(respuesta).contains("CAE").doesNotContain("TOKEN_FICTICIO", "SIGN_FICTICIA");
    }
    FacturaReporteDTO crearAB(String tipo) {
        jdbc.update("merge into cliente(id,nombre,documento,tipo_documento,condicion_iva_receptor_id) key(id) values (3,'Receptor CUIT ficticio','20123456786',80,1)");
        jdbc.update("merge into lista_precio_articulo(id,articulo_id,tipo_moneda_id,precio,lista_precio_id) key(id) values (10,3,1,10,1)");
        return facturas.crear(new FacturaRequestDTO(LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")),
            tipo.equals("1")?3L:1L,1L,2L,1L,"A/B de prueba",List.of(
                new FacturaDetalleRequestDTO(1L,new BigDecimal("2")), new FacturaDetalleRequestDTO(1L,BigDecimal.ONE),
                new FacturaDetalleRequestDTO(2L,new BigDecimal("2")), new FacturaDetalleRequestDTO(3L,BigDecimal.ONE)),
            null,tipo,tipo.equals("1")?80:96,tipo.equals("1")?1:5));
    }
    @ParameterizedTest @ValueSource(strings={"1","6"})
    void abAgrupaIvaConciliaYAnulaConCreditoDeLaMismaClase(String tipo) {
        var f=crearAB(tipo);
        assertThat(f.neto()).isEqualByComparingTo("3410");
        assertThat(f.iva()).isEqualByComparingTo("672");
        assertThat(f.total()).isEqualByComparingTo("4082");
        soap.perderRespuesta=true;
        assertThatThrownBy(()->fiscal.autorizar(f.id())).hasMessageContaining("conciliación");
        var solicitud=xml(soap.solicitudes.get(tipo+"/1"));
        var grupos=solicitud.getElementsByTagNameNS("*","AlicIva");
        assertThat(grupos.getLength()).isEqualTo(3);
        assertThat(texto(grupos.item(0),"Id")).isEqualTo("3");
        assertThat(texto(grupos.item(0),"BaseImp")).isEqualTo("10.00");
        assertThat(texto(grupos.item(1),"Id")).isEqualTo("4");
        assertThat(texto(grupos.item(1),"Importe")).isEqualTo("42.00");
        assertThat(texto(grupos.item(2),"Id")).isEqualTo("5");
        assertThat(texto(grupos.item(2),"BaseImp")).isEqualTo("3000.00");
        assertThat(texto(grupos.item(2),"Importe")).isEqualTo("630.00");
        assertThat(fiscal.conciliar(f.id()).informadaArca()).isTrue();
        assertThat(soap.envios.get()).isEqualTo(1);
        soap.perderRespuesta=true;
        assertThatThrownBy(()->fiscal.anular(f.id(),"Devolución de prueba A/B")).hasMessageContaining("conciliación");
        assertThat(fiscal.conciliar(f.id()).estado().name()).isEqualTo("ANULADA");
        var ajuste=xml(soap.ultimoAjuste);
        assertThat(texto(ajuste,"CbteTipo")).isEqualTo(tipo.equals("1")?"3":"8");
        assertThat(texto(elemento(ajuste,"CbteAsoc"),"Tipo")).isEqualTo(tipo);
        assertThat(facturas.obtener(f.id()).tipoComprobante()).isEqualTo(tipo);
        assertThat(facturas.obtener(f.id()).letraComprobante().name()).isEqualTo(tipo.equals("1") ? "A" : "B");
        assertThat(soap.envios.get()).isEqualTo(2);
    }
    @Test void rechazaReceptorDeAConDniYCondicionesIncompatiblesYAlícuotasNoFiscales() {
        var base=crearC();
        var detalles=List.of(new FacturaDetalleRequestDTO(1L,BigDecimal.ONE));
        assertThatThrownBy(()->facturas.crear(new FacturaRequestDTO(base.fecha(),1L,1L,2L,1L,"",detalles,null,"1",96,1)))
            .hasMessageContaining("condición fiscal");
        assertThatThrownBy(()->facturas.crear(new FacturaRequestDTO(base.fecha(),1L,1L,2L,1L,"",detalles,null,"6",96,1)))
            .hasMessageContaining("condición fiscal");
        jdbc.update("update articulo set alicuota_iva=7 where id=1");
        try {
            assertThatThrownBy(()->facturas.crear(new FacturaRequestDTO(base.fecha(),1L,1L,2L,1L,"",detalles,null,"6",96,5)))
                .hasMessageContaining("Alícuota");
        } finally { jdbc.update("update articulo set alicuota_iva=21 where id=1"); }
    }
    @Test void timeoutConciliaSinNuevaEmision() {
        var f=crearC(); soap.perderRespuesta=true;
        assertThatThrownBy(()->fiscal.autorizar(f.id())).hasMessageContaining("conciliación");
        assertThat(facturas.obtener(f.id()).estadoArca().name()).isEqualTo("INCIERTA");
        assertThatThrownBy(()->fiscal.autorizar(f.id())).hasMessageContaining("no se reenviará");
        assertThat(fiscal.conciliar(f.id()).informadaArca()).isTrue(); assertThat(soap.envios.get()).isEqualTo(1);
    }
    @Test void rechazoLiberaSerieYReutilizaNumeroNoAutorizado() {
        var f=crearC(); soap.rechazar=true;
        assertThatThrownBy(()->fiscal.autorizar(f.id())).hasMessageContaining("rechazó");
        assertThat(facturas.obtener(f.id()).informadaArca()).isFalse(); soap.rechazar=false;
        assertThat(fiscal.autorizar(f.id()).numero()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from reserva_fiscal where numero=1",Integer.class)).isEqualTo(2);
    }
    @Test void dosFacturasDeMismaSerieNoReservanElMismoNumero() throws Exception {
        var f1=crearC(); var f2=crearC(); soap.entro=new CountDownLatch(1); soap.continuar=new CountDownLatch(1);
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var primera=executor.submit(()->fiscal.autorizar(f1.id()));
            assertThat(soap.entro.await(5,TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(()->fiscal.autorizar(f2.id())).hasMessageContaining("otra operación activa");
            soap.continuar.countDown(); assertThat(primera.get(5,TimeUnit.SECONDS).numero()).isEqualTo(1);
            assertThat(fiscal.autorizar(f2.id()).numero()).isEqualTo(2);
        } finally { soap.continuar.countDown(); }
        assertThat(soap.envios.get()).isEqualTo(2);
    }
    @Test void conciliarDuranteLaReservaConservaElEnvioEnCurso() throws Exception {
        var factura = crearC();
        soap.consultandoNumero = new CountDownLatch(1);
        soap.permitirReserva = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var autorizando = executor.submit(() -> fiscal.autorizar(factura.id()));
            try {
                assertThat(soap.consultandoNumero.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> fiscal.conciliar(factura.id())).hasMessageContaining("conciliación");
                assertThat(facturas.obtener(factura.id()).editable()).isFalse();
            } finally {
                soap.permitirReserva.countDown();
            }
            assertThat(autorizando.get(5, TimeUnit.SECONDS).informadaArca()).isTrue();
        }
        assertThat(soap.envios.get()).isEqualTo(1);
        assertThat(facturas.obtener(factura.id()).codigoAutorizacion()).isEqualTo("12345678901234");
    }
    @Test void falloDeConsultaAntesDeReservarPermiteReintentoSinDuplicar() {
        var factura = crearC();
        soap.fallarConsultaNumero = true;
        assertThatThrownBy(() -> fiscal.autorizar(factura.id())).hasMessageContaining("antes del envío");
        assertThat(soap.envios.get()).isZero();
        assertThat(facturas.obtener(factura.id()).editable()).isTrue();
        soap.fallarConsultaNumero = false;
        assertThat(fiscal.autorizar(factura.id()).informadaArca()).isTrue();
        assertThat(soap.envios.get()).isEqualTo(1);
    }
    @Test void notaCreditoAsociadaMantieneAutorizacionOriginal() {
        var original=fiscal.autorizar(crearC().id());
        var anulada=fiscal.anular(original.id(),"Devolución de prueba");
        assertThat(anulada.estado().name()).isEqualTo("ANULADA"); assertThat(anulada.numero()).isEqualTo(original.numero());
        assertThat(anulada.codigoAutorizacion()).isEqualTo(original.codigoAutorizacion());
        assertThat(jdbc.queryForObject("select tipo_comprobante from operacion_fiscal where operacion='ANULAR'",String.class)).isEqualTo("13");
        assertThat(soap.ultimoAjuste).contains("<Tipo>11</Tipo>","<PtoVta>2</PtoVta>","<Nro>1</Nro>");
    }
    @Test void conciliacionNoAdoptaImportesDeOtroComprobante() {
        var f=crearC(); soap.perderRespuesta=true;
        assertThatThrownBy(()->fiscal.autorizar(f.id())).hasMessageContaining("conciliación");
        soap.alterarImporte=true;
        assertThatThrownBy(()->fiscal.conciliar(f.id())).hasMessageContaining("conciliación");
        assertThat(facturas.obtener(f.id()).informadaArca()).isFalse();
        assertThat(soap.envios.get()).isEqualTo(1);
    }
    @Test void xmlNoPermiteEntidadesExternasYAuditoriaSaneaSecretos() {
        assertThatThrownBy(()->xml("<!DOCTYPE x [<!ENTITY a SYSTEM 'file:///no-debe-leerse'>]><x>&a;</x>"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(auditar(xml("<x><Token>secreto</Token><Sign>firma</Sign><CAE>123</CAE></x>"))).doesNotContain("secreto","firma").contains("123");
    }
    @Test void renuevaTicketConCmsFirmadoYLoReutiliza() throws Exception {
        Files.delete(local.resolve("ticket-wsfe.json")); var renovaciones=new AtomicInteger();
        var transporte=new DobleSoap() {
            @Override public org.w3c.dom.Document wsdlWsaa() {
                return xml("<definitions xmlns='http://schemas.xmlsoap.org/wsdl/' xmlns:s='http://schemas.xmlsoap.org/wsdl/soap/' targetNamespace='urn:wsaa-prueba'><binding><operation name='loginCms'><s:operation soapAction='urn:LoginCms'/><input><s:body namespace='urn:wsaa-prueba'/></input></operation></binding></definitions>");
            }
            @Override public Respuesta llamar(boolean wsaa,String accion,String contenido) {
                assertThat(wsaa).isTrue(); assertThat(accion).isEqualTo("urn:LoginCms"); renovaciones.incrementAndGet();
                try {
                    var firmado=new org.bouncycastle.cms.CMSSignedData(Base64.getDecoder().decode(texto(xml(contenido),"in0")));
                    var signer=firmado.getSignerInfos().getSigners().iterator().next();
                    assertThat(signer.verify(new org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder().build(certificado))).isTrue();
                    var tra=xml(new String((byte[])firmado.getSignedContent().getContent(),java.nio.charset.StandardCharsets.UTF_8));
                    assertThat(texto(tra,"service")).isEqualTo("wsfe");
                } catch(Exception e) { throw new AssertionError(e); }
                String ticket="<loginTicketResponse><header>"+campo("expirationTime",OffsetDateTime.now(ZoneOffset.UTC).plusHours(1))+"</header><credentials><token>TOKEN_NUEVO_PRUEBA</token><sign>SIGN_NUEVA_PRUEBA</sign></credentials></loginTicketResponse>";
                return respuesta("<loginCmsResponse>"+campo("loginCmsReturn",ticket)+"</loginCmsResponse>");
            }
        };
        var cliente=new WsaaHomologacion(local,transporte);
        assertThat(cliente.ticket().token()).isEqualTo("TOKEN_NUEVO_PRUEBA");
        assertThat(cliente.ticket().cuit()).isEqualTo(20123456786L); assertThat(renovaciones.get()).isEqualTo(1);
    }
    static class DobleSoap extends SoapArca {
        AtomicInteger envios=new AtomicInteger();
        Map<Integer,Long> ultimos=new ConcurrentHashMap<>();
        Map<String,String> solicitudes=new ConcurrentHashMap<>();
        boolean perderRespuesta,rechazar,alterarImporte,fallarConsultaNumero;
        String ultimoAjuste="";
        CountDownLatch entro,continuar,consultandoNumero,permitirReserva;
        @Override public Respuesta llamar(boolean wsaa,String accion,String contenido) {
            assertThat(wsaa).isFalse(); // El ticket ficticio vigente debe reutilizarse.
            var doc=xml(contenido); String op=doc.getDocumentElement().getLocalName();
            if(op.equals("FECompUltimoAutorizado")) {
                if (fallarConsultaNumero) throw new IllegalStateException("Consulta de numeración interrumpida");
                if (consultandoNumero != null) {
                    consultandoNumero.countDown();
                    try {
                        if (!permitirReserva.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Tiempo de prueba agotado");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                }
                return respuesta("<FECompUltimoAutorizadoResult>"+campo("CbteNro",ultimos.getOrDefault(Integer.parseInt(texto(doc,"CbteTipo")),0L))+"</FECompUltimoAutorizadoResult>");
            }
            if(op.equals("FECAESolicitar")) {
                envios.incrementAndGet();
                if(entro!=null) { entro.countDown(); try { if(!continuar.await(5,TimeUnit.SECONDS)) throw new IllegalStateException(); } catch(InterruptedException e) { throw new IllegalStateException(e); } }
                int tipo=Integer.parseInt(texto(doc,"CbteTipo")); long nro=Long.parseLong(texto(doc,"CbteDesde"));
                if(rechazar) return respuesta("<FECAESolicitarResult><FeDetResp><FECAEDetResponse><Resultado>R</Resultado><Observaciones><Obs><Code>10017</Code><Msg>Rechazo controlado</Msg></Obs></Observaciones></FECAEDetResponse></FeDetResp></FECAESolicitarResult>");
                solicitudes.put(tipo+"/"+nro,contenido); ultimos.put(tipo,nro);
                if(Set.of(3,8,13).contains(tipo)) ultimoAjuste=contenido;
                if(perderRespuesta) { perderRespuesta=false; throw new IllegalStateException("Respuesta perdida controlada"); }
                return respuesta("<FECAESolicitarResult><FeCabResp>"+campo("PtoVta",2)+campo("CbteTipo",tipo)+"</FeCabResp><FeDetResp><FECAEDetResponse>"+eco(doc)+"<Resultado>A</Resultado><CAE>12345678901234</CAE>"+campo("CAEFchVto",LocalDate.now().plusDays(10).format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE))+"</FECAEDetResponse></FeDetResp></FECAESolicitarResult>");
            }
            if(op.equals("FECompConsultar")) {
                int tipo=Integer.parseInt(texto(doc,"CbteTipo")); long nro=Long.parseLong(texto(doc,"CbteNro"));
                var original=xml(solicitudes.get(tipo+"/"+nro));
                String campos=eco(original)+campo("PtoVta",2)+campo("CbteTipo",tipo)+campo("ImpTotal",alterarImporte?"999.99":texto(original,"ImpTotal"))
                    +campo("ImpIVA",texto(original,"ImpIVA"))+campo("ImpNeto",texto(original,"ImpNeto"))+campo("MonId","PES")
                    +copiarGrupo(original,"Iva")+copiarGrupo(original,"CbtesAsoc");
                return respuesta("<FECompConsultarResult><ResultGet>"+campos+"<Resultado>A</Resultado><CodAutorizacion>12345678901234</CodAutorizacion>"+campo("FchVto",LocalDate.now().plusDays(10).format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE))+"</ResultGet></FECompConsultarResult>");
            }
            throw new AssertionError("Operación inesperada: "+op);
        }
        String eco(org.w3c.dom.Document d) {
            StringBuilder out=new StringBuilder(); for(String nombre:List.of("DocTipo","DocNro","CbteDesde","CbteHasta","CbteFch")) out.append(campo(nombre,texto(d,nombre))); return out.toString();
        }
        String copiarGrupo(org.w3c.dom.Document original,String nombre) {
            var grupo=elemento(original,nombre);
            if(grupo==null) return "";
            var copia=xml("<Raiz/>");
            copia.replaceChild(copia.importNode(grupo,true),copia.getDocumentElement());
            return auditar(copia).replaceFirst("<\\?xml[^>]*\\?>","");
        }
        Respuesta respuesta(String contenido) { var d=xml(contenido); return new Respuesta(d,auditar(d)); }
    }
}
