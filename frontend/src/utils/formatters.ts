export const money = (value: number | string, currency = 'ARS'): string => {
  // Preservar los centavos de la vista previa BigInt sin pasarlos por number.
  const text = String(value);
  if (/^\d+(\.\d{1,2})?$/.test(text)) {
    const [whole, fraction = ''] = text.split('.');
    return `${currency} ${new Intl.NumberFormat('es-AR').format(BigInt(whole))},${fraction.padEnd(2, '0')}`.trim();
  }
  return `${currency} ${new Intl.NumberFormat('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(Number(value))}`.trim();
};
export const fecha = (value: string): string => new Intl.DateTimeFormat('es-AR').format(new Date(`${value}T12:00:00`));
export const referencia = (id: number): string => `#${String(id).padStart(6, '0')}`;
export const hoy = (): string => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};
/** Usar en todo texto externo interpolado en plantillas HTML. */
export const escape = (value: unknown): string => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]!);
export const mensaje = (error: unknown): string => error instanceof Error ? error.message : 'No se pudo completar la operación.';
