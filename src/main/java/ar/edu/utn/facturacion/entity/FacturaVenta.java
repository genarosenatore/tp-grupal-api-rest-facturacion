package ar.edu.utn.facturacion.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
@Entity
@Table(name = "factura_venta")
public class FacturaVenta {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Version
    private Long version;
    @Column(nullable = false)
    private LocalDate fecha;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "condicion_iva_id", nullable = false)
    private CondicionIva condicionIva;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "punto_venta_id", nullable = false)
    private PuntoVenta puntoVenta;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tipo_moneda_id", nullable = false)
    private TipoMoneda tipoMoneda;
    @Column(nullable = false, columnDefinition = "bigint default 1")
    private Long listaPrecioId = 1L;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EstadoFactura estado = EstadoFactura.EMITIDA;
    private Instant fechaAnulacion;
    @Column(length = 1000)
    private String observaciones;
    @Column(length = 250) private String clienteDomicilio;
    @Column(length = 80) private String condicionVenta;
    @Column(length = 120) private String clienteNombre;
    @Column(length = 30) private String clienteDocumento;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20) default 'SIN_DESCUENTO'")
    private TipoDescuento tipoDescuento = TipoDescuento.SIN_DESCUENTO;
    @Column(nullable = false, precision = 19, scale = 2, columnDefinition = "numeric(19,2) default 0")
    private BigDecimal valorDescuento = new BigDecimal("0.00");
    @Column(nullable = false, precision = 19, scale = 2, columnDefinition = "numeric(19,2) default 0")
    private BigDecimal importeDescuento = new BigDecimal("0.00");
    private Long numero;
    @Column(length = 40) private String tipoComprobante;
    @Enumerated(EnumType.STRING)
    @Column(name = "letra_comprobante", nullable = false, length = 1, columnDefinition = "varchar(1) default 'X'")
    private LetraComprobante letraComprobante = LetraComprobante.X;
    private Integer tipoDocumentoReceptor;
    private Integer condicionIvaReceptorId;
    @Column(length = 30) private String ambienteArca;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private EstadoArca estadoArca = EstadoArca.PENDIENTE;
    @Column(length = 4) private String tipoAutorizacion;
    @Column(length = 30) private String codigoAutorizacion;
    private LocalDate vencimientoAutorizacion;
    private Instant fechaAutorizacion;
    @Column(length = 1000) private String motivoAnulacion;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal neto = new BigDecimal("0.00");
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal iva = new BigDecimal("0.00");
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal total = new BigDecimal("0.00");
    @OneToMany(mappedBy = "factura", cascade = CascadeType.ALL, orphanRemoval = true)
    @org.hibernate.annotations.BatchSize(size = 32)
    @OrderBy("id ASC")
    private List<FacturaDetalle> detalles = new ArrayList<>();
    public FacturaVenta() {}
    public void actualizarCabecera(LocalDate fecha, Cliente cliente, CondicionIva condicion,
                                   PuntoVenta punto, TipoMoneda moneda, String observaciones) {
        this.fecha = fecha; this.cliente = cliente; this.condicionIva = condicion;
        this.puntoVenta = punto; this.tipoMoneda = moneda; this.observaciones = observaciones;
    }
    /** Mantener la colección administrada permite detectar los detalles huérfanos. */
    public void reemplazarDetalles(List<FacturaDetalle> nuevos) {
        detalles.clear();
        nuevos.forEach(d -> { d.asociarA(this); detalles.add(d); });
        neto = nuevos.stream().map(FacturaDetalle::getNeto).reduce(new BigDecimal("0.00"), BigDecimal::add);
        iva = nuevos.stream().map(FacturaDetalle::getIva).reduce(new BigDecimal("0.00"), BigDecimal::add);
        total = neto.add(iva);
    }
    public void anular(Instant instante) { estado = EstadoFactura.ANULADA; fechaAnulacion = instante.truncatedTo(java.time.temporal.ChronoUnit.MICROS); }
    public void cambiarObservaciones(String valor) { observaciones = valor; }
    public void anular(Instant instante, String motivo) { anular(instante); motivoAnulacion = motivo; }
    public void cambiarEstadoArca(EstadoArca valor) { estadoArca = valor; }
    public void autorizar(Long numero, String tipo, String tipoCodigo, String codigo, LocalDate vencimiento, Instant instante) {
        this.numero = numero; tipoComprobante = tipo; tipoAutorizacion = tipoCodigo;
        letraComprobante = LetraComprobante.desdeTipo(tipo);
        codigoAutorizacion = codigo; vencimientoAutorizacion = vencimiento;
        fechaAutorizacion = instante.truncatedTo(java.time.temporal.ChronoUnit.MICROS); estadoArca = EstadoArca.AUTORIZADA;
    }
    public boolean isInformadaArca() {
        return estadoArca == EstadoArca.AUTORIZADA || codigoAutorizacion != null || fechaAutorizacion != null;
    }
    public boolean isFiscalBloqueada() {
        return isInformadaArca() || estadoArca == EstadoArca.EN_PROCESO || estadoArca == EstadoArca.INCIERTA;
    }
    public boolean isEditable() { return estado == EstadoFactura.EMITIDA && !isFiscalBloqueada(); }
    public Long getNumero() { return numero; }
    public Long getListaPrecioId() { return listaPrecioId; }
    public void definirListaPrecio(Long id) { listaPrecioId = id; }
    public String getTipoComprobante() { return tipoComprobante; }
    public LetraComprobante getLetraComprobante() { return letraComprobante; }
    public void definirCircuito(String tipo, Integer documento, Integer condicion) {
        tipoComprobante = tipo; tipoDocumentoReceptor = documento; condicionIvaReceptorId = condicion;
        letraComprobante = LetraComprobante.desdeTipo(tipo);
    }
    public void definirAmbienteArca(String ambiente) { ambienteArca = ambiente; }
    public void definirDatosComerciales(String domicilio, String venta) {
        clienteDomicilio = limpiar(domicilio); condicionVenta = limpiar(venta);
    }
    public void copiarCliente(Cliente c) { clienteNombre = c.getNombre(); clienteDocumento = c.getDocumento(); clienteDomicilio = c.getDomicilio(); }
    public void definirDescuento(TipoDescuento tipo, BigDecimal valor, BigDecimal importe) {
        tipoDescuento = tipo; valorDescuento = valor; importeDescuento = importe;
    }
    public String getClienteNombre() { return clienteNombre; }
    public String getClienteDocumento() { return clienteDocumento; }
    public TipoDescuento getTipoDescuento() { return tipoDescuento; }
    public BigDecimal getValorDescuento() { return valorDescuento; }
    public BigDecimal getImporteDescuento() { return importeDescuento; }
    public BigDecimal getSubtotal() { return neto.add(importeDescuento); }
    private static String limpiar(String texto) { return texto == null || texto.isBlank() ? null : texto.strip(); }
    public String getClienteDomicilio() { return clienteDomicilio; }
    public String getCondicionVenta() { return condicionVenta; }
    public String getAmbienteArca() { return ambienteArca; }
    public Integer getTipoDocumentoReceptor() { return tipoDocumentoReceptor; }
    public Integer getCondicionIvaReceptorId() { return condicionIvaReceptorId; }
    public EstadoArca getEstadoArca() { return estadoArca; }
    public String getTipoAutorizacion() { return tipoAutorizacion; }
    public String getCodigoAutorizacion() { return codigoAutorizacion; }
    public LocalDate getVencimientoAutorizacion() { return vencimientoAutorizacion; }
    public Instant getFechaAutorizacion() { return fechaAutorizacion; }
    public String getMotivoAnulacion() { return motivoAnulacion; }
    public Long getId() { return id; }
    public Long getVersion() { return version; }
    public LocalDate getFecha() { return fecha; }
    public Cliente getCliente() { return cliente; }
    public CondicionIva getCondicionIva() { return condicionIva; }
    public PuntoVenta getPuntoVenta() { return puntoVenta; }
    public TipoMoneda getTipoMoneda() { return tipoMoneda; }
    public EstadoFactura getEstado() { return estado; }
    public Instant getFechaAnulacion() { return fechaAnulacion; }
    public String getObservaciones() { return observaciones; }
    public BigDecimal getNeto() { return neto; }
    public BigDecimal getIva() { return iva; }
    public BigDecimal getTotal() { return total; }
    public List<FacturaDetalle> getDetalles() { return Collections.unmodifiableList(detalles); }
}
