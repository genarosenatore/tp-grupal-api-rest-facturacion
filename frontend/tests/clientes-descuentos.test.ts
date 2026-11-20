import test from 'node:test';
import assert from 'node:assert/strict';
import { descontar, importe } from '../src/utils/importes';
import { obtenerCliente, buscarClientes, guardarCliente, obtenerPrecio, buscarPrecios } from '../src/services/CatalogoService';

test('descuento general por porcentaje o monto fijo distribuye bases e IVA con el mismo resultado', () => {
  const bases = [{ neto: 200000n, alicuota: 21 }, { neto: 60000n, alicuota: 10.5 }];
  const p = descontar(bases,'PORCENTAJE','10'), fijo = descontar(bases,'MONTO_FIJO','260');
  assert.deepEqual(p,fijo); assert.equal(importe(p.subtotal),'2600.00'); assert.equal(importe(p.descuento),'260.00');
  assert.equal(importe(p.neto),'2340.00'); assert.equal(importe(p.iva),'434.70'); assert.equal(importe(p.total),'2774.70');
  assert.deepEqual(p.detalles.map(d => d.descuento),[20000n,6000n]);
});
test('restos mayores, cero, cien por ciento y límites del descuento', () => {
  const bases = [{ neto: 1n, alicuota: 21 },{ neto: 1n, alicuota: 10.5 },{ neto: 1n, alicuota: 0 },{ neto: 0n, alicuota: 21 }];
  assert.deepEqual(descontar(bases,'MONTO_FIJO','0.02').detalles.map(d => d.descuento),[1n,1n,0n,0n]);
  assert.equal(descontar(bases,'PORCENTAJE','100').total,0n);
  assert.equal(descontar([{ neto: 0n, alicuota: 0 }],'PORCENTAJE','100').descuento,0n);
  for (const [tipo,valor] of [['PORCENTAJE','100.01'],['MONTO_FIJO','0.04'],['MONTO_FIJO','-1'],['MONTO_FIJO','0.001'],['SIN_DESCUENTO','1']]) assert.throws(() => descontar(bases,tipo,valor));
});
test('consulta clientes y precios vigentes por ID y combina filtros en el servidor', async t => {
  const cliente = { id: 1, nombre: 'Ana', documento: '12345678', tipoDocumento: 96, condicionIvaReceptorId: 5, domicilio: 'Calle 123' };
  const precio = { id: 1, codigo: 'A1', descripcion: 'Cuaderno', alicuotaIva: 21, precio: 1000, tipoMonedaId: 1, listaPrecioId: 1 };
  const urls: string[] = [];
  t.mock.method(globalThis,'fetch',async (url: string) => {
    urls.push(url);
    return Response.json(url.includes('/clientes') ? url.includes('?') ? [cliente] : cliente : url.includes('/precios/1?') ? precio : [precio]);
  });
  assert.deepEqual(await obtenerCliente(1),cliente); assert.deepEqual(await buscarClientes('ana','123'),[cliente]);
  assert.deepEqual(await obtenerPrecio(1,1,1),precio); assert.deepEqual(await buscarPrecios('cuaderno','1','1','21'),[precio]);
  assert.deepEqual(urls,['/api/clientes/1','/api/clientes?q=ana&documento=123','/api/catalogos/precios/1?tipoMonedaId=1&listaPrecioId=1','/api/catalogos/precios?q=cuaderno&listaPrecioId=1&tipoMonedaId=1&iva=21']);
});
test('edita los datos en la ficha del cliente y valida su respuesta', async t => {
  const cliente = { id: 1, nombre: 'Ana', documento: '12345678', tipoDocumento: 96, condicionIvaReceptorId: 5, domicilio: null };
  const { id: _, ...datos } = cliente;
  const mock = t.mock.method(globalThis,'fetch',async (url: string,options: RequestInit) => {
    assert.equal(url,'/api/clientes/1'); assert.equal(options.method,'PUT'); assert.deepEqual(JSON.parse(String(options.body)),datos);
    return Response.json(cliente);
  });
  assert.deepEqual(await guardarCliente(1,datos),cliente);
  mock.mock.mockImplementation(async () => Response.json({ ...cliente,tipoDocumento: 'DNI' }));
  await assert.rejects(obtenerCliente(1),/estructura inesperada/);
});
