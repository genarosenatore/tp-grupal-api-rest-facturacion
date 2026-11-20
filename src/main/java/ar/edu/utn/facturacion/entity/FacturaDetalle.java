package ar.edu.utn.facturacion.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
/** Copias históricas de precio, descripción e IVA aplicados al emitir. */
@Entity
@Table(name = "factura_detalle")
public class FacturaDetalle {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "factura_id", nullable = false)
    private FacturaVenta factura;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lista_precio_articulo_id", nullable = false)
    private ListaPrecioArticulo listaPrecioArticulo;
    @Column(nullable = false, length = 150)
    private String descripcion;
    @Column(nullable = false, precision = 9, scale = 3)
    private BigDecimal cantidad;
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal precioUnitario;
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal alicuotaIva;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal neto;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal iva;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal total;
    @Column(nullable = false, precision = 19, scale = 2, columnDefinition = "numeric(19,2) default 0")
    private BigDecimal descuento = new BigDecimal("0.00");
    protected FacturaDetalle() {}
    public FacturaDetalle(ListaPrecioArticulo precio, BigDecimal cantidad,
                          BigDecimal neto, BigDecimal iva, BigDecimal total) {
        this.listaPrecioArticulo = precio;
        this.descripcion = precio.getArticulo().getDescripcion();
        this.cantidad = cantidad; this.precioUnitario = precio.getPrecio();
        this.alicuotaIva = precio.getArticulo().getAlicuotaIva();
        this.neto = neto; this.iva = iva; this.total = total;
    }
    void asociarA(FacturaVenta factura) { this.factura = factura; }
    public void aplicarAlicuota(BigDecimal alicuota) { alicuotaIva = alicuota; }
    public void aplicarDescuento(BigDecimal importe, BigDecimal neto, BigDecimal iva, BigDecimal total) {
        descuento = importe; this.neto = neto; this.iva = iva; this.total = total;
    }
    public BigDecimal getDescuento() { return descuento; }
    public Long getId() { return id; }
    public ListaPrecioArticulo getListaPrecioArticulo() { return listaPrecioArticulo; }
    public String getDescripcion() { return descripcion; }
    public BigDecimal getCantidad() { return cantidad; }
    public BigDecimal getPrecioUnitario() { return precioUnitario; }
    public BigDecimal getAlicuotaIva() { return alicuotaIva; }
    public BigDecimal getNeto() { return neto; }
    public BigDecimal getIva() { return iva; }
    public BigDecimal getTotal() { return total; }
}
