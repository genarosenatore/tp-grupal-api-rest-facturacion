package ar.edu.utn.facturacion.repository;

import ar.edu.utn.facturacion.entity.OperacionFiscal;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface OperacionFiscalRepository extends JpaRepository<OperacionFiscal, UUID> {
    Optional<OperacionFiscal> findFirstByFacturaIdOrderByCreadaDesc(Long facturaId);
    Optional<OperacionFiscal> findFirstByFacturaIdAndOperacionAndEstadoOrderByCreadaDesc(Long facturaId, String operacion, String estado);
    @Modifying
    @Query("update OperacionFiscal o set o.factura = null where o.factura.id = :facturaId")
    void desvincularFactura(Long facturaId);
}
