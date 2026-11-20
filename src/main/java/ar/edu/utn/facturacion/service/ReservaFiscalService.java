package ar.edu.utn.facturacion.service;
import ar.edu.utn.facturacion.entity.*;
import ar.edu.utn.facturacion.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.*;
import java.time.LocalDate;
import java.util.function.LongSupplier;

/** Una operación activa por serie, durable incluso si se reinicia la aplicación. */
@Service
public class ReservaFiscalService {
    private final SerieFiscalRepository series;
    private final ReservaFiscalRepository reservas;
    private final TransactionTemplate tx;
    public ReservaFiscalService(SerieFiscalRepository series,ReservaFiscalRepository reservas,PlatformTransactionManager manager) {
        this.series=series; this.reservas=reservas; tx=new TransactionTemplate(manager);
    }
    public static class SerieOcupada extends RuntimeException {}
    public record Preparacion(ReservaFiscal reserva, boolean nueva) {}
    public Preparacion preparar(UUID id,long cuit,int punto,int tipo,long factura,boolean ajuste,LocalDate fecha,LongSupplier ultimo) {
        String clave="HOMOLOGACION/"+cuit+"/"+punto+"/"+tipo;
        if (!series.existsById(clave)) {
            try { tx.executeWithoutResult(s -> series.saveAndFlush(new SerieFiscal(clave))); }
            catch (DataIntegrityViolationException carrera) { /* Otra instancia creó la misma serie. */ }
        }
        return tx.execute(s -> {
            var serie=series.bloquear(clave).orElseThrow();
            var previa=reservas.findById(id);
            if (previa.isPresent()) return new Preparacion(previa.get(),false);
            if (serie.getOperacionActiva()!=null) throw new SerieOcupada();
            long numero=Math.addExact(ultimo.getAsLong(),1);
            var reserva=reservas.saveAndFlush(new ReservaFiscal(id,clave,numero,punto,tipo,factura,ajuste,fecha));
            serie.asignar(id); return new Preparacion(reserva,true);
        });
    }
    public Optional<ReservaFiscal> buscar(UUID id) { return reservas.findById(id); }
    public void finalizar(UUID id,String estado,String respuesta) {
        tx.executeWithoutResult(s -> {
            var r=reservas.findById(id).orElseThrow(); var serie=series.bloquear(r.getSerieId()).orElseThrow();
            if ("CONFIRMADA".equals(r.getEstado())) return;
            r.finalizar(estado,respuesta);
            if (id.equals(serie.getOperacionActiva())) serie.asignar(null);
        });
    }
}
