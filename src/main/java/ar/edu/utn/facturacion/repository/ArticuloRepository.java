package ar.edu.utn.facturacion.repository;

import ar.edu.utn.facturacion.entity.Articulo;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ArticuloRepository extends JpaRepository<Articulo, Long> {
    boolean existsByCodigoIgnoreCase(String codigo);
}
