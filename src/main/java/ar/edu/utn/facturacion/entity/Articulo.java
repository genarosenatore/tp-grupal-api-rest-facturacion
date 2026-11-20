package ar.edu.utn.facturacion.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "articulo")
public class Articulo {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 30)
    private String codigo;
    @Column(nullable = false, length = 150)
    private String descripcion;
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal alicuotaIva;
    protected Articulo() {}
    public Articulo(String codigo, String descripcion, BigDecimal alicuotaIva) {
        this.codigo = codigo; this.descripcion = descripcion; this.alicuotaIva = alicuotaIva;
    }
    public Long getId() { return id; }
    public String getCodigo() { return codigo; }
    public String getDescripcion() { return descripcion; }
    public BigDecimal getAlicuotaIva() { return alicuotaIva; }
}
