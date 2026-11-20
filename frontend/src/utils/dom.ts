export function el<T extends HTMLElement = HTMLElement>(selector: string, root: ParentNode = document): T {
  const node = root.querySelector<T>(selector);
  if (!node) throw new Error(`No se encontró ${selector}`);
  return node;
}
export const input = (name: string, root: ParentNode = document): HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement => el(`[name="${name}"]`, root);
export function opciones<T extends { id: number }>(select: HTMLSelectElement, rows: T[], label: (row: T) => string, placeholder: string): void {
  select.replaceChildren(new Option(placeholder, ''), ...rows.map(row => new Option(label(row), String(row.id))));
}
