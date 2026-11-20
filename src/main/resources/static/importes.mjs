// Aritmética decimal para la vista previa. El servidor confirma los importes finales.
export function decimal(valor, escala) {
  const texto = String(valor);
  if (!/^\d+(\.\d+)?$/.test(texto)) throw new Error('Número decimal inválido');
  const [entero, fraccion = ''] = texto.split('.');
  if (fraccion.length > escala) throw new Error('Demasiados decimales');
  return BigInt(entero) * 10n ** BigInt(escala) + BigInt(fraccion.padEnd(escala, '0'));
}
export function importe(centavos) {
  return `${centavos / 100n}.${String(centavos % 100n).padStart(2, '0')}`;
}
export function calcular(cantidad, precio, alicuota) {
  const neto = (decimal(cantidad, 3) * decimal(precio, 2) + 500n) / 1000n;
  const iva = (neto * decimal(alicuota, 2) + 5000n) / 10000n;
  return { neto, iva, total: neto + iva };
}
