import type { FacturaRequestDTO } from '../models/factura.dto';
import { esFiscal, condicionesReceptor } from './fiscal';
export function fechaValida(value: string): boolean {
  return /^\d{4}-\d{2}-\d{2}$/.test(value) && Number.isFinite(Date.parse(value)) && new Date(value).toISOString().slice(0, 10) === value;
}
export function validarFactura(f: FacturaRequestDTO): void {
  if (f.tipoComprobante !== undefined) {
    if (!esFiscal(f.tipoComprobante) || (f.condicionIvaReceptorId !== undefined && !condicionesReceptor(f.tipoComprobante).some(c => c.id === f.condicionIvaReceptorId)))
      throw new Error('Revisá el tipo de comprobante y la condición fiscal del receptor.');
    if (f.tipoDocumentoReceptor !== undefined && (![80, 96, 99].includes(f.tipoDocumentoReceptor) || ((f.tipoComprobante === '1' || [1,4,6].includes(f.condicionIvaReceptorId ?? 0)) && f.tipoDocumentoReceptor !== 80)))
      throw new Error('Factura A y clientes inscriptos, monotributistas o exentos requieren CUIT (80).');
  }
  const tipo = f.tipoDescuento ?? 'SIN_DESCUENTO', valor = f.valorDescuento ?? 0;
  if (!['SIN_DESCUENTO','PORCENTAJE','MONTO_FIJO'].includes(tipo) || !Number.isFinite(valor) || valor < 0 || !/^\d+(\.\d{1,2})?$/.test(String(valor))
      || (tipo === 'PORCENTAJE' && valor > 100) || (tipo === 'SIN_DESCUENTO' && valor !== 0)) throw new Error('Revisá el descuento general.');
  if (!fechaValida(f.fecha)) throw new Error('Ingresá una fecha válida.');
  if (f.listaPrecioId !== undefined && (!Number.isSafeInteger(f.listaPrecioId) || f.listaPrecioId <= 0)) throw new Error('Seleccioná una lista de precios válida.');
  if (![f.clienteId, f.condicionIvaId, f.puntoVentaId, f.tipoMonedaId].every(id => Number.isSafeInteger(id) && id > 0)) throw new Error('Seleccioná todos los datos de cabecera.');
  if (f.detalles.length < 1 || f.detalles.length > 100) throw new Error('La factura debe contener entre 1 y 100 detalles.');
  if ((f.observaciones?.length ?? 0) > 1000) throw new Error('Las observaciones admiten hasta 1000 caracteres.');
  if ((f.clienteDomicilio?.length ?? 0) > 250) throw new Error('El domicilio admite hasta 250 caracteres.');
  if ((f.condicionVenta?.length ?? 0) > 80) throw new Error('La condición de venta admite hasta 80 caracteres.');
  for (const d of f.detalles) {
    if (!Number.isSafeInteger(d.articuloId) || d.articuloId <= 0 || !Number.isFinite(d.cantidad) || d.cantidad < 0.001 || d.cantidad > 999999.999 || !/^\d+(\.\d{1,3})?$/.test(String(d.cantidad))) throw new Error('Revisá los artículos y cantidades: de 0,001 a 999.999,999, hasta tres decimales.');
  }
}
