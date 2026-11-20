export type EstadoFactura = 'EMITIDA' | 'ANULADA';
export type LetraComprobante = 'A' | 'B' | 'C' | 'X';
export type TipoDescuento = 'SIN_DESCUENTO' | 'PORCENTAJE' | 'MONTO_FIJO';

/** IDs y cantidad que Java recibe por renglón; el servidor resuelve el precio. */
export interface FacturaDetalleRequestDTO {
  articuloId: number;
  cantidad: number;
}

/** Cabecera de POST/PUT completo. LocalDate se transmite como YYYY-MM-DD. */
export interface FacturaRequestDTO {
  listaPrecioId?: number;
  tipoDescuento?: TipoDescuento;
  valorDescuento?: number;
  fecha: string;
  clienteId: number;
  condicionIvaId: number;
  puntoVentaId: number;
  tipoMonedaId: number;
  observaciones?: string | null;
  clienteDomicilio?: string | null;
  condicionVenta?: string | null;
  detalles: FacturaDetalleRequestDTO[];
  estado?: never; // Java permite estado únicamente en el otro modo de PUT.
  tipoComprobante?: '1' | '6' | '11';
  tipoDocumentoReceptor?: number;
  condicionIvaReceptorId?: number;
}

/** Modo alternativo del mismo FacturaRequestDTO Java: no mezclar con cabecera. */
export interface FacturaEstadoRequestDTO {
  estado: EstadoFactura;
  observaciones?: string;
}

/** Copia histórica del detalle devuelta por Java. BigDecimal se serializa a número JSON. */
export interface FacturaDetalleReporteDTO {
  id: number;
  articuloId: number;
  listaPrecioArticuloId: number;
  descripcion: string;
  cantidad: number;
  precioUnitario: number;
  alicuotaIva: number;
  neto: number;
  iva: number;
  total: number;
  descuento: number;
}

/** Reporte completo: importes definitivos y estado asignados por el backend. */
export interface FacturaReporteDTO {
  listaPrecioId: number;
  id: number;
  version: number;
  fecha: string;
  clienteId: number;
  clienteNombre: string;
  condicionIvaId: number;
  puntoVentaId: number;
  tipoMonedaId: number;
  moneda: string;
  estado: EstadoFactura;
  fechaAnulacion: string | null; // Instant ISO-8601 con zona UTC.
  observaciones: string | null;
  neto: number;
  iva: number;
  total: number;
  subtotal: number;
  tipoDescuento: TipoDescuento;
  valorDescuento: number;
  importeDescuento: number;
  detalles: FacturaDetalleReporteDTO[];
  numero: number | null;
  tipoComprobante: string | null;
  letraComprobante: LetraComprobante;
  estadoArca: 'PENDIENTE' | 'EN_PROCESO' | 'RECHAZADA' | 'INCIERTA' | 'AUTORIZADA';
  informadaArca: boolean;
  tipoAutorizacion: string | null;
  codigoAutorizacion: string | null;
  vencimientoAutorizacion: string | null;
  fechaAutorizacion: string | null;
  motivoAnulacion: string | null;
  editable: boolean;
  eliminable: boolean;
  clienteDocumento: string;
  puntoVentaCodigo: string;
  condicionIvaDescripcion: string;
  ambienteArca: string | null;
  tipoDocumentoReceptor: number | null;
  condicionIvaReceptorId: number | null;
  clienteDomicilio: string | null;
  condicionVenta: string | null;
}

/** Parámetros reales de GET; Java los recibe como @RequestParam, no como cuerpo JSON. */
export interface FacturaFiltrosDTO {
  listaPrecioId?: number;
  clienteId?: number;
  estado?: EstadoFactura;
  desde?: string;
  hasta?: string;
  numero?: number;
  informadaArca?: boolean;
  page?: number;
  size?: number;
}

export interface PaginaDTO<T> {
  content: T[]; page: number; size: number; totalElements: number; totalPages: number;
}
