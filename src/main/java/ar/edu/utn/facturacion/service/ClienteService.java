package ar.edu.utn.facturacion.service;

import ar.edu.utn.facturacion.dto.*;
import ar.edu.utn.facturacion.entity.Cliente;
import ar.edu.utn.facturacion.exception.*;
import ar.edu.utn.facturacion.repository.ClienteRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.text.Normalizer;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class ClienteService {
    private final ClienteRepository clientes;
    public ClienteService(ClienteRepository clientes) { this.clientes = clientes; }
    public static CatalogosDTO.Cliente reporte(Cliente c) {
        return new CatalogosDTO.Cliente(c.getId(), c.getNombre(), c.getDocumento(), c.getTipoDocumento(), c.getCondicionIvaReceptorId(), c.getDomicilio());
    }
    public CatalogosDTO.Cliente obtener(Long id) { return reporte(buscar(id)); }
    public List<CatalogosDTO.Cliente> listar(String q, String documento) {
        return clientes.findAll(Sort.by("nombre")).stream()
            .filter(c -> normal(c.getNombre() + " " + c.getDocumento()).contains(normal(q)) && normal(c.getDocumento()).contains(normal(documento)))
            .limit(100).map(ClienteService::reporte).toList();
    }
    @Transactional
    public CatalogosDTO.Cliente guardar(Long id, ClienteRequestDTO r) {
        String documento = r.documento().strip().replace("-", "").replace(" ", "");
        if (!Set.of(80,96,99).contains(r.tipoDocumento())) throw new SolicitudInvalidaException("Seleccioná DNI, CUIT o sin identificar.");
        if (!Set.of(1,4,5,6).contains(r.condicionIvaReceptorId())) throw new SolicitudInvalidaException("Seleccioná una condición fiscal válida.");
        if ((r.tipoDocumento() == 80 && !documento.matches("[0-9]{11}")) || (r.tipoDocumento() == 96 && !documento.matches("[0-9]{7,8}"))
                || (r.tipoDocumento() == 99 && !documento.equals("0"))) throw new SolicitudInvalidaException("El documento no coincide con el tipo: CUIT de 11 dígitos, DNI de 7 u 8, o 0 sin identificar.");
        if (Set.of(1,4,6).contains(r.condicionIvaReceptorId()) && r.tipoDocumento() != 80)
            throw new SolicitudInvalidaException("La condición fiscal seleccionada requiere CUIT.");
        if (clientes.existsByDocumentoAndIdNot(documento, id == null ? -1L : id)) throw new ConflictoNegocioException("Ya existe un cliente con ese documento.");
        String domicilio = r.domicilio() == null || r.domicilio().isBlank() ? null : r.domicilio().strip();
        Cliente cliente = id == null ? new Cliente(r.nombre().strip(), documento, r.tipoDocumento(), r.condicionIvaReceptorId(), domicilio) : buscar(id);
        cliente.actualizar(r.nombre().strip(), documento, r.tipoDocumento(), r.condicionIvaReceptorId(), domicilio);
        return reporte(clientes.saveAndFlush(cliente));
    }
    private Cliente buscar(Long id) { return clientes.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Cliente no encontrado: " + id)); }
    private static String normal(String s) { return Normalizer.normalize(Objects.toString(s,""), Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).strip(); }
}
