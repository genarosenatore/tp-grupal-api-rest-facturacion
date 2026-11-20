package ar.edu.utn.facturacion.fiscal;

import ar.edu.utn.facturacion.dto.FacturaReporteDTO;
import ar.edu.utn.facturacion.entity.ReservaFiscal;
import ar.edu.utn.facturacion.exception.ConflictoNegocioException;
import ar.edu.utn.facturacion.service.ReservaFiscalService;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.w3c.dom.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.util.*;
import static ar.edu.utn.facturacion.fiscal.SoapArca.*;

@Service
@ConditionalOnProperty(name="arca.homologacion.enabled",havingValue="true")
public class WsfeHomologacionGateway implements ArcaGateway {
    private static final DateTimeFormatter FECHA=DateTimeFormatter.BASIC_ISO_DATE;
    private final SoapArca soap;
    private final WsaaHomologacion wsaa;
    private final ReservaFiscalService reservas;
    private final int punto;
    @Autowired public WsfeHomologacionGateway(ReservaFiscalService reservas,
        @Value("${arca.homologacion.directorio:.local/arca-homologacion}") String directorio,
        @Value("${arca.homologacion.punto-venta:2}") int punto) {
        this(reservas,new SoapArca(),Path.of(directorio),punto);
    }
    public WsfeHomologacionGateway(ReservaFiscalService reservas,SoapArca soap,Path directorio,int punto) {
        this.reservas=reservas; this.soap=soap; wsaa=new WsaaHomologacion(directorio,soap); this.punto=punto;
    }
    @Override public String ambiente() { return "HOMOLOGACION"; }
    @Override public void verificarConfiguracion() {
        if (punto<1 || punto>99999) throw new ConflictoNegocioException("Punto de venta de homologación inválido.");
        try { wsaa.certificado(); wsaa.cuit(); }
        catch (RuntimeException e) { throw new ConflictoNegocioException("Falta configurar un certificado vigente de homologación y su CUIT."); }
    }
    @Override public void verificarFactura(FacturaReporteDTO f,String operacion) {
        if (!ReglasHomologacion.admite(f.tipoComprobante())) throw new ConflictoNegocioException("Seleccioná Factura A, B o C (homologación) en el formulario.");
        if (!"ARS".equals(f.moneda()) || f.total().signum()<=0 || f.neto().add(f.iva()).compareTo(f.total())!=0)
            throw new ConflictoNegocioException("Homologación requiere pesos, importe positivo y total igual a neto más IVA.");
        if ("11".equals(f.tipoComprobante()) && f.iva().signum()!=0)
            throw new ConflictoNegocioException("La Factura C requiere IVA sin discriminar.");
        try {
            ReglasHomologacion.validarReceptor(f.tipoComprobante(), f.tipoDocumentoReceptor(), f.condicionIvaReceptorId(), f.clienteDocumento());
            if (!"11".equals(f.tipoComprobante())) gruposIva(f);
        } catch (IllegalArgumentException error) { throw new ConflictoNegocioException(error.getMessage()); }
        if (!String.valueOf(punto).equals(f.puntoVentaCodigo().replaceFirst("^0+", "")))
            throw new ConflictoNegocioException("Seleccioná el punto de venta " + punto + " del escenario de homologación.");
        documento(f);
        if (operacion.equals("ANULAR") && !"HOMOLOGACION".equals(f.ambienteArca()))
            throw new ConflictoNegocioException("Este adaptador solo ajusta comprobantes autorizados en homologación.");
    }
    @Override public Resultado ejecutar(UUID clave,String operacion,FacturaReporteDTO f) {
        boolean ajuste=operacion.equals("ANULAR"); int tipo=ajuste?ReglasHomologacion.credito(f.tipoComprobante()):Integer.parseInt(f.tipoComprobante());
        ReservaFiscalService.Preparacion p;
        try {
            p=reservas.preparar(clave,wsaa.cuit(),punto,tipo,f.id(),ajuste,
                ajuste?LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")):f.fecha(),()->ultimo(tipo));
        } catch (ReservaFiscalService.SerieOcupada ocupada) {
            return sinEnvio("Hay otra operación activa en esa serie. Primero debe confirmarse o conciliarse.");
        } catch (ConsultaDeNumeracionFallida falla) {
            return sinEnvio("No se pudo consultar la numeración antes del envío. Intentá nuevamente.");
        }
        if (!p.nueva()) return consultar(clave,operacion,f);
        var r=p.reserva();
        return interpretar(r,f,llamar("FECAESolicitar",solicitud(r,f)));
    }
    @Override public Resultado consultar(UUID clave,String operacion,FacturaReporteDTO f) {
        var existente=reservas.buscar(clave);
        // La escritura de la reserva puede estar en curso en otra transacción.
        if (existente.isEmpty()) return new Resultado(false, false,
            "La reserva fiscal aún no está confirmada. Requiere conciliación.", null, null, null, null, null, null, null);
        var r=existente.get();
        if (r.getFacturaId()!=f.id()) throw new IllegalStateException("La reserva pertenece a otra factura.");
        if (Set.of("CONFIRMADA","RECHAZADA").contains(r.getEstado()))
            return interpretar(r,f,new SoapArca.Respuesta(xml(r.getRespuesta()),r.getRespuesta()));
        var respuesta=llamar("FECompConsultar","<FeCompConsReq>"+campo("CbteTipo",r.getTipo())+campo("CbteNro",r.getNumero())+campo("PtoVta",r.getPuntoVenta())+"</FeCompConsReq>");
        // Una ausencia no prueba que un pedido demorado no vaya a procesarse. No liberar/reemitir.
        if (elemento(respuesta.documento(),"ResultGet")==null)
            return new Resultado(false,false,"ARCA todavía no confirma el comprobante reservado. Requiere conciliación.",respuesta.auditoria(),null,null,null,null,null,null);
        return interpretar(r,f,respuesta);
    }
    private Resultado sinEnvio(String motivo) {
        return new Resultado(false,true,motivo,"<SinEnvio>"+e(motivo)+"</SinEnvio>",null,null,null,null,null,null);
    }
    private static final class ConsultaDeNumeracionFallida extends RuntimeException {
        ConsultaDeNumeracionFallida(RuntimeException causa) { super(causa); }
    }
    private long ultimo(int tipo) {
        try {
            var respuesta = llamar("FECompUltimoAutorizado", campo("PtoVta", punto) + campo("CbteTipo", tipo));
            var doc = respuesta.documento();
            if (elemento(doc, "Err") != null) throw new IllegalStateException("ARCA rechazó la consulta de numeración.");
            return Long.parseLong(texto(doc, "CbteNro"));
        } catch (RuntimeException fallo) {
            // Esta consulta es de lectura y ocurre antes de reservar y solicitar el CAE.
            throw new ConsultaDeNumeracionFallida(fallo);
        }
    }
    private SoapArca.Respuesta llamar(String operacion,String cuerpo) {
        var t=wsaa.ticket();
        String auth="<Auth>"+campo("Token",t.token())+campo("Sign",t.sign())+campo("Cuit",t.cuit())+"</Auth>";
        return soap.llamar(false,NS+operacion,"<"+operacion+" xmlns=\""+NS+"\">"+auth+cuerpo+"</"+operacion+">");
    }
    private long documento(FacturaReporteDTO f) {
        if (Integer.valueOf(99).equals(f.tipoDocumentoReceptor())) return 0;
        String doc=f.clienteDocumento().replace("-", "");
        if ((Integer.valueOf(96).equals(f.tipoDocumentoReceptor()) && !doc.matches("[0-9]{7,8}"))
            || (Integer.valueOf(80).equals(f.tipoDocumentoReceptor()) && !doc.matches("[0-9]{11}")))
            throw new ConflictoNegocioException("El documento del cliente no coincide con el tipo indicado.");
        return Long.parseLong(doc);
    }
    private String solicitud(ReservaFiscal r,FacturaReporteDTO f) {
        String detalle=campo("Concepto",1)+campo("DocTipo",f.tipoDocumentoReceptor())+campo("DocNro",documento(f))
            +campo("CbteDesde",r.getNumero())+campo("CbteHasta",r.getNumero())+campo("CbteFch",FECHA.format(r.getFecha()))
            +campo("ImpTotal",f.total().toPlainString())+campo("ImpTotConc","0.00")+campo("ImpNeto",f.neto().toPlainString())
            +campo("ImpOpEx","0.00")+campo("ImpTrib","0.00")+campo("ImpIVA",f.iva().toPlainString())
            +campo("MonId","PES")+campo("MonCotiz","1")+campo("CondicionIVAReceptorId",f.condicionIvaReceptorId());
        if (r.isCompensacion()) detalle+="<CbtesAsoc><CbteAsoc>"+campo("Tipo",f.tipoComprobante())+campo("PtoVta",punto)
            +campo("Nro",f.numero())+campo("Cuit",wsaa.cuit())+campo("CbteFch",FECHA.format(f.fecha()))+"</CbteAsoc></CbtesAsoc>";
        if (!"11".equals(f.tipoComprobante())) {
            detalle += "<Iva>";
            for (var g : gruposIva(f).entrySet()) detalle += "<AlicIva>" + campo("Id", g.getKey())
                + campo("BaseImp", g.getValue().neto().toPlainString()) + campo("Importe", g.getValue().iva().toPlainString()) + "</AlicIva>";
            detalle += "</Iva>";
        }
        return "<FeCAEReq><FeCabReq>"+campo("CantReg",1)+campo("PtoVta",punto)+campo("CbteTipo",r.getTipo())
            +"</FeCabReq><FeDetReq><FECAEDetRequest>"+detalle+"</FECAEDetRequest></FeDetReq></FeCAEReq>";
    }
    private Resultado interpretar(ReservaFiscal r,FacturaReporteDTO f,SoapArca.Respuesta respuesta) {
        var doc=respuesta.documento(); Element consulta=elemento(doc,"ResultGet");
        Element det=consulta!=null?consulta:elemento(doc,"FECAEDetResponse");
        String mensajes=mensajes(doc);
        if (det==null || !"A".equals(texto(det,"Resultado"))) {
            // Consecutividad puede señalar un comprobante ya procesado: exige consulta, no reenvío.
            if (mensajes.contains("10016") || (det!=null && !"R".equals(texto(det,"Resultado")))
                    || (det==null && elemento(doc,"Err")==null))
                return new Resultado(false,false,"La respuesta requiere conciliación: "+mensajes,respuesta.auditoria(),null,null,null,null,null,null);
            reservas.finalizar(r.getId(),"RECHAZADA",respuesta.auditoria());
            return new Resultado(false,true,mensajes.isBlank()?"Comprobante rechazado por ARCA":mensajes,respuesta.auditoria(),null,null,null,null,null,null);
        }
        if (!mismo(det,"DocTipo",f.tipoDocumentoReceptor()) || !mismo(det,"DocNro",documento(f))
            || !mismo(det,"CbteDesde",r.getNumero()) || !mismo(det,"CbteHasta",r.getNumero())
            || !FECHA.format(r.getFecha()).equals(texto(det,"CbteFch")))
            throw new IllegalStateException("Los datos devueltos no corresponden a la reserva fiscal.");
        if (consulta!=null) {
            if (!mismo(det,"PtoVta",punto) || !mismo(det,"CbteTipo",r.getTipo())
                    || !igualImporte(det,"ImpTotal",f.total()) || !igualImporte(det,"ImpIVA",f.iva())
                    || !igualImporte(det,"ImpNeto",f.neto()) || !"PES".equals(texto(det,"MonId")))
                throw new IllegalStateException("La consulta no coincide con los importes o la serie reservada.");
            if (!"11".equals(f.tipoComprobante())) verificarIvaConsultado(det, f);
            if (r.isCompensacion()) {
                var asociado=elemento(det,"CbteAsoc");
                if (asociado==null || !mismo(asociado,"Tipo",f.tipoComprobante()) || !mismo(asociado,"PtoVta",punto) || !mismo(asociado,"Nro",f.numero()))
                    throw new IllegalStateException("No pudo verificarse el comprobante asociado al ajuste.");
            }
        } else {
            var cab=elemento(doc,"FeCabResp");
            if (cab==null || !mismo(cab,"PtoVta",punto) || !mismo(cab,"CbteTipo",r.getTipo()))
                throw new IllegalStateException("La respuesta tiene una cabecera diferente de la reserva.");
        }
        String codigo=texto(det,consulta!=null?"CodAutorizacion":"CAE");
        String vto=texto(det,consulta!=null?"FchVto":"CAEFchVto");
        if (!codigo.matches("[0-9]{14}") || !vto.matches("[0-9]{8}")) throw new IllegalStateException("Autorización ARCA incompleta.");
        LocalDate fecha=LocalDate.parse(vto,FECHA);
        reservas.finalizar(r.getId(),"CONFIRMADA",respuesta.auditoria());
        return new Resultado(true,true,mensajes.isBlank()?"Autorizado en homologación":mensajes,respuesta.auditoria(),
            String.valueOf(r.getTipo()),r.getNumero(),"CAE",codigo,fecha,r.isCompensacion()?f.id():null);
    }
    private boolean mismo(Node nodo,String nombre,Object valor) {
        try { return new BigDecimal(texto(nodo,nombre)).compareTo(new BigDecimal(String.valueOf(valor)))==0; }
        catch (NumberFormatException e) { return false; }
    }
    private boolean igualImporte(Node n,String campo,BigDecimal valor) { return mismo(n,campo,valor); }
    private record GrupoIva(BigDecimal neto, BigDecimal iva) {}
    private Map<Integer, GrupoIva> gruposIva(FacturaReporteDTO f) {
        var grupos = new TreeMap<Integer, GrupoIva>();
        for (var d : f.detalles()) {
            int id = ReglasHomologacion.idIva(d.alicuotaIva());
            grupos.merge(id, new GrupoIva(d.neto(), d.iva()), (a, b) -> new GrupoIva(a.neto().add(b.neto()), a.iva().add(b.iva())));
        }
        // Los renglones gratuitos no generan bases imponibles de cero en WSFE.
        grupos.entrySet().removeIf(g -> g.getValue().neto().signum() == 0);
        return grupos;
    }
    private void verificarIvaConsultado(Element det, FacturaReporteDTO f) {
        var esperados = new TreeMap<>(gruposIva(f));
        var filas = det.getElementsByTagNameNS("*", "AlicIva");
        for (int i = 0; i < filas.getLength(); i++) {
            var fila = filas.item(i);
            GrupoIva g;
            try { g = esperados.remove(Integer.parseInt(texto(fila, "Id"))); }
            catch (NumberFormatException error) { throw new IllegalStateException("Alícuota de la consulta inválida."); }
            if (g == null || !igualImporte(fila, "BaseImp", g.neto()) || !igualImporte(fila, "Importe", g.iva()))
                throw new IllegalStateException("El desglose de IVA consultado no coincide con la factura.");
        }
        if (!esperados.isEmpty()) throw new IllegalStateException("Falta el desglose de IVA en la consulta.");
    }
    private String mensajes(Document doc) {
        var out=new ArrayList<String>();
        for (String tag:List.of("Err","Obs")) {
            var nodes=doc.getElementsByTagNameNS("*",tag);
            for(int i=0;i<nodes.getLength();i++) out.add(texto(nodes.item(i),"Code")+" - "+texto(nodes.item(i),"Msg"));
        }
        return seguro(String.join("; ",out));
    }
}
