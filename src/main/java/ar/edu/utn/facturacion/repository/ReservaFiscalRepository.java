package ar.edu.utn.facturacion.repository;
import ar.edu.utn.facturacion.entity.ReservaFiscal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface ReservaFiscalRepository extends JpaRepository<ReservaFiscal,UUID> {}
