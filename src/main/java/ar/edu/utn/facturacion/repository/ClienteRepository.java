package ar.edu.utn.facturacion.repository;

import ar.edu.utn.facturacion.entity.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    boolean existsByDocumentoAndIdNot(String documento, Long id);
}
