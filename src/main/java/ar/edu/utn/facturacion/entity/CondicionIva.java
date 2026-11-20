package ar.edu.utn.facturacion.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "condicion_iva")
public class CondicionIva {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 80)
    private String descripcion;
    protected CondicionIva() {}
    public Long getId() { return id; }
    public String getDescripcion() { return descripcion; }
}
