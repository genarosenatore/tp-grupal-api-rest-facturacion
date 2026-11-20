import type { CatalogosDTO } from './models/catalogos.dto';
import type { ProductoReporteDTO } from './models/producto.dto';
import { crearProducto } from './services/CatalogoService';
import { el, input, opciones } from './utils/dom';
import { mensaje } from './utils/formatters';

export class ProductForm {
  private readonly dialog = el<HTMLDialogElement>('#product-dialog');
  private readonly form = el<HTMLFormElement>('#product-form');
  private saving = false;
  constructor(private catalogo: () => CatalogosDTO, private creado: (p: ProductoReporteDTO) => void) {
    this.form.addEventListener('submit', event => { event.preventDefault(); void this.guardar(); });
    this.dialog.addEventListener('cancel', event => { if (this.saving) event.preventDefault(); });
  }
  abrir(moneda: string, lista = '1'): void {
    if (this.saving) return;
    this.form.reset(); el('#product-error').hidden = true;
    opciones(el<HTMLSelectElement>('[name=listaPrecioId]', this.form), this.catalogo().listasPrecio, l => l.nombre, 'Seleccioná una lista');
    input('listaPrecioId', this.form).value = lista || '1';
    opciones(el<HTMLSelectElement>('[name=tipoMonedaId]', this.form), this.catalogo().monedas, m => `${m.codigo} · ${m.descripcion}`, 'Seleccioná una moneda');
    input('tipoMonedaId', this.form).value = moneda || String(this.catalogo().monedas.find(m => m.codigo === 'ARS')?.id ?? '');
    this.dialog.showModal(); input('codigo', this.form).focus();
  }
  private lock(locked: boolean): void {
    this.saving = locked;
    el<HTMLFieldSetElement>('#product-fields').disabled = locked;
    this.dialog.querySelectorAll<HTMLButtonElement>('button').forEach(b => { b.disabled = locked; });
    el('#save-product').textContent = locked ? 'Guardando…' : 'Guardar producto';
  }
  private async guardar(): Promise<void> {
    if (this.saving || !this.form.reportValidity()) return;
    el('#product-error').hidden = true;
    this.lock(true);
    try {
      const producto = await crearProducto({
        codigo: input('codigo', this.form).value.trim(), descripcion: input('descripcion', this.form).value.trim(),
        alicuotaIva: Number(input('alicuotaIva', this.form).value),
        tipoMonedaId: Number(input('tipoMonedaId', this.form).value), precio: Number(input('precio', this.form).value),
        listaPrecioId: Number(input('listaPrecioId', this.form).value)
      });
      this.creado(producto);
      this.dialog.close(); this.form.reset();
    } catch (e) {
      el('#product-error').textContent = mensaje(e); el('#product-error').hidden = false;
      el('#product-error').scrollIntoView({ block: 'nearest' });
    }
    finally { this.lock(false); }
  }
}
