package ar.edu.utn.facturacion.repository;
import ar.edu.utn.facturacion.entity.SerieFiscal;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;
public interface SerieFiscalRepository extends JpaRepository<SerieFiscal,String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from SerieFiscal s where s.id=:id")
    Optional<SerieFiscal> bloquear(String id);
}
