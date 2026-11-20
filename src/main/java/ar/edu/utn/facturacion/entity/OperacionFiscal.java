package ar.edu.utn.facturacion.entity;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

/** Intento durable previo a cualquier llamada externa. No contiene credenciales. */
@Entity @Table(name = "operacion_fiscal")
public class OperacionFiscal {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "factura_id")
    private FacturaVenta factura;
    @Column(nullable = false) private Long facturaOriginalId;
    @Column(length = 20) private String operacion;
    @Column(length = 20) private String estado;
    private Instant creada;
    private Instant finalizada;
    @Column(length = 1000) private String motivo;
    @Column(length = 2000) private String mensaje;
    @Column(columnDefinition = "text") private String respuesta;
    @Column(length = 40) private String tipoComprobante;
    private Long numero;
    @Column(length = 4) private String tipoAutorizacion;
    @Column(length = 30) private String codigoAutorizacion;
    private LocalDate vencimientoAutorizacion;
    protected OperacionFiscal() {}
    public OperacionFiscal(FacturaVenta factura, String operacion, String motivo, Instant ahora) {
        id = UUID.randomUUID(); this.factura = factura; facturaOriginalId = factura.getId(); this.operacion = operacion;
        this.motivo = motivo; creada = ahora; estado = "EN_PROCESO";
    }
    public void resultado(String estado, String mensaje, String respuesta, Instant ahora) {
        this.estado = estado; this.mensaje = mensaje == null ? null : mensaje.substring(0, Math.min(2000, mensaje.length()));
        this.respuesta = respuesta; finalizada = ahora;
    }
    public void autorizacion(String tipo, Long numero, String tipoCodigo, String codigo, LocalDate vto) {
        tipoComprobante = tipo; this.numero = numero; tipoAutorizacion = tipoCodigo;
        codigoAutorizacion = codigo; vencimientoAutorizacion = vto;
    }
    public UUID getId() { return id; }
    public FacturaVenta getFactura() { return factura; }
    public String getOperacion() { return operacion; }
    public String getEstado() { return estado; }
    public Instant getCreada() { return creada; }
    public Instant getFinalizada() { return finalizada; }
    public String getMotivo() { return motivo; }
    public String getTipoComprobante() { return tipoComprobante; }
    public Long getNumero() { return numero; }
    public String getTipoAutorizacion() { return tipoAutorizacion; }
    public String getCodigoAutorizacion() { return codigoAutorizacion; }
    public LocalDate getVencimientoAutorizacion() { return vencimientoAutorizacion; }
}
