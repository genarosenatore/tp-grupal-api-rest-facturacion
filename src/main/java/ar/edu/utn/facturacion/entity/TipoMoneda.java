package ar.edu.utn.facturacion.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "tipo_moneda")
public class TipoMoneda {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 3)
    private String codigo;
    @Column(nullable = false, length = 60)
    private String descripcion;
    protected TipoMoneda() {}
    public Long getId() { return id; }
    public String getCodigo() { return codigo; }
    public String getDescripcion() { return descripcion; }
}
