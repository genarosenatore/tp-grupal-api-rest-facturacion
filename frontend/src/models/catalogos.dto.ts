/** Espejo de los records anidados de CatalogosDTO.java. */
export interface CatalogosDTO {
  homologacionHabilitada: boolean;
  listasPrecio: { id: number; nombre: string }[];
  clientes: ClienteDTO[];
  condicionesIva: { id: number; descripcion: string }[];
  puntosVenta: { id: number; codigo: string; descripcion: string }[];
  monedas: { id: number; codigo: string; descripcion: string }[];
  articulos: { id: number; codigo: string; descripcion: string; alicuotaIva: number }[];
  precios: { articuloId: number; tipoMonedaId: number; precio: number; listaPrecioId: number }[];
}

export interface ClienteDTO {
  id: number; nombre: string; documento: string;
  tipoDocumento: number | null; condicionIvaReceptorId: number | null; domicilio: string | null;
}
export type ClienteRequestDTO = Omit<ClienteDTO, 'id'>;
