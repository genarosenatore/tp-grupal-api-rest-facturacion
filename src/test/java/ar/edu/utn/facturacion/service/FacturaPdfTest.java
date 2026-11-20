package ar.edu.utn.facturacion.service;

import ar.edu.utn.facturacion.dto.*;
import ar.edu.utn.facturacion.entity.*;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class FacturaPdfTest {
    final EmisorFactura emisor=new EmisorFactura("Librería Aula TP","20000000001","Av. del Aprendizaje 123, Ciudad de Prueba",
        "Responsable Monotributo (demostración)","No contribuyente (demostración)",LocalDate.of(2026,1,1),true);
    final FacturaPdfService pdf=new FacturaPdfService(null,null,emisor);
    FacturaReporteDTO factura(boolean autorizada,boolean anulada,int renglones,String observaciones) {
        var detalle=new FacturaDetalleReporteDTO(1L,1L,1L,"Cuaderno A4",new BigDecimal("2"),new BigDecimal("1000"),
            BigDecimal.ZERO,new BigDecimal("2000"),BigDecimal.ZERO,new BigDecimal("2000"));
        var total=new BigDecimal("2000").multiply(BigDecimal.valueOf(renglones));
        return new FacturaReporteDTO(1L,0L,LocalDate.of(2026,11,11),1L,"Cliente de Prueba",1L,2L,1L,"ARS",
            anulada?EstadoFactura.ANULADA:EstadoFactura.EMITIDA,anulada?Instant.parse("2026-11-12T00:29:43Z"):null,observaciones,
            total,BigDecimal.ZERO,total,Collections.nCopies(renglones,detalle),autorizada?1L:null,"11",
            autorizada?EstadoArca.AUTORIZADA:EstadoArca.PENDIENTE,autorizada,autorizada?"CAE":null,
            autorizada?"12345678901234":null,autorizada?LocalDate.of(2026,11,21):null,null,anulada?"Devolución total de prueba":null,
            !autorizada,!autorizada,"12345678","0002","Consumidor final",autorizada?"HOMOLOGACION":null,96,5,
            "Calle del Ejemplo 456, Ciudad de Prueba","Contado",1L,LetraComprobante.C,total,TipoDescuento.SIN_DESCUENTO,BigDecimal.ZERO,BigDecimal.ZERO);
    }
    @Test void facturaCTieneDatosCompletosYQrDecodificableConLosMismosImportes() throws Exception {
        byte[] bytes=pdf.generar(factura(true,false,1,"Operación de prueba"));
        try(var doc=Loader.loadPDF(bytes)) {
            assertThat(doc.getNumberOfPages()).isEqualTo(1);
            String texto=new PDFTextStripper().getText(doc);
            assertThat(texto).contains("FACTURA","CÓD. 011","00002-00000001","11/11/2026","Ingresos Brutos:","Inicio de actividades: 01/01/2026",
                "DNI: 12345678","Calle del Ejemplo 456","Contado","IVA sin discriminar","2.000,00","CAE: 12345678901234","21/11/2026",
                "HOMOLOGACIÓN - SIN VALIDEZ FISCAL","Página 1 de 1").doesNotContain("No informado","IVA 21");
            var imagen=new PDFRenderer(doc).renderImageWithDPI(0,220);
            int s=imagen.getWidth(); int h=imagen.getHeight();
            var luminancia=new RGBLuminanceSource(s,h,imagen.getRGB(0,0,s,h,null,0,s));
            String url=new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(luminancia)),Map.of(DecodeHintType.TRY_HARDER,true)).getText();
            assertThat(url).startsWith("https://www.arca.gob.ar/fe/qr/?p=");
            var json=JsonMapper.builder().build().readTree(new String(Base64.getDecoder().decode(url.substring(url.indexOf("?p=")+3)),StandardCharsets.UTF_8));
            assertThat(json.path("cuit").asLong()).isEqualTo(20000000001L);
            assertThat(json.path("ptoVta").asInt()).isEqualTo(2);
            assertThat(json.path("tipoCmp").asInt()).isEqualTo(11);
            assertThat(json.path("nroCmp").asInt()).isEqualTo(1);
            assertThat(json.path("importe").decimalValue()).isEqualByComparingTo("2000");
            assertThat(json.path("moneda").asString()).isEqualTo("PES");
            assertThat(json.path("tipoCodAut").asString()).isEqualTo("E");
            assertThat(json.path("codAut").asLong()).isEqualTo(12345678901234L);
            assertThat(json.path("tipoDocRec").asInt()).isEqualTo(96);
            guardar("factura-diseno-prueba",bytes,doc);
        }
    }
    @Test void pendienteNoInventaCodigoNiQrYAnuladaDistingueAmbasAutorizaciones() throws Exception {
        var pendiente=factura(false,false,1,null);
        assertThat(FacturaQr.url(pendiente,emisor.cuit())).isEmpty();
        try(var doc=Loader.loadPDF(pdf.generar(pendiente))) {
            assertThat(new PDFTextStripper().getText(doc)).contains("SIN NÚMERO","SIN QR FISCAL","PENDIENTE DE AUTORIZACIÓN").doesNotContain("CAE:");
        }
        byte[] bytes=pdf.generar(factura(true,true,1,"Prueba controlada"),new FacturaPdfService.Credito("13",1L,"CAE","98765432109876",LocalDate.of(2026,11,21)));
        try(var doc=Loader.loadPDF(bytes)) {
            assertThat(doc.getNumberOfPages()).isEqualTo(1);
            assertThat(new PDFTextStripper().getText(doc)).contains("ANULADA","Nota de crédito C 00002-00000001","CAE: 98765432109876",
                "CAE: 12345678901234","11/11/2026 21:29 (Argentina)");
            guardar("factura-diseno-anulada-prueba",bytes,doc);
        }
    }
    @Test void cienArticulosNoPierdenImportesYRepitenCabeceraYPaginacion() throws Exception {
        byte[] bytes=pdf.generar(factura(true,false,100,"Observaciones multilínea\n"+"Detalle comercial ".repeat(60)));
        try(var doc=Loader.loadPDF(bytes)) {
            assertThat(doc.getNumberOfPages()).isGreaterThan(1);
            String texto=new PDFTextStripper().getText(doc);
            assertThat(texto.split("Cuaderno A4",-1).length-1).isEqualTo(100);
            assertThat(texto).contains("TOTAL ARS","200.000,00","CAE: 12345678901234","Observaciones multilínea");
            for(int i=1;i<=doc.getNumberOfPages();i++) assertThat(texto).contains("Página "+i+" de "+doc.getNumberOfPages());
            guardar("factura-diseno-multipagina-prueba",bytes,doc);
        }
    }
    @ParameterizedTest @ValueSource(strings={"1","6"})
    void pdfABIdentificaClaseImportesCondicionYCredito(String tipo) throws Exception {
        var f=factura(true,true,1,"Prueba A/B");
        var detalle=new FacturaDetalleReporteDTO(1L,1L,1L,"Cuaderno A4",new BigDecimal("2"),new BigDecimal("1000"),
            new BigDecimal("21"),new BigDecimal("2000"),new BigDecimal("420"),new BigDecimal("2420"));
        var ab=new FacturaReporteDTO(f.id(),f.version(),f.fecha(),f.clienteId(),f.clienteNombre(),f.condicionIvaId(),
            f.puntoVentaId(),f.tipoMonedaId(),f.moneda(),f.estado(),f.fechaAnulacion(),f.observaciones(),
            detalle.neto(),detalle.iva(),detalle.total(),List.of(detalle),f.numero(),tipo,f.estadoArca(),f.informadaArca(),
            f.tipoAutorizacion(),f.codigoAutorizacion(),f.vencimientoAutorizacion(),f.fechaAutorizacion(),f.motivoAnulacion(),
            f.editable(),f.eliminable(),tipo.equals("1")?"20123456786":f.clienteDocumento(),f.puntoVentaCodigo(),f.condicionIvaDescripcion(),
            f.ambienteArca(),tipo.equals("1")?80:96,tipo.equals("1")?1:5,f.clienteDomicilio(),f.condicionVenta(),f.listaPrecioId(),LetraComprobante.desdeTipo(tipo),detalle.neto(),TipoDescuento.SIN_DESCUENTO,BigDecimal.ZERO,BigDecimal.ZERO);
        String letra=tipo.equals("1")?"A":"B";
        byte[] bytes=pdf.generar(ab,new FacturaPdfService.Credito(tipo.equals("1")?"3":"8",1L,"CAE","98765432109876",LocalDate.of(2026,11,21)));
        try(var doc=Loader.loadPDF(bytes)) {
            String texto=new PDFTextStripper().getText(doc);
            assertThat(texto).contains("CÓD. "+(tipo.equals("1")?"001":"006"),"2.420,00","420,00","Subtotal neto","IVA 21%",
                "Nota de crédito "+letra,"HOMOLOGACIÓN - SIN VALIDEZ FISCAL").doesNotContain("COMPROBANTE LOCAL","IVA sin discriminar");
            if(tipo.equals("1")) assertThat(texto).contains("CUIT: 20123456786","IVA Responsable Inscripto");
            guardar("factura-"+letra.toLowerCase()+"-prueba",bytes,doc);
        }
    }
    @Test void comprobanteXMuestraLetraYLeyendaSinEsperarAutorizacionFiscal() throws Exception {
        var f=factura(false,false,100,"Comprobante local de prueba");
        var x=new FacturaReporteDTO(f.id(),f.version(),f.fecha(),f.clienteId(),f.clienteNombre(),f.condicionIvaId(),
            f.puntoVentaId(),f.tipoMonedaId(),f.moneda(),f.estado(),f.fechaAnulacion(),f.observaciones(),
            f.neto(),f.iva(),f.total(),f.detalles(),null,null,f.estadoArca(),false,
            null,null,null,null,null,true,true,f.clienteDocumento(),f.puntoVentaCodigo(),f.condicionIvaDescripcion(),
            null,null,null,f.clienteDomicilio(),f.condicionVenta(),f.listaPrecioId(),LetraComprobante.X,f.subtotal(),TipoDescuento.SIN_DESCUENTO,BigDecimal.ZERO,BigDecimal.ZERO);
        byte[] bytes=pdf.generar(x);
        try(var doc=Loader.loadPDF(bytes)) {
            assertThat(doc.getNumberOfPages()).isGreaterThan(1);
            var extractor=new PDFTextStripper();
            for(int i=1;i<=doc.getNumberOfPages();i++) {
                extractor.setStartPage(i); extractor.setEndPage(i);
                assertThat(extractor.getText(doc)).contains("COMPROBANTE X","DOCUMENTO NO VÁLIDO COMO FACTURA")
                    .doesNotContain("PENDIENTE DE AUTORIZACIÓN","CAE:","CÓD. 011");
            }
            assertThat(FacturaQr.url(x,emisor.cuit())).isEmpty();
            guardar("comprobante-x-prueba",bytes,doc);
        }
    }
    void guardar(String nombre,byte[] bytes,org.apache.pdfbox.pdmodel.PDDocument doc) throws Exception {
        Path dir=Path.of("tmp","pdfs"); Files.createDirectories(dir); Files.write(dir.resolve(nombre+".pdf"),bytes);
        var render=new PDFRenderer(doc);
        javax.imageio.ImageIO.write(render.renderImageWithDPI(0,120),"png",dir.resolve(nombre+".png").toFile());
        if(doc.getNumberOfPages()>1) javax.imageio.ImageIO.write(render.renderImageWithDPI(doc.getNumberOfPages()-1,120),"png",dir.resolve(nombre+"-ultima.png").toFile());
    }
}
