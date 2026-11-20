import test from 'node:test';
import assert from 'node:assert/strict';
import { calcular, decimal, importe } from '../../main/resources/static/importes.mjs';

test('coincide con la factura de referencia: dos cuadernos y tres lápices', () => {
  const cuadernos = calcular('2', '1000.00', '21.00');
  const lapices = calcular('3', '200.00', '10.50');
  assert.equal(importe(cuadernos.neto + lapices.neto), '2600.00');
  assert.equal(importe(cuadernos.iva + lapices.iva), '483.00');
  assert.equal(importe(cuadernos.total + lapices.total), '3083.00');
});
test('redondea HALF_UP el neto y luego calcula IVA sobre ese neto', () => {
  assert.deepEqual(calcular('0.005', '1.00', '50.00'), {neto:1n,iva:1n,total:2n});
  assert.deepEqual(calcular('0.004', '1.00', '50.00'), {neto:0n,iva:0n,total:0n});
});
test('mantiene precisión con precios grandes y cantidades fraccionarias', () => {
  assert.equal(importe(calcular('999999.999', '9999999999999.99', '21.00').neto), '9999999989999990000.00');
  assert.equal(decimal('0.001',3),1n);
  assert.equal(importe(1n), '0.01');
});
test('rechaza datos decimales inválidos y exceso de precisión', () => {
  for (const value of ['NaN','-1','1.2345','','1e3']) assert.throws(() => decimal(value,3));
});
