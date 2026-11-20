package ar.edu.utn.facturacion.repository;

import ar.edu.utn.facturacion.entity.FacturaVenta;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
public interface FacturaRepository extends JpaRepository<FacturaVenta, Long>, JpaSpecificationExecutor<FacturaVenta> {
    // Serializa escrituras simultáneas sobre la misma factura.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FacturaVenta f where f.id = :id")
    Optional<FacturaVenta> buscarParaActualizar(@Param("id") Long id);
}
