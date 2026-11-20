import type { CatalogosDTO } from './models/catalogos.dto';
import { crearListaPrecio, guardarPrecio } from './services/CatalogoService';
import { el, input, opciones } from './utils/dom';
import { mensaje } from './utils/formatters';

export class PriceListForm {
  private saving = false;
  constructor(private catalogo: () => CatalogosDTO, private actualizado: (c: CatalogosDTO) => void,
      private guardado: (lista: number) => void) {
    for (const tipo of ['list', 'price-entry']) {
      const form = el<HTMLFormElement>(`#${tipo}-form`), dialog = el<HTMLDialogElement>(`#${tipo}-dialog`);
      dialog.addEventListener('cancel', e => { if (this.saving) e.preventDefault(); });
      form.addEventListener('submit', e => { e.preventDefault(); void this.guardar(tipo); });
    }
    el('#price-entry-form').addEventListener('change', e => {
      if (e.target instanceof HTMLSelectElement) this.precioActual();
    });
  }
  abrirLista(): void {
    el<HTMLFormElement>('#list-form').reset(); el('#list-error').hidden = true;
    el<HTMLDialogElement>('#list-dialog').showModal(); input('nombre', el('#list-form')).focus();
  }
  abrirPrecio(lista: string, moneda: string, articulo = ''): void {
    const form = el<HTMLFormElement>('#price-entry-form'), c = this.catalogo();
    form.reset(); el('#price-entry-error').hidden = true;
    opciones(el('[name=listaPrecioId]', form), c.listasPrecio, l => l.nombre, 'Seleccioná una lista');
    opciones(el('[name=articuloId]', form), c.articulos, a => `${a.codigo} · ${a.descripcion}`, 'Seleccioná un producto');
    opciones(el('[name=tipoMonedaId]', form), c.monedas, m => m.codigo, 'Seleccioná una moneda');
    input('listaPrecioId', form).value = lista || '1'; input('tipoMonedaId', form).value = moneda || String(c.monedas.find(m => m.codigo === 'ARS')?.id ?? '');
    input('articuloId', form).value = articulo; this.precioActual();
    el<HTMLDialogElement>('#price-entry-dialog').showModal();
  }
  private precioActual(): void {
    const f = el('#price-entry-form');
    const p = this.catalogo().precios.find(p => p.listaPrecioId === Number(input('listaPrecioId', f).value)
      && p.articuloId === Number(input('articuloId', f).value) && p.tipoMonedaId === Number(input('tipoMonedaId', f).value));
    input('precio', f).value = p ? String(p.precio) : '';
    el('#price-entry-hint').textContent = p ? 'Este precio ya existe. Al guardar se actualizará para las próximas facturas.' : 'Se agregará el producto a esta lista con el precio indicado.';
  }
  private async guardar(tipo: string): Promise<void> {
    const form = el<HTMLFormElement>(`#${tipo}-form`), dialog = el<HTMLDialogElement>(`#${tipo}-dialog`);
    if (this.saving || !form.reportValidity()) return;
    this.saving = true; el(`#${tipo}-error`).hidden = true;
    form.querySelectorAll<HTMLButtonElement | HTMLInputElement | HTMLSelectElement>('button,input,select').forEach(x => { x.disabled = true; });
    try {
      let listaId: number;
      if (tipo === 'list') {
        const lista = await crearListaPrecio(input('nombre', form).value.trim()); listaId = lista.id;
        this.actualizado({ ...this.catalogo(), listasPrecio: [...this.catalogo().listasPrecio, lista].sort((a, b) => a.nombre.localeCompare(b.nombre, 'es')) });
      } else {
        listaId = Number(input('listaPrecioId', form).value);
        const precio = await guardarPrecio(listaId, { articuloId: Number(input('articuloId', form).value), tipoMonedaId: Number(input('tipoMonedaId', form).value), precio: Number(input('precio', form).value) });
        this.actualizado({ ...this.catalogo(), precios: [...this.catalogo().precios.filter(p => p.listaPrecioId !== listaId || p.articuloId !== precio.articuloId || p.tipoMonedaId !== precio.tipoMonedaId), precio] });
      }
      this.guardado(listaId); dialog.close();
    } catch (e) { el(`#${tipo}-error`).textContent = mensaje(e); el(`#${tipo}-error`).hidden = false; }
    finally {
      this.saving = false;
      form.querySelectorAll<HTMLButtonElement | HTMLInputElement | HTMLSelectElement>('button,input,select').forEach(x => { x.disabled = false; });
    }
  }
}
