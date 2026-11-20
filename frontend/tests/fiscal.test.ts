import test from 'node:test';
import assert from 'node:assert/strict';
import { condicionesReceptor, letraFactura, alicuotaFiscalValida } from '../src/utils/fiscal';
import { validarFactura } from '../src/utils/validators';
import type { FacturaRequestDTO } from '../src/models/factura.dto';

const base: FacturaRequestDTO = { fecha: '2026-11-13', clienteId: 1, condicionIvaId: 1, puntoVentaId: 2, tipoMonedaId: 1, detalles: [{ articuloId: 1, cantidad: 2 }] };
test('A exige CUIT y condición RI o monotributo; B acepta CF y exento con CUIT', () => {
  validarFactura({ ...base, tipoComprobante: '1', tipoDocumentoReceptor: 80, condicionIvaReceptorId: 1 });
  validarFactura({ ...base, tipoComprobante: '1', tipoDocumentoReceptor: 80, condicionIvaReceptorId: 6 });
  validarFactura({ ...base, tipoComprobante: '6', tipoDocumentoReceptor: 96, condicionIvaReceptorId: 5 });
  assert.throws(() => validarFactura({ ...base, tipoComprobante: '1', tipoDocumentoReceptor: 96, condicionIvaReceptorId: 1 }), /CUIT/);
  assert.throws(() => validarFactura({ ...base, tipoComprobante: '6', tipoDocumentoReceptor: 96, condicionIvaReceptorId: 4 }), /CUIT/);
  assert.throws(() => validarFactura({ ...base, tipoComprobante: '11', tipoDocumentoReceptor: 96, condicionIvaReceptorId: 1 }), /CUIT/);
  for (const condicion of [1,4,5,6]) validarFactura({ ...base, tipoComprobante: '11', tipoDocumentoReceptor: 80, condicionIvaReceptorId: condicion });
  assert.throws(() => validarFactura({ ...base, tipoComprobante: '1', tipoDocumentoReceptor: 96, condicionIvaReceptorId: 5 }), /condición fiscal/);
  assert.throws(() => validarFactura({ ...base, tipoComprobante: '6', tipoDocumentoReceptor: 80, condicionIvaReceptorId: 6 }), /condición fiscal/);
});
test('identifica letras y condiciones por clase y limita las alícuotas de homologación', () => {
  assert.deepEqual(['1', '6', '11'].map(letraFactura), ['A', 'B', 'C']);
  assert.equal(letraFactura(null), 'X'); assert.equal(letraFactura(''), 'X');
  assert.deepEqual(condicionesReceptor('1').map(c => c.id), [1, 6]);
  assert.deepEqual(condicionesReceptor('6').map(c => c.id), [5, 4]);
  assert.ok([0, 2.5, 5, 10.5, 21, 27].every(alicuotaFiscalValida));
  assert.equal(alicuotaFiscalValida(7), false);
});
