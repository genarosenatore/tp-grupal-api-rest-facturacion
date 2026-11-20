import type { CatalogosDTO, ClienteDTO, ClienteRequestDTO } from '../models/catalogos.dto';
import type { ProductoReporteDTO, ProductoRequestDTO } from '../models/producto.dto';
import { http, isRecord } from './http';

export async function obtenerCatalogos(incluirArticulos = true): Promise<CatalogosDTO> {
  const json = await http(incluirArticulos ? '/catalogos' : '/catalogos?incluirArticulos=false');
  const fields: Record<string, Record<string, string>> = {
    listasPrecio: { id: 'number', nombre: 'string' },
    clientes: { id: 'number', nombre: 'string', documento: 'string' },
    condicionesIva: { id: 'number', descripcion: 'string' },
    puntosVenta: { id: 'number', codigo: 'string', descripcion: 'string' },
    monedas: { id: 'number', codigo: 'string', descripcion: 'string' },
    articulos: { id: 'number', codigo: 'string', descripcion: 'string', alicuotaIva: 'number' },
    precios: { articuloId: 'number', tipoMonedaId: 'number', precio: 'number', listaPrecioId: 'number' }
  };
  if (!isRecord(json) || (json.homologacionHabilitada !== undefined && typeof json.homologacionHabilitada !== 'boolean') ||
      !Object.entries(fields).every(([key, shape]) => {
    const rows = json[key];
    return Array.isArray(rows) && rows.every(row => isRecord(row) && Object.entries(shape).every(([k, type]) => typeof row[k] === type));
  })) throw new Error('La API devolvió catálogos con una estructura inesperada.');
  // Un servidor anterior no anuncia el adaptador. En ese caso se inicia en modo local.
  return { ...json, clientes: (json.clientes as unknown[]).map(clienteReporte), homologacionHabilitada: json.homologacionHabilitada === true } as unknown as CatalogosDTO;
}

export function clienteReporte(json: unknown): ClienteDTO {
  if (!isRecord(json) || !Number.isSafeInteger(json.id) || !['nombre','documento'].every(k => typeof json[k] === 'string')
      || !['tipoDocumento','condicionIvaReceptorId'].every(k => json[k] === null || Number.isSafeInteger(json[k]))
      || !(json.domicilio === null || typeof json.domicilio === 'string')) throw new Error('La API devolvió un cliente con una estructura inesperada.');
  return json as unknown as ClienteDTO;
}
export async function obtenerCliente(id: number): Promise<ClienteDTO> { return clienteReporte(await http(`/clientes/${id}`)); }
export async function buscarClientes(q: string, documento: string): Promise<ClienteDTO[]> {
  const json = await http(`/clientes?${new URLSearchParams({ q, documento })}`);
  if (!Array.isArray(json)) throw new Error('La búsqueda de clientes devolvió una estructura inesperada.');
  return json.map(clienteReporte);
}
export async function guardarCliente(id: number | null, datos: ClienteRequestDTO): Promise<ClienteDTO> {
  return clienteReporte(await http(`/clientes${id === null ? '' : `/${id}`}`, id === null ? 'POST' : 'PUT', datos));
}
function productoReporte(json: unknown): ProductoReporteDTO {
  if (!isRecord(json) || !['id','tipoMonedaId','listaPrecioId','alicuotaIva','precio'].every(k => typeof json[k] === 'number' && Number.isFinite(json[k]))
      || !['codigo','descripcion'].every(k => typeof json[k] === 'string')) throw new Error('La API devolvió un precio con una estructura inesperada.');
  return json as unknown as ProductoReporteDTO;
}
export async function obtenerPrecio(articuloId: number, tipoMonedaId: number, listaPrecioId: number): Promise<ProductoReporteDTO> {
  return productoReporte(await http(`/catalogos/precios/${articuloId}?${new URLSearchParams({ tipoMonedaId: String(tipoMonedaId), listaPrecioId: String(listaPrecioId) })}`));
}
export async function buscarPrecios(q: string, lista: string, moneda: string, iva: string): Promise<ProductoReporteDTO[]> {
  const p = new URLSearchParams({ q });
  if (lista) p.set('listaPrecioId', lista); if (moneda) p.set('tipoMonedaId', moneda); if (iva) p.set('iva', iva);
  const json = await http(`/catalogos/precios?${p}`);
  if (!Array.isArray(json)) throw new Error('La búsqueda de precios devolvió una estructura inesperada.');
  return json.map(productoReporte);
}
export async function consultarPrecios(articuloIds: number[], listaPrecioId: number, tipoMonedaId: number): Promise<ProductoReporteDTO[]> {
  const params = new URLSearchParams({ listaPrecioId: String(listaPrecioId), tipoMonedaId: String(tipoMonedaId), articuloIds: [...new Set(articuloIds)].join(',') });
  const json = await http(`/catalogos/precios?${params}`);
  if (!Array.isArray(json)) throw new Error('No se pudieron consultar los precios de la nueva lista.');
  return json.map(productoReporte);
}

export async function crearProducto(datos: ProductoRequestDTO): Promise<ProductoReporteDTO> {
  const json = await http('/articulos', 'POST', datos);
  if (!isRecord(json) || !['id', 'alicuotaIva', 'tipoMonedaId', 'precio'].every(k => typeof json[k] === 'number' && Number.isFinite(json[k]))
      || !['codigo', 'descripcion'].every(k => typeof json[k] === 'string')) throw new Error('La API devolvió un producto con una estructura inesperada.');
  return json as unknown as ProductoReporteDTO;
}

/** Actualiza únicamente el artículo y su precio; conserva el resto del catálogo. */
export function incorporarProducto(c: CatalogosDTO, p: ProductoReporteDTO): CatalogosDTO {
  return {
    ...c,
    articulos: [...c.articulos.filter(a => a.id !== p.id), { id: p.id, codigo: p.codigo, descripcion: p.descripcion, alicuotaIva: p.alicuotaIva }]
      .sort((a, b) => a.descripcion.localeCompare(b.descripcion, 'es')),
    precios: [...c.precios.filter(a => a.articuloId !== p.id || a.tipoMonedaId !== p.tipoMonedaId || a.listaPrecioId !== (p.listaPrecioId ?? 1)),
      { articuloId: p.id, tipoMonedaId: p.tipoMonedaId, precio: p.precio, listaPrecioId: p.listaPrecioId ?? 1 }]
  };
}

export async function crearListaPrecio(nombre: string): Promise<{ id: number; nombre: string }> {
  const json = await http('/listas-precio', 'POST', { nombre });
  if (!isRecord(json) || typeof json.id !== 'number' || typeof json.nombre !== 'string') throw new Error('Respuesta de lista inesperada.');
  return json as { id: number; nombre: string };
}

export async function guardarPrecio(listaPrecioId: number, datos: { articuloId: number; tipoMonedaId: number; precio: number }): Promise<CatalogosDTO['precios'][number]> {
  const json = await http(`/listas-precio/${listaPrecioId}/precios`, 'PUT', datos);
  if (!isRecord(json) || !['articuloId', 'tipoMonedaId', 'precio', 'listaPrecioId'].every(k => typeof json[k] === 'number' && Number.isFinite(json[k]))) throw new Error('Respuesta de precio inesperada.');
  return json as unknown as CatalogosDTO['precios'][number];
}
