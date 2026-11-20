import test from 'node:test';
import assert from 'node:assert/strict';
import { crearProducto, incorporarProducto, obtenerCatalogos } from '../src/services/CatalogoService';
import type { CatalogosDTO } from '../src/models/catalogos.dto';

const producto = { id: 4, codigo: 'P4', descripcion: 'Marcador', alicuotaIva: 21, tipoMonedaId: 1, precio: 350.5 };
test('alta de producto envía los datos y valida la respuesta del servidor', async t => {
  const { id: _, ...datos } = producto;
  const mock = t.mock.method(globalThis, 'fetch', async (_url: string, options: RequestInit) => {
    assert.equal(_url, '/api/articulos'); assert.equal(options.method, 'POST'); assert.deepEqual(JSON.parse(String(options.body)), datos);
    return Response.json(producto, { status: 201 });
  });
  assert.deepEqual(await crearProducto(datos), producto);
  mock.mock.mockImplementation(async () => Response.json({ codigo: 'P4' }, { status: 201 }));
  await assert.rejects(crearProducto(datos), /estructura inesperada/);
});
test('incorporar un producto conserva catálogos y precios de otras monedas sin duplicados', () => {
  const c: CatalogosDTO = { homologacionHabilitada: true, listasPrecio: [{ id: 1, nombre: 'Minorista' }], clientes: [{ id: 1, nombre: 'Ana', documento: '12345678', tipoDocumento: 96, condicionIvaReceptorId: 5, domicilio: null }], puntosVenta: [], condicionesIva: [], monedas: [],
    articulos: [{ id: 1, codigo: 'P1', descripcion: 'Cuaderno', alicuotaIva: 21 }],
    precios: [{ articuloId: 1, tipoMonedaId: 1, precio: 1000, listaPrecioId: 1 }, { articuloId: 4, tipoMonedaId: 2, precio: 1, listaPrecioId: 1 }] };
  const actualizado = incorporarProducto(incorporarProducto(c, producto), producto);
  assert.equal(actualizado.articulos.length, 2); assert.equal(actualizado.precios.length, 3);
  assert.equal(actualizado.clientes, c.clientes);
  assert.equal(actualizado.homologacionHabilitada, true);
  assert.equal(actualizado.precios.find(p => p.articuloId === 4 && p.tipoMonedaId === 2)?.precio, 1);
  assert.equal(c.articulos.length, 1);
});
test('el catálogo conserva el modo anunciado y usa el modo local con servidores anteriores', async t => {
  const catalogo = { listasPrecio: [], clientes: [], condicionesIva: [], puntosVenta: [], monedas: [], articulos: [], precios: [] };
  const mock = t.mock.method(globalThis, 'fetch', async () => Response.json(catalogo));
  assert.equal((await obtenerCatalogos()).homologacionHabilitada, false);
  mock.mock.mockImplementation(async () => Response.json({ ...catalogo, homologacionHabilitada: true }));
  assert.equal((await obtenerCatalogos()).homologacionHabilitada, true);
});
