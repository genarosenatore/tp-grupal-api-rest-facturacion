export type TipoFiscal = '1' | '6' | '11';
export const esFiscal = (tipo: string): tipo is TipoFiscal => ['1', '6', '11'].includes(tipo);
/** Sólo para anticipar la letra en el formulario; los reportes usan la letra persistida. */
export const letraFactura = (tipo: string | null): string => tipo === null || tipo === '' ? 'X' : ({ '1': 'A', '6': 'B', '11': 'C' })[tipo] ?? '';
export const condicionesReceptor = (tipo: string): { id: number; descripcion: string }[] => tipo === '1'
  ? [{ id: 1, descripcion: 'IVA Responsable Inscripto' }, { id: 6, descripcion: 'Responsable Monotributo' }]
  : tipo === '6' ? [{ id: 5, descripcion: 'Consumidor final' }, { id: 4, descripcion: 'IVA Sujeto Exento' }]
    : tipo === '11' ? [{ id: 1, descripcion: 'IVA Responsable Inscripto' }, { id: 4, descripcion: 'IVA Sujeto Exento' }, { id: 5, descripcion: 'Consumidor final' }, { id: 6, descripcion: 'Responsable Monotributo' }] : [];
export const descripcionCondicion = (id: number | null): string => ({ 1: 'IVA Responsable Inscripto', 4: 'IVA Sujeto Exento', 5: 'Consumidor final', 6: 'Responsable Monotributo' })[id ?? 0] ?? 'No informada';
export const alicuotaFiscalValida = (iva: number): boolean => [0, 2.5, 5, 10.5, 21, 27].includes(iva);
