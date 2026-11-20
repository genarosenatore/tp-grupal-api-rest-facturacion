package ar.edu.utn.facturacion.repository;

import ar.edu.utn.facturacion.entity.ListaPrecioArticulo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.Optional;
public interface ListaPrecioArticuloRepository extends JpaRepository<ListaPrecioArticulo, Long> {
    @EntityGraph(attributePaths = {"articulo", "tipoMoneda"})
    java.util.List<ListaPrecioArticulo> findByListaPrecioId(Long listaPrecioId);
    @EntityGraph(attributePaths = {"articulo", "tipoMoneda"})
    Optional<ListaPrecioArticulo> findByArticuloIdAndTipoMonedaIdAndListaPrecioId(Long articuloId, Long tipoMonedaId, Long listaPrecioId);
    @EntityGraph(attributePaths = {"articulo", "tipoMoneda"})
    java.util.List<ListaPrecioArticulo> findByListaPrecioIdAndTipoMonedaIdAndArticuloIdIn(Long listaPrecioId, Long tipoMonedaId, java.util.List<Long> articuloIds);
}
