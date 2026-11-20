package ar.edu.utn.facturacion.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
/** Un precio vigente por lista, artículo y moneda. */
@Entity
@Table(name = "lista_precio_articulo", uniqueConstraints = @UniqueConstraint(columnNames = {"articulo_id", "tipo_moneda_id", "lista_precio_id"}))
public class ListaPrecioArticulo {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "articulo_id", nullable = false)
    private Articulo articulo;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tipo_moneda_id", nullable = false)
    private TipoMoneda tipoMoneda;
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal precio;
    @Column(nullable = false, columnDefinition = "bigint default 1")
    private Long listaPrecioId = 1L;
    protected ListaPrecioArticulo() {}
    public ListaPrecioArticulo(Articulo articulo, TipoMoneda tipoMoneda, BigDecimal precio) {
        this.articulo = articulo; this.tipoMoneda = tipoMoneda; this.precio = precio;
    }
    public Long getId() { return id; }
    public ListaPrecioArticulo(Articulo articulo, TipoMoneda moneda, BigDecimal precio, Long listaPrecioId) {
        this(articulo, moneda, precio); this.listaPrecioId = listaPrecioId;
    }
    public Long getListaPrecioId() { return listaPrecioId; }
    public void cambiarPrecio(BigDecimal precio) { this.precio = precio; }
    public Articulo getArticulo() { return articulo; }
    public TipoMoneda getTipoMoneda() { return tipoMoneda; }
    public BigDecimal getPrecio() { return precio; }
}
