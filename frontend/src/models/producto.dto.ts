export interface ProductoRequestDTO {
  codigo: string;
  descripcion: string;
  alicuotaIva: number;
  tipoMonedaId: number;
  precio: number;
  listaPrecioId?: number;
}

export interface ProductoReporteDTO extends ProductoRequestDTO { id: number }
