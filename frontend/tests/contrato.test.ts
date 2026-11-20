import test from 'node:test';
import assert from 'node:assert/strict';
import { FacturaService } from '../src/services/FacturaService';
import { ApiError } from '../src/services/http';
import { calcular, importe } from '../src/utils/importes';
import { validarFactura, fechaValida } from '../src/utils/validators';
import type { FacturaReporteDTO, FacturaRequestDTO } from '../src/models/factura.dto';

const reporte: FacturaReporteDTO = { listaPrecioId: 1, id: 1, version: 0, fecha: '2026-10-23', clienteId: 1, clienteNombre: 'Demo', condicionIvaId: 1, puntoVentaId: 1, tipoMonedaId: 1, moneda: 'ARS', estado: 'EMITIDA', fechaAnulacion: null, observaciones: null, neto: 1000, iva: 210, total: 1210, detalles: [{ id: 1, articuloId: 1, listaPrecioArticuloId: 1, descripcion: 'Cuaderno', cantidad: 1, precioUnitario: 1000, alicuotaIva: 21, neto: 1000, iva: 210, total: 1210, descuento: 0 }], numero: null, tipoComprobante: null, estadoArca: 'PENDIENTE', informadaArca: false, tipoAutorizacion: null, codigoAutorizacion: null, vencimientoAutorizacion: null, fechaAutorizacion: null, motivoAnulacion: null, editable: true, eliminable: true, clienteDocumento: '12345678', puntoVentaCodigo: '0001', condicionIvaDescripcion: 'Consumidor final',
  ambienteArca: null, tipoDocumentoReceptor: null, condicionIvaReceptorId: null, clienteDomicilio: null, condicionVenta: null, letraComprobante: 'X', subtotal: 1000, tipoDescuento: 'SIN_DESCUENTO', valorDescuento: 0, importeDescuento: 0 };
const pagina = { content: [reporte], page: 0, size: 8, totalElements: 1, totalPages: 1 };
const payload: FacturaRequestDTO = { fecha: '2026-10-23', clienteId: 1, condicionIvaId: 1, puntoVentaId: 1, tipoMonedaId: 1, detalles: [{ articuloId: 1, cantidad: 1 }] };
const service = new FacturaService();

test('GET sólo transmite parámetros soportados y decodifica el reporte', async t => {
  t.mock.method(globalThis, 'fetch', async (url: string) => {
    assert.equal(url, '/api/facturas?clienteId=1&estado=EMITIDA&desde=2026-10-01&hasta=2026-10-23&numero=7&informadaArca=false&page=2&size=8');
    return Response.json(pagina);
  });
  assert.deepEqual(await service.obtenerPaginaFacturas({ clienteId: 1, estado: 'EMITIDA', desde: '2026-10-01', hasta: '2026-10-23', numero: 7, informadaArca: false, page: 2, size: 8 }), pagina);
});
test('el listado académico retorna todas las coincidencias y respeta una página explícita', async t => {
  const consultas: number[] = [];
  t.mock.method(globalThis, 'fetch', async (url: string) => {
    const params = new URL(url, 'http://localhost').searchParams;
    assert.equal(params.get('estado'), 'EMITIDA');
    const page = Number(params.get('page')); consultas.push(page);
    return Response.json({ ...pagina, page, size: 1, totalElements: 2, totalPages: 2, content: [{ ...reporte, id: 2 - page }] });
  });
  const filtros = { estado: 'EMITIDA' as const, size: 1 };
  assert.deepEqual((await service.obtenerFacturas(filtros)).map(f => f.id), [2, 1]);
  assert.deepEqual((await service.obtenerFacturas({ ...filtros, page: 1 })).map(f => f.id), [1]);
  assert.deepEqual(consultas, [0, 1, 1]);
});
test('campos comerciales y fiscales del reporte distinguen null de ausente o mal tipado', async t => {
  const mock = t.mock.method(globalThis, 'fetch', async () => Response.json({ ...reporte, condicionVenta: undefined }));
  await assert.rejects(service.obtenerFactura(1), /estructura/);
  mock.mock.mockImplementation(async () => Response.json({ ...reporte, tipoDocumentoReceptor: '96' }));
  await assert.rejects(service.obtenerFactura(1), /estructura/);
});

test('el reporte exige la letra persistida y rechaza letras ausentes o inválidas', async t => {
  const mock = t.mock.method(globalThis, 'fetch', async () => Response.json({ ...reporte, letraComprobante: undefined }));
  await assert.rejects(service.obtenerFactura(1), /estructura/);
  for (const letraComprobante of [null, 'Z', 1, '']) {
    mock.mock.mockImplementation(async () => Response.json({ ...reporte, letraComprobante }));
    await assert.rejects(service.obtenerFactura(1), /estructura/);
  }
  for (const letraComprobante of ['A', 'B', 'C', 'X']) {
    mock.mock.mockImplementation(async () => Response.json({ ...reporte, letraComprobante }));
    assert.equal((await service.obtenerFactura(1)).letraComprobante, letraComprobante);
  }
});
test('actualizarEstado anula por el circuito con motivo y solicita el motivo si falta', async t => {
  await assert.rejects(service.actualizarEstado(1, 'ANULADA', '   '), /motivo/);
  t.mock.method(globalThis, 'fetch', async (url: string, options: RequestInit) => {
    assert.equal(url, '/api/facturas/1/anular');
    assert.equal(options.method, 'POST');
    assert.deepEqual(JSON.parse(String(options.body)), { motivo: 'Error de carga' });
    return Response.json({ ...reporte, estado: 'ANULADA' });
  });
  assert.equal((await service.actualizarEstado(1, 'ANULADA', ' Error de carga ')).estado, 'ANULADA');
});
test('POST envía IDs y detalles sin precios ni importes; interpreta 201', async t => {
  t.mock.method(globalThis, 'fetch', async (_: string, options: RequestInit) => {
    assert.equal(options.method, 'POST'); assert.deepEqual(JSON.parse(String(options.body)), payload);
    return Response.json(reporte, { status: 201 });
  });
  assert.equal((await service.crearFactura(payload)).id, 1);
});
test('PUT estado no mezcla cabecera; PUT completo conserva los múltiples detalles', async t => {
  const bodies: unknown[] = [];
  t.mock.method(globalThis, 'fetch', async (_: string, options: RequestInit) => { assert.equal(options.method, 'PUT'); bodies.push(JSON.parse(String(options.body))); return Response.json(reporte); });
  await service.actualizarEstado(1, 'EMITIDA', 'Revisada');
  await service.actualizarFactura(1, { ...payload, detalles: [...payload.detalles, { articuloId: 2, cantidad: 3 }] });
  assert.deepEqual(bodies[0], { estado: 'EMITIDA', observaciones: 'Revisada' });
  assert.equal((bodies[1] as FacturaRequestDTO).detalles.length, 2);
  await assert.rejects(service.actualizarEstado(1, 'PAGADA'), /inválido/);
});
test('DELETE acepta 204 sin intentar leer JSON', async t => {
  t.mock.method(globalThis, 'fetch', async (_: string, options: RequestInit) => { assert.equal(options.method, 'DELETE'); return new Response(null, { status: 204 }); });
  assert.equal(await service.eliminarFactura(1), undefined);
});
for (const status of [400, 404, 409, 500]) test(`maneja HTTP ${status} y oculta detalles internos en 500`, async t => {
  t.mock.method(globalThis, 'fetch', async () => Response.json({ mensaje: 'Mensaje controlado', errores: ['Campo inválido'], trace: 'dato privado' }, { status }));
  await assert.rejects(service.obtenerFacturas({}), (e: unknown) => {
    assert.ok(e instanceof ApiError); assert.equal(e.status, status); assert.ok(!e.message.includes('dato privado'));
    assert.equal(e.message.includes('Mensaje controlado'), status < 500); return true;
  });
});
test('maneja conexión caída, HTML y JSON de forma incorrecta', async t => {
  const mock = t.mock.method(globalThis, 'fetch', async (): Promise<Response> => { throw new TypeError('Failed to fetch'); });
  await assert.rejects(service.obtenerFacturas({}), /No se pudo conectar/);
  mock.mock.mockImplementation(async () => new Response('<html>Error</html>'));
  await assert.rejects(service.obtenerFacturas({}), /JSON inválida/);
  mock.mock.mockImplementation(async () => Response.json([{ id: 1 }]));
  await assert.rejects(service.obtenerFacturas({}), /estructura/);
});
test('validación coincide con límites Java, incluidos fechas y renglones', () => {
  validarFactura(payload); assert.equal(fechaValida('2026-02-30'), false);
  for (const cantidad of [0, -1, 1.0001, 1000000, NaN]) assert.throws(() => validarFactura({ ...payload, detalles: [{ articuloId: 1, cantidad }] }));
  validarFactura({ ...payload, detalles: [{ articuloId: 1, cantidad: 0.001 }, { articuloId: 2, cantidad: 999999.999 }] });
  assert.throws(() => validarFactura({ ...payload, detalles: [] }));
  assert.throws(() => validarFactura({ ...payload, clienteDomicilio: 'a'.repeat(251) }));
  assert.throws(() => validarFactura({ ...payload, condicionVenta: 'a'.repeat(81) }));
  assert.throws(() => validarFactura({ ...payload, detalles: Array.from({ length: 101 }, () => payload.detalles[0]) }));
});
test('vista previa HALF_UP y suma de dos renglones equivalentes al backend', () => {
  assert.deepEqual(calcular('0.005', 1, 50), { neto: 1n, iva: 1n, total: 2n });
  const a = calcular(2, 1000, 21), b = calcular(3, 200, 10.5);
  assert.equal(importe(a.neto + b.neto), '2600.00'); assert.equal(importe(a.iva + b.iva), '483.00'); assert.equal(importe(a.total + b.total), '3083.00');
});
