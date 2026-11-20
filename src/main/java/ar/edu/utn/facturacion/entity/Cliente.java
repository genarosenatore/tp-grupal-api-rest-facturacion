package ar.edu.utn.facturacion.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "cliente")
public class Cliente {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 120)
    private String nombre;
    @Column(nullable = false, unique = true, length = 30)
    private String documento;
    private Integer tipoDocumento;
    private Integer condicionIvaReceptorId;
    @Column(length = 250) private String domicilio;
    protected Cliente() {}
    public Cliente(String nombre, String documento, Integer tipoDocumento, Integer condicionIvaReceptorId, String domicilio) {
        actualizar(nombre, documento, tipoDocumento, condicionIvaReceptorId, domicilio);
    }
    public void actualizar(String nombre, String documento, Integer tipoDocumento, Integer condicionIvaReceptorId, String domicilio) {
        this.nombre = nombre; this.documento = documento; this.tipoDocumento = tipoDocumento;
        this.condicionIvaReceptorId = condicionIvaReceptorId; this.domicilio = domicilio;
    }
    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getDocumento() { return documento; }
    public Integer getTipoDocumento() { return tipoDocumento; }
    public Integer getCondicionIvaReceptorId() { return condicionIvaReceptorId; }
    public String getDomicilio() { return domicilio; }
}
