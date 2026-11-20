package ar.edu.utn.facturacion.entity;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;
@Entity @Table(name="reserva_fiscal")
public class ReservaFiscal {
    @Id private UUID id;
    @Column(length=100,nullable=false) private String serieId;
    private long numero;
    private int puntoVenta;
    private int tipo;
    private long facturaId;
    private boolean compensacion;
    private LocalDate fecha;
    @Column(length=20) private String estado;
    @Column(columnDefinition="text") private String respuesta;
    protected ReservaFiscal() {}
    public ReservaFiscal(UUID id,String serie,long numero,int punto,int tipo,long factura,boolean ajuste,LocalDate fecha) {
        this.id=id; serieId=serie; this.numero=numero; puntoVenta=punto; this.tipo=tipo;
        facturaId=factura; compensacion=ajuste; this.fecha=fecha; estado="PREPARADA";
    }
    public UUID getId() { return id; }
    public String getSerieId() { return serieId; }
    public long getNumero() { return numero; }
    public int getPuntoVenta() { return puntoVenta; }
    public int getTipo() { return tipo; }
    public long getFacturaId() { return facturaId; }
    public boolean isCompensacion() { return compensacion; }
    public LocalDate getFecha() { return fecha; }
    public String getEstado() { return estado; }
    public String getRespuesta() { return respuesta; }
    public void finalizar(String estado,String respuesta) { this.estado=estado; this.respuesta=respuesta; }
}
