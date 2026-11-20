import type { FacturaEstadoRequestDTO, FacturaFiltrosDTO, FacturaReporteDTO, FacturaRequestDTO, PaginaDTO } from '../models/factura.dto';
import { http, isRecord } from './http';

// El tipado no valida la red en ejecución: comprobamos también la forma del JSON.
export function reporte(value: unknown): FacturaReporteDTO {
  const numeric = ['id', 'version', 'clienteId', 'condicionIvaId', 'puntoVentaId', 'tipoMonedaId', 'listaPrecioId', 'neto', 'iva', 'total', 'subtotal', 'valorDescuento', 'importeDescuento'];
  const detailNumeric = ['id', 'articuloId', 'listaPrecioArticuloId', 'cantidad', 'precioUnitario', 'alicuotaIva', 'neto', 'iva', 'total', 'descuento'];
  if (!isRecord(value) || !numeric.every(k => typeof value[k] === 'number' && Number.isFinite(value[k])) ||
      !['fecha', 'clienteNombre', 'moneda'].every(k => typeof value[k] === 'string') ||
      !['EMITIDA', 'ANULADA'].includes(String(value.estado)) ||
      !['A', 'B', 'C', 'X'].includes(String(value.letraComprobante)) ||
      !['SIN_DESCUENTO', 'PORCENTAJE', 'MONTO_FIJO'].includes(String(value.tipoDescuento)) ||
      !['PENDIENTE', 'EN_PROCESO', 'RECHAZADA', 'INCIERTA', 'AUTORIZADA'].includes(String(value.estadoArca)) ||
      !['informadaArca', 'editable', 'eliminable'].every(k => typeof value[k] === 'boolean') ||
      !(value.numero === null || (typeof value.numero === 'number' && Number.isSafeInteger(value.numero) && value.numero > 0)) ||
      !['tipoComprobante', 'tipoAutorizacion', 'codigoAutorizacion', 'vencimientoAutorizacion', 'fechaAutorizacion', 'motivoAnulacion',
        'ambienteArca', 'clienteDomicilio', 'condicionVenta'].every(k => value[k] === null || typeof value[k] === 'string') ||
      !['tipoDocumentoReceptor', 'condicionIvaReceptorId'].every(k => value[k] === null || Number.isSafeInteger(value[k])) ||
      !['clienteDocumento', 'puntoVentaCodigo', 'condicionIvaDescripcion'].every(k => typeof value[k] === 'string') ||
      !(value.fechaAnulacion === null || typeof value.fechaAnulacion === 'string') ||
      !(value.observaciones === null || typeof value.observaciones === 'string') ||
      !Array.isArray(value.detalles) || !value.detalles.every(d => isRecord(d) && typeof d.descripcion === 'string' && detailNumeric.every(k => typeof d[k] === 'number' && Number.isFinite(d[k])))) {
    throw new Error('La estructura de la factura recibida no coincide con la API.');
  }
  return value as unknown as FacturaReporteDTO;
}

export class FacturaService {
  /** Devuelve las coincidencias del filtro. Con page explícito limita el arreglo a esa página. */
  async obtenerFacturas(filtros: FacturaFiltrosDTO): Promise<FacturaReporteDTO[]> {
    const size = filtros.size ?? 100;
    const primera = await this.obtenerPaginaFacturas({ ...filtros, page: filtros.page ?? 0, size });
    const facturas = primera.content;
    if (filtros.page === undefined) {
      for (let page = 1; page < primera.totalPages; page++) {
        const siguiente = await this.obtenerPaginaFacturas({ ...filtros, page, size });
        if (siguiente.content.length === 0) break;
        facturas.push(...siguiente.content);
      }
    }
    return facturas;
  }
  async obtenerPaginaFacturas(filtros: FacturaFiltrosDTO): Promise<PaginaDTO<FacturaReporteDTO>> {
    const params = new URLSearchParams();
    for (const [key, value] of Object.entries(filtros)) if (value !== undefined && value !== '') params.set(key, String(value));
    const json = await http(`/facturas${params.size ? `?${params}` : ''}`);
    if (!isRecord(json) || !Array.isArray(json.content) ||
        !['page', 'size', 'totalElements', 'totalPages'].every(k => typeof json[k] === 'number' && Number.isSafeInteger(json[k]) && Number(json[k]) >= 0))
      throw new Error('La estructura de la página recibida no coincide con la API.');
    return { content: json.content.map(reporte), page: Number(json.page), size: Number(json.size), totalElements: Number(json.totalElements), totalPages: Number(json.totalPages) };
  }
  async obtenerFactura(id: number): Promise<FacturaReporteDTO> { return reporte(await http(`/facturas/${id}`)); }
  async crearFactura(factura: FacturaRequestDTO): Promise<FacturaReporteDTO> { return reporte(await http('/facturas', 'POST', factura)); }
  async actualizarFactura(id: number, factura: FacturaRequestDTO): Promise<FacturaReporteDTO> { return reporte(await http(`/facturas/${id}`, 'PUT', factura)); }
  async actualizarEstado(id: number, estado: string, observaciones?: string): Promise<FacturaReporteDTO> {
    if (estado !== 'EMITIDA' && estado !== 'ANULADA') throw new Error('Estado de factura inválido.');
    if (estado === 'ANULADA') {
      if (!observaciones?.trim()) throw new Error('Indicá el motivo de la anulación.');
      return this.anularFactura(id, observaciones.trim());
    }
    const body: FacturaEstadoRequestDTO = { estado, ...(observaciones === undefined ? {} : { observaciones }) };
    return reporte(await http(`/facturas/${id}`, 'PUT', body));
  }
  async anularFactura(id: number, motivo: string): Promise<FacturaReporteDTO> { return reporte(await http(`/facturas/${id}/anular`, 'POST', { motivo })); }
  async autorizarFactura(id: number): Promise<FacturaReporteDTO> { return reporte(await http(`/facturas/${id}/autorizar`, 'POST')); }
  async conciliarFactura(id: number): Promise<FacturaReporteDTO> { return reporte(await http(`/facturas/${id}/conciliar`, 'POST')); }
  /** DELETE físico de borradores; responde 204. */
  async eliminarFactura(id: number): Promise<void> { await http(`/facturas/${id}`, 'DELETE'); }
}
