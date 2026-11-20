import test from 'node:test';
import assert from 'node:assert/strict';
import { filtrarClientes, filtrarPrecios } from '../src/explorers';
import type { CatalogosDTO } from '../src/models/catalogos.dto';

const c: CatalogosDTO = {
  homologacionHabilitada: false, listasPrecio: [{ id: 1, nombre: 'Minorista' }, { id: 2, nombre: 'Mayorista' }],
  clientes: [{ id: 1, nombre: 'Ana Martínez', documento: '12345678', tipoDocumento: 96, condicionIvaReceptorId: 5, domicilio: null }, { id: 2, nombre: 'Bruno', documento: '20123456789', tipoDocumento: 80, condicionIvaReceptorId: 1, domicilio: null }],
  condicionesIva: [], puntosVenta: [],
  monedas: [{ id: 1, codigo: 'ARS', descripcion: 'Pesos' }, { id: 2, codigo: 'USD', descripcion: 'Dólares' }],
  articulos: [{ id: 1, codigo: 'A1', descripcion: 'Lápiz', alicuotaIva: 21 }, { id: 2, codigo: 'A2', descripcion: 'Carpeta', alicuotaIva: 0 }],
  precios: [{ articuloId: 1, tipoMonedaId: 1, precio: 100, listaPrecioId: 1 }, { articuloId: 1, tipoMonedaId: 2, precio: 1, listaPrecioId: 1 }, { articuloId: 2, tipoMonedaId: 1, precio: 50, listaPrecioId: 1 }]
};
test('clientes combina texto y documento, sin afectar el catálogo', () => {
  assert.deepEqual(filtrarClientes(c, 'martinez', '5678').map(r => r.id), [1]);
  assert.equal(filtrarClientes(c, 'Bruno', '999').length, 0);
  assert.equal(c.clientes.length, 2);
});
test('precios combina código/descripción, moneda e IVA (incluido cero)', () => {
  const precios = filtrarPrecios(c, 'lapiz', '2', '21');
  assert.equal(precios.length, 1); assert.equal(precios[0].precio, 1);
  assert.equal(precios[0].articuloId, 1); assert.equal(precios[0].tipoMonedaId, 2);
  assert.equal(filtrarPrecios(c, 'A2', '1', '0').length, 1);
  assert.equal(filtrarPrecios(c, '', '2', '0').length, 0);
});

test('la lista de precios se combina con los otros filtros sin mezclar productos', () => {
  const catalogo: CatalogosDTO = { ...c, precios: [...c.precios, { articuloId: 1, tipoMonedaId: 1, precio: 80, listaPrecioId: 2 }] };
  assert.deepEqual(filtrarPrecios(catalogo, 'lapiz', '1', '21', '2').map(p => [p.lista, p.precio]), [['Mayorista', 80]]);
  assert.equal(filtrarPrecios(catalogo, 'A2', '', '', '2').length, 0);
  assert.equal(filtrarPrecios(catalogo, '', '', '', '').length, 4);
});
