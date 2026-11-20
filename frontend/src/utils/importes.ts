// Vista previa equivalente a CalculadoraImportes.java; el backend confirma el resultado.
export function decimal(valor: number | string, escala: number): bigint {
  const texto = String(valor);
  if (!/^\d+(\.\d+)?$/.test(texto)) throw new Error('Número decimal inválido');
  const [entero, fraccion = ''] = texto.split('.');
  if (fraccion.length > escala) throw new Error('Demasiados decimales');
  return BigInt(entero) * 10n ** BigInt(escala) + BigInt(fraccion.padEnd(escala, '0'));
}
export function importe(centavos: bigint): string { return `${centavos / 100n}.${String(centavos % 100n).padStart(2, '0')}`; }
export function calcular(cantidad: number | string, precio: number, alicuota: number): { neto: bigint; iva: bigint; total: bigint } {
  const neto = (decimal(cantidad, 3) * decimal(precio, 2) + 500n) / 1000n;
  const iva = (neto * decimal(alicuota, 2) + 5000n) / 10000n;
  return { neto, iva, total: neto + iva };
}

/** Mismo reparto por restos mayores y redondeo que DescuentoGeneral.java. */
export function descontar(lineas: { neto: bigint; alicuota: number }[], tipo: string, valor: number | string) {
  if (!['SIN_DESCUENTO','PORCENTAJE','MONTO_FIJO'].includes(tipo)) throw new Error('Seleccioná un tipo de descuento válido.');
  const v = decimal(valor, 2), subtotal = lineas.reduce((s,l) => s + l.neto, 0n);
  if (tipo === 'SIN_DESCUENTO' && v !== 0n) throw new Error('Elegí porcentaje o monto fijo.');
  if (tipo === 'PORCENTAJE' && v > 10000n) throw new Error('El descuento no puede superar el 100%.');
  const descuento = tipo === 'PORCENTAJE' ? (subtotal * v + 5000n) / 10000n : tipo === 'MONTO_FIJO' ? v : 0n;
  if (descuento > subtotal) throw new Error('El descuento no puede superar el subtotal antes de IVA.');
  const partes = lineas.map(l => subtotal ? l.neto * descuento / subtotal : 0n);
  const orden = lineas.map((l,i) => ({ i, resto: subtotal ? l.neto * descuento % subtotal : 0n }))
    .sort((a,b) => a.resto === b.resto ? a.i - b.i : a.resto > b.resto ? -1 : 1);
  const faltan = Number(descuento - partes.reduce((s,p) => s+p,0n));
  for (let i=0; i<faltan; i++) partes[orden[i].i]++;
  const detalles = lineas.map((l,i) => {
    const neto = l.neto - partes[i], iva = (neto * decimal(l.alicuota,2) + 5000n) / 10000n;
    return { neto, iva, total: neto+iva, descuento: partes[i] };
  });
  const neto = detalles.reduce((s,d) => s+d.neto,0n), iva = detalles.reduce((s,d) => s+d.iva,0n);
  return { subtotal, descuento, neto, iva, total: neto+iva, detalles };
}
