import type { CatalogosDTO } from './models/catalogos.dto';
import type { InvoiceForm } from './invoice-form';
import { el, input, opciones } from './utils/dom';
import { escape, money, mensaje } from './utils/formatters';

export class ChangeListForm {
  private readonly dialog = el<HTMLDialogElement>('#change-list-dialog');
  private readonly form = el<HTMLFormElement>('#change-list-form');
  private version = 0;
  private busy = false;
  constructor(private catalogo: () => CatalogosDTO, private editor: () => InvoiceForm) {
    el('#change-price-list').addEventListener('click', () => this.abrir());
    this.form.addEventListener('submit', e => { e.preventDefault(); void this.consultar(); });
    this.form.addEventListener('change', () => this.limpiar());
    this.dialog.addEventListener('close', () => { this.version++; this.editor().descartarCambioLista(); });
    el('#apply-price-list').addEventListener('click', () => {
      try {
        const nombre = el('#change-list-impact').dataset.lista ?? '';
        this.editor().aplicarCambioLista(); this.dialog.close();
        el('#invoice-list-status').textContent = `Lista actualizada a «${nombre}». Revisá el total antes de guardar.`; el('#invoice-list-status').hidden = false;
      } catch (e) { this.error(e); this.limpiar(); }
    });
  }
  private abrir(): void {
    this.version++; this.form.reset(); this.limpiar(); el('#change-list-error').hidden = true;
    opciones(el<HTMLSelectElement>('[name=listaPrecioId]',this.form), this.catalogo().listasPrecio.filter(l => l.id !== this.editor().listaSeleccionada), l => l.nombre, 'Elegí otra lista');
    el('#change-list-description').textContent = `Lista actual: ${this.editor().nombreListaSeleccionada}.`;
    this.dialog.showModal(); input('listaPrecioId',this.form).focus();
  }
  private limpiar(): void {
    this.editor().descartarCambioLista(); el('#change-list-preview').hidden = true; el('#apply-price-list').hidden = true;
  }
  private error(e: unknown): void { el('#change-list-error').textContent = mensaje(e); el('#change-list-error').hidden = false; }
  private lock(busy: boolean): void {
    this.busy = busy; el<HTMLSelectElement>('[name=listaPrecioId]',this.form).disabled = busy;
    el<HTMLButtonElement>('#preview-price-list').disabled = busy; el<HTMLButtonElement>('#apply-price-list').disabled = busy;
    el('#change-list-loading').hidden = !busy;
  }
  private async consultar(): Promise<void> {
    if (this.busy || !this.form.reportValidity()) return;
    const version = ++this.version; this.limpiar(); el('#change-list-error').hidden = true; this.lock(true);
    try {
      const vista = await this.editor().prepararCambioLista(Number(input('listaPrecioId',this.form).value), () => this.dialog.open && version === this.version);
      if (!vista || version !== this.version) return;
      el('#change-list-impact').textContent = `${vista.actual} → ${vista.nueva}. Total actual: ${vista.totalActual === null ? 'sin precio completo' : money(vista.totalActual,vista.moneda)}. Total nuevo: ${money(vista.totalNuevo,vista.moneda)}.`;
      el('#change-list-impact').dataset.lista = vista.nueva;
      el('#change-list-rows').innerHTML = vista.filas.map(f => `<tr><td data-label="Artículo">${escape(f.descripcion)}</td><td data-label="Cantidad">${escape(f.cantidad)}</td><td data-label="Precio actual">${f.precioActual === null ? '—' : escape(money(f.precioActual,vista.moneda))}</td><td data-label="Precio nuevo">${escape(money(f.precioNuevo,vista.moneda))}</td></tr>`).join('');
      el('#change-list-preview').hidden = false; el('#apply-price-list').hidden = false;
    } catch (e) { if (version === this.version) this.error(e); }
    finally { this.lock(false); }
  }
}
