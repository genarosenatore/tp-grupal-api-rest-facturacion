package ar.edu.utn.facturacion.repository;

import ar.edu.utn.facturacion.entity.ListaPrecio;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ListaPrecioRepository extends JpaRepository<ListaPrecio, Long> {
    boolean existsByNombreIgnoreCase(String nombre);
}
