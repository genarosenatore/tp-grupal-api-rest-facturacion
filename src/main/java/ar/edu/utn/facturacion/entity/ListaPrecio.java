package ar.edu.utn.facturacion.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "lista_precio")
public class ListaPrecio {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 80)
    private String nombre;
    protected ListaPrecio() {}
    public ListaPrecio(String nombre) { this.nombre = nombre; }
    public Long getId() { return id; }
    public String getNombre() { return nombre; }
}
