package ar.edu.utn.facturacion.service;

import ar.edu.utn.facturacion.dto.*;
import ar.edu.utn.facturacion.entity.*;
import ar.edu.utn.facturacion.fiscal.ReglasHomologacion;
import ar.edu.utn.facturacion.repository.OperacionFiscalRepository;
import com.google.zxing.*;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.awt.Color;
import java.io.*;
import java.math.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

@Service
public class FacturaPdfService {
    private final FacturaService facturas;
    private final OperacionFiscalRepository operaciones;
    private final EmisorFactura emisor;
    public FacturaPdfService(FacturaService facturas, OperacionFiscalRepository operaciones, EmisorFactura emisor) {
        this.facturas=facturas; this.operaciones=operaciones; this.emisor=emisor;
    }
    @Transactional(readOnly=true)
    public byte[] generar(Long id) {
        var f=facturas.obtener(id);
        var credito=f.estado()==EstadoFactura.ANULADA ? operaciones
            .findFirstByFacturaIdAndOperacionAndEstadoOrderByCreadaDesc(id,"ANULAR","APROBADA")
            .filter(o -> o.getCodigoAutorizacion()!=null && o.getNumero()!=null)
            .map(o -> new Credito(o.getTipoComprobante(),o.getNumero(),o.getTipoAutorizacion(),
                o.getCodigoAutorizacion(),o.getVencimientoAutorizacion())).orElse(null) : null;
        return generar(f,credito);
    }
    public byte[] generar(FacturaReporteDTO f) { return generar(f,null); }
    record Credito(String tipo,Long numero,String tipoCodigo,String codigo,LocalDate vencimiento) {}
    byte[] generar(FacturaReporteDTO f,Credito credito) {
        try(var doc=new PDDocument(); var bytes=new ByteArrayOutputStream()) {
            doc.getDocumentInformation().setTitle("Factura "+numero(f.puntoVentaCodigo(),f.numero()));
            doc.getDocumentInformation().setAuthor(dato(emisor.razonSocial()));
            try(var l=new Lienzo(doc,f,emisor)) {
                l.cabecera(); l.cliente(); l.estado(); l.tabla();
                int i=0;
                for(var d:f.detalles()) l.renglon(d,++i);
                l.resumen(); l.observaciones(); l.anulacion(credito); l.autorizacion(); l.pies();
            }
            doc.save(bytes); return bytes.toByteArray();
        } catch(IOException|WriterException ex) { throw new IllegalStateException("No se pudo generar el PDF de la factura",ex); }
    }
    private static final DateTimeFormatter FECHA=DateTimeFormatter.ofPattern("dd/MM/uuuu");
    private static final ZoneId ZONA=ZoneId.of("America/Argentina/Buenos_Aires");
    private static String fecha(LocalDate d) { return d==null?"No informado":FECHA.format(d); }
    private static String dato(String s) { return s==null||s.isBlank()?"No informado":s; }
    static String numero(String punto,Long n) {
        String pv=Objects.toString(punto,"");
        if(pv.matches("[0-9]{1,5}")) pv="%05d".formatted(Integer.parseInt(pv));
        return n==null?"SIN NÚMERO":pv+"-"+"%08d".formatted(n);
    }
    private static String monto(BigDecimal n) { return new DecimalFormat("#,##0.00",DecimalFormatSymbols.getInstance(Locale.forLanguageTag("es-AR"))).format(n); }
    private static String cantidad(BigDecimal n) { return new DecimalFormat("#,##0.###",DecimalFormatSymbols.getInstance(Locale.forLanguageTag("es-AR"))).format(n); }
    private static String cuit(String s) {
        String n=Objects.toString(s,"").replace("-","");
        return n.matches("[0-9]{11}")?n.substring(0,2)+"-"+n.substring(2,10)+"-"+n.substring(10):dato(s);
    }

    private static class Lienzo implements AutoCloseable {
        static final float X=36,W=523,PISO=213;
        static final Color TINTA=new Color(25,43,61),GRIS=new Color(91,103,113),BORDE=new Color(204,212,219),
            FONDO=new Color(242,245,248),ROJO=new Color(153,40,40);
        final PDDocument doc;
        final FacturaReporteDTO f;
        final EmisorFactura e;
        final boolean c;
        final boolean fiscal;
        final PDFont regular=new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        final PDFont negrita=new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        PDPageContentStream p;
        float y;
        boolean observacionesEnResumen;
        Lienzo(PDDocument doc,FacturaReporteDTO f,EmisorFactura e) throws IOException {
            this.doc=doc; this.f=f; this.e=e; c="11".equals(f.tipoComprobante()); fiscal=ReglasHomologacion.admite(f.tipoComprobante()); nueva(false);
        }
        String ambiente() {
            if(!fiscal && !f.informadaArca()) return "DOCUMENTO NO VÁLIDO COMO FACTURA";
            if("HOMOLOGACION".equals(f.ambienteArca())) return "HOMOLOGACIÓN - SIN VALIDEZ FISCAL";
            if("PRUEBA_CONTROLADA".equals(f.ambienteArca())) return "PRUEBA CONTROLADA - SIN VALIDEZ FISCAL";
            if(!f.informadaArca()) return "PENDIENTE DE AUTORIZACIÓN - SIN VALIDEZ FISCAL";
            return "COMPROBANTE ELECTRÓNICO";
        }
        void nueva(boolean continuacion) throws IOException {
            if(p!=null) p.close();
            var hoja=new PDPage(PDRectangle.A4); doc.addPage(hoja); p=new PDPageContentStream(doc,hoja);
            texto(X,808,"ORIGINAL",negrita,9,TINTA); derecha(X+W,808,ambiente(),negrita,8,GRIS);
            if(continuacion) {
                texto(X,778,fiscal?"FACTURA "+f.letraComprobante().name():"COMPROBANTE "+f.letraComprobante().name(),negrita,16,TINTA);
                derecha(X+W,778,numero(f.puntoVentaCodigo(),f.numero()),negrita,12,TINTA);
                var filas=lineas("Cliente: "+f.clienteNombre()+" | Fecha: "+fecha(f.fecha()),regular,9,W);
                float yy=757;
                for(String s:filas) { texto(X,yy,s,regular,9,GRIS); yy-=13; }
                y=yy-9;
            }
        }
        void cabecera() throws IOException {
            var nombres=lineas(dato(e.razonSocial()),negrita,16,227);
            var direcciones=lineas("Domicilio comercial: "+dato(e.domicilio()),regular,9,227);
            String condicionEmisor=e.datosDemostracion() && fiscal && !c ? "Responsable Inscripto (escenario de prueba)" : dato(e.condicionIva());
            var condiciones=lineas("IVA: "+condicionEmisor,negrita,9,227);
            var iibb=lineas("Ingresos Brutos: "+dato(e.ingresosBrutos()),regular,9,220);
            float alto=Math.max(160,60+nombres.size()*19+direcciones.size()*13+condiciones.size()*13+(e.datosDemostracion()?20:0));
            alto=Math.max(alto,144+iibb.size()*12);
            float abajo=796-alto;
            caja(X,abajo,W,alto,Color.WHITE,BORDE);
            p.setStrokingColor(BORDE); p.moveTo(310,abajo); p.lineTo(310,736); p.stroke();
            caja(287,745,46,46,Color.WHITE,TINTA); texto(298,759,f.letraComprobante().name(),negrita,29,TINTA);
            centrado(310,735,fiscal?"CÓD. "+"%03d".formatted(Integer.parseInt(f.tipoComprobante())):"LOCAL",negrita,7,GRIS);
            float yy=768;
            for(String s:nombres) { texto(48,yy,s,negrita,16,TINTA); yy-=19; }
            yy-=9;
            for(String s:direcciones) { texto(48,yy,s,regular,9,TINTA); yy-=13; }
            for(String s:condiciones) { texto(48,yy-8,s,negrita,9,TINTA); yy-=13; }
            if(e.datosDemostracion()) texto(48,abajo+12,"Datos comerciales ficticios para el TP",regular,8,GRIS);
            texto(347,769,fiscal?"FACTURA":"COMPROBANTE X",negrita,fiscal?21:16,TINTA);
            texto(347,745,numero(f.puntoVentaCodigo(),f.numero()),negrita,12,TINTA);
            texto(347,724,"Fecha de emisión: "+fecha(f.fecha()),regular,9,TINTA);
            texto(326,700,"CUIT: "+cuit(e.cuit()),negrita,9,TINTA);
            bloque("Ingresos Brutos: "+dato(e.ingresosBrutos()),326,683,220,regular,9,12,TINTA);
            texto(326,abajo+14,"Inicio de actividades: "+fecha(e.inicioActividades()),regular,9,TINTA);
            y=abajo-13;
        }
        void cliente() throws IOException {
            var filas=new ArrayList<String>();
            filas.addAll(lineas("Apellido y nombre / Razón social: "+dato(f.clienteNombre()),negrita,10,W-24));
            String tipo=Objects.equals(f.tipoDocumentoReceptor(),80)?"CUIT":Objects.equals(f.tipoDocumentoReceptor(),96)?"DNI":"Documento";
            String documento=Objects.equals(f.tipoDocumentoReceptor(),99)?"Consumidor final sin identificar":dato(f.clienteDocumento());
            filas.addAll(lineas(tipo+": "+documento+"     |     Condición IVA: "+(f.condicionIvaReceptorId()!=null?ReglasHomologacion.condicion(f.condicionIvaReceptorId()):dato(f.condicionIvaDescripcion())),regular,9,W-24));
            String domicilio=f.clienteDomicilio();
            if((domicilio==null||domicilio.isBlank()) && c && Integer.valueOf(5).equals(f.condicionIvaReceptorId())) domicilio="NR (No requerido - consumidor final)";
            filas.addAll(lineas("Domicilio: "+dato(domicilio),regular,9,W-24));
            filas.addAll(lineas("Condición de venta: "+dato(f.condicionVenta())+"     |     Moneda: "+dato(f.moneda()),regular,9,W-24));
            float alto=filas.size()*15+20;
            caja(X,y-alto,W,alto,Color.WHITE,BORDE);
            float yy=y-18;
            for(int i=0;i<filas.size();i++) { texto(48,yy,filas.get(i),i==0?negrita:regular,i==0?10:9,TINTA); yy-=15; }
            y-=alto+12;
        }
        void estado() throws IOException {
            var avisos=new ArrayList<String>();
            if(f.estado()==EstadoFactura.ANULADA) avisos.add("ANULADA - ver comprobante de anulación al pie");
            if(!fiscal && !f.informadaArca()) avisos.add("COMPROBANTE X - DOCUMENTO NO VÁLIDO COMO FACTURA");
            else if(!f.informadaArca()) avisos.add("PENDIENTE DE AUTORIZACIÓN - SIN VALIDEZ FISCAL");
            else if(f.estadoArca()!=EstadoArca.AUTORIZADA) avisos.add("OPERACIÓN FISCAL EN CONCILIACIÓN");
            if(!e.completo()) avisos.add("DATOS DEL EMISOR INCOMPLETOS - completar configuración");
            for(String s:avisos) { caja(X,y-23,W,23,FONDO,BORDE); texto(48,y-15,s,negrita,8.5f,f.estado()==EstadoFactura.ANULADA?ROJO:TINTA); y-=29; }
            y-=2;
        }
        void tabla() throws IOException {
            caja(X,y-25,W,25,TINTA,TINTA);
            texto(44,y-16,"ARTÍCULO / DESCRIPCIÓN",negrita,8,Color.WHITE);
            derecha(350,y-16,"CANT.",negrita,8,Color.WHITE); derecha(443,y-16,"PRECIO UNIT.",negrita,8,Color.WHITE);
            derecha(551,y-16,"IMPORTE",negrita,8,Color.WHITE); y-=25;
        }
        void asegurar(float alto,boolean tabla) throws IOException {
            if(y-alto<PISO) { nueva(true); if(tabla) tabla(); }
        }
        void renglon(FacturaDetalleReporteDTO d,int i) throws IOException {
            var descripcion=lineas(d.descripcion(),regular,9,259);
            if(d.descuento().signum()>0) descripcion.addAll(lineas("Descuento aplicado: "+monto(d.descuento())+" "+f.moneda(),regular,9,259));
            int posicion=0;
            while(posicion<descripcion.size()) {
                asegurar(42,true);
                int capacidad=Math.max(1,(int)((y-PISO-22)/13));
                int cant=Math.min(descripcion.size()-posicion,capacidad);
                float alto=Math.max(36,cant*13+22);
                caja(X,y-alto,W,alto,i%2==0?FONDO:Color.WHITE,BORDE);
                for(int j=0;j<cant;j++) texto(44,y-16-j*13,descripcion.get(posicion+j),regular,9,TINTA);
                if(posicion==0) {
                    texto(44,y-alto+9,"Artículo interno "+d.articuloId()+(c?"":"  |  IVA "+cantidad(d.alicuotaIva())+"%"),regular,7,GRIS);
                    derecha(350,y-17,cantidad(d.cantidad())+" un.",regular,9,TINTA);
                    derecha(443,y-17,monto(d.precioUnitario()),regular,9,TINTA);
                    derecha(551,y-17,monto(d.total()),negrita,9,TINTA);
                } else texto(44,y-alto+9,"Continuación del artículo "+d.articuloId(),regular,7,GRIS);
                y-=alto; posicion+=cant;
            }
        }
        void resumen() throws IOException {
            boolean descuento=f.importeDescuento().signum()>0;
            y-=13; asegurar(descuento?(c?92:115):(c?82:119),false);
            texto(X,y-12,c?"Precios finales. IVA sin discriminar.":"Importes expresados en "+f.moneda()+".",regular,8,GRIS);
            if(descuento) {
                if(!c) texto(X,y-28,"Neto con descuento: "+monto(f.neto())+" "+f.moneda(),regular,8,GRIS);
                if(f.observaciones()!=null&&!f.observaciones().isBlank()) {
                    var notas=lineas(f.observaciones(),regular,8,267);
                    if(notas.size()<=4) {
                        texto(X,y-47,"OBSERVACIONES",negrita,8,TINTA);
                        for(int i=0;i<notas.size();i++) texto(X,y-61-i*12,notas.get(i),regular,8,TINTA);
                        observacionesEnResumen=true;
                    }
                }
                total("Subtotal",f.subtotal(),false);
                total(f.tipoDescuento()==TipoDescuento.PORCENTAJE?"Descuento "+cantidad(f.valorDescuento())+"%":"Descuento general",f.importeDescuento(),false);
            }
            if(!c) { if(!descuento) total("Subtotal neto",f.neto(),false); total("IVA",f.iva(),false); }
            else if(!descuento) total("Subtotal final",f.total(),false);
            total("TOTAL "+f.moneda(),f.total(),true); y-=10;
        }
        void total(String etiqueta,BigDecimal valor,boolean grande) throws IOException {
            float alto=grande?34:23;
            caja(327,y-alto,232,alto,grande?TINTA:FONDO,grande?TINTA:BORDE);
            texto(338,y-(grande?22:15),etiqueta,negrita,grande?11:9,grande?Color.WHITE:TINTA);
            derecha(548,y-(grande?22:15),monto(valor),negrita,grande?14:10,grande?Color.WHITE:TINTA); y-=alto;
        }
        void observaciones() throws IOException {
            if(!observacionesEnResumen && f.observaciones()!=null&&!f.observaciones().isBlank()) parrafos("OBSERVACIONES",f.observaciones(),TINTA);
        }
        void anulacion(Credito credito) throws IOException {
            if(f.estado()!=EstadoFactura.ANULADA) return;
            String instante=f.fechaAnulacion()==null?"No informada":DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm").withZone(ZONA).format(f.fechaAnulacion())+" (Argentina)";
            var partes=new ArrayList<String>(); partes.add("Fecha: "+instante+". Motivo: "+dato(f.motivoAnulacion()));
            if(credito!=null) {
                partes.add((Set.of("3","8","13").contains(credito.tipo())?"Nota de crédito "+ReglasHomologacion.letra(credito.tipo()):"Comprobante de anulación "+credito.tipo())+" "+numero(f.puntoVentaCodigo(),credito.numero()));
                partes.add(credito.tipoCodigo()+": "+credito.codigo()+" | Vencimiento: "+fecha(credito.vencimiento()));
            } else partes.add(f.informadaArca()?"Sin referencia fiscal de anulación disponible. Revisar auditoría.":"Anulación administrativa local; sin comprobante fiscal asociado.");
            parrafos("ANULACIÓN DEL COMPROBANTE",String.join("\n",partes),ROJO);
        }
        void parrafos(String titulo,String contenido,Color color) throws IOException {
            var filas=lineas(contenido,regular,8,W-20);
            int indice=0;
            while(indice<filas.size()) {
                asegurar(48,false);
                int cant=Math.min(filas.size()-indice,Math.max(1,(int)((y-PISO-29)/12)));
                float alto=29+cant*12;
                caja(X,y-alto,W,alto,FONDO,BORDE); texto(46,y-14,titulo+(indice==0?"":" (continuación)"),negrita,8,color);
                for(int i=0;i<cant;i++) texto(46,y-29-i*12,filas.get(indice+i),regular,8,TINTA);
                indice+=cant; y-=alto+9;
            }
        }
        void autorizacion() throws IOException,WriterException {
            if(y<209) nueva(true);
            p.setStrokingColor(BORDE); p.moveTo(X,199); p.lineTo(X+W,199); p.stroke();
            if(!fiscal && !f.informadaArca()) {
                texto(X,176,"COMPROBANTE X - DOCUMENTO NO VÁLIDO COMO FACTURA",negrita,10,TINTA);
                texto(X,153,"Comprobante local sin envío fiscal. No corresponde autorización ni QR fiscal.",regular,9,GRIS);
                return;
            }
            var qr=FacturaQr.url(f,e.cuit());
            if(qr.isPresent()) {
                var matriz=new QRCodeWriter().encode(qr.get(),BarcodeFormat.QR_CODE,0,0,
                    Map.of(EncodeHintType.ERROR_CORRECTION,ErrorCorrectionLevel.M,EncodeHintType.MARGIN,4));
                float lado=122,modulo=lado/matriz.getWidth(); p.setNonStrokingColor(Color.BLACK);
                for(int fila=0;fila<matriz.getHeight();fila++) for(int col=0;col<matriz.getWidth();col++)
                    if(matriz.get(col,fila)) p.addRect(X+col*modulo,64+(matriz.getHeight()-1-fila)*modulo,modulo,modulo);
                p.fill();
            } else {
                caja(X+4,79,111,88,FONDO,BORDE); centrado(X+59,133,"SIN QR FISCAL",negrita,10,GRIS);
                centrado(X+59,116,"Datos de autorización",regular,8,GRIS); centrado(X+59,103,"no disponibles",regular,8,GRIS);
            }
            texto(178,176,"AUTORIZACIÓN DEL COMPROBANTE ORIGINAL",negrita,9,TINTA);
            if(f.codigoAutorizacion()!=null) {
                texto(178,151,dato(f.tipoAutorizacion())+": "+f.codigoAutorizacion(),negrita,16,TINTA);
                texto(178,132,"Fecha de vencimiento: "+fecha(f.vencimientoAutorizacion()),regular,10,TINTA);
                texto(178,114,"Tipo de comprobante: "+dato(f.tipoComprobante()),regular,8,GRIS);
            } else {
                texto(178,151,"PENDIENTE DE AUTORIZACIÓN",negrita,12,TINTA);
                texto(178,132,"Sin código ni vencimiento otorgados por ARCA.",regular,9,GRIS);
            }
            String aviso=qr.isPresent()&&"HOMOLOGACION".equals(f.ambienteArca())?"QR con datos de homologación. No acredita validez fiscal."
                :qr.isEmpty()?"No se genera un QR con datos fiscales incompletos o de prueba controlada."
                :"QR con los datos del comprobante según especificación ARCA.";
            bloque(aviso,178,93,369,regular,8,11,GRIS);
            if(e.datosDemostracion()) texto(178,64,"Identidad comercial de demostración. CUIT y CAE de homologación.",regular,7.5f,GRIS);
        }
        void pies() throws IOException {
            p.close(); p=null;
            for(int i=0;i<doc.getNumberOfPages();i++) {
                try(var pie=new PDPageContentStream(doc,doc.getPage(i),PDPageContentStream.AppendMode.APPEND,true,true)) {
                    p=pie; texto(X,38,"ID interno "+f.id()+" | "+(f.estado()==EstadoFactura.ANULADA?"ANULADA | ":"")+ambiente(),negrita,7,GRIS);
                    derecha(X+W,38,"Página "+(i+1)+" de "+doc.getNumberOfPages(),regular,8,GRIS);
                }
            }
            p=null;
        }
        List<String> lineas(String texto,PDFont font,float tam,float ancho) throws IOException {
            var resultado=new ArrayList<String>();
            for(String parrafo:seguro(Objects.toString(texto,"")).split("\\R",-1)) {
                String resto=parrafo;
                do {
                    int fin=0;
                    while(fin<resto.length()&&font.getStringWidth(resto.substring(0,fin+1))*tam/1000<=ancho) fin++;
                    if(fin<resto.length()) { int espacio=resto.lastIndexOf(' ',fin); if(espacio>0) fin=espacio; }
                    if(fin==0&&!resto.isEmpty()) fin=1;
                    resultado.add(resto.substring(0,fin)); resto=resto.substring(fin).stripLeading();
                } while(!resto.isEmpty());
            }
            return resultado;
        }
        void bloque(String s,float x,float yy,float ancho,PDFont font,float tam,float salto,Color color) throws IOException {
            for(String fila:lineas(s,font,tam,ancho)) { texto(x,yy,fila,font,tam,color); yy-=salto; }
        }
        void caja(float x,float abajo,float ancho,float alto,Color fondo,Color borde) throws IOException {
            p.setNonStrokingColor(fondo); p.addRect(x,abajo,ancho,alto); p.fill();
            p.setStrokingColor(borde); p.setLineWidth(.5f); p.addRect(x,abajo,ancho,alto); p.stroke();
        }
        void texto(float x,float yy,String s,PDFont font,float tam,Color color) throws IOException {
            p.setNonStrokingColor(color); p.beginText(); p.setFont(font,tam); p.newLineAtOffset(x,yy); p.showText(seguro(s)); p.endText();
        }
        void derecha(float x,float yy,String s,PDFont font,float tam,Color color) throws IOException {
            String limpio=seguro(s); float max=x==551?99:x==443?85:x==350?43:110;
            float ancho=font.getStringWidth(limpio)*tam/1000;
            if(x==551||x==443||x==350||x==548) { if(ancho>max) tam*=max/ancho; }
            texto(x-font.getStringWidth(limpio)*tam/1000,yy,limpio,font,tam,color);
        }
        void centrado(float x,float yy,String s,PDFont font,float tam,Color color) throws IOException {
            texto(x-font.getStringWidth(seguro(s))*tam/2000,yy,s,font,tam,color);
        }
        String seguro(String s) {
            var resultado=new StringBuilder();
            Objects.toString(s,"").codePoints().forEach(cp -> {
                if(cp=='\r'||cp=='\n') { resultado.append((char)cp); return; }
                String t=new String(Character.toChars(cp));
                try { regular.encode(t); resultado.append(t); } catch(IOException|IllegalArgumentException ex) { resultado.append('?'); }
            }); return resultado.toString();
        }
        @Override public void close() throws IOException { if(p!=null) p.close(); }
    }
}
