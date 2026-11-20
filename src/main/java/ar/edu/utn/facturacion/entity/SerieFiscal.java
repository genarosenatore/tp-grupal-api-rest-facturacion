package ar.edu.utn.facturacion.entity;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name="serie_fiscal")
public class SerieFiscal {
    @Id @Column(length=100) private String id;
    private UUID operacionActiva;
    protected SerieFiscal() {}
    public SerieFiscal(String id) { this.id=id; }
    public UUID getOperacionActiva() { return operacionActiva; }
    public void asignar(UUID id) { operacionActiva=id; }
}
