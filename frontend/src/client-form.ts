import type { ClienteDTO } from './models/catalogos.dto';
import { obtenerCliente, guardarCliente } from './services/CatalogoService';
import { el, input } from './utils/dom';
import { mensaje } from './utils/formatters';

export class ClientForm {
  private readonly dialog = el<HTMLDialogElement>('#client-edit-dialog');
  private readonly form = el<HTMLFormElement>('#client-edit-form');
  private id: number | null = null;
  private busy = false;
  constructor(private guardado: (cliente: ClienteDTO) => void) {
    this.form.addEventListener('submit', event => { event.preventDefault(); void this.guardar(); });
    this.dialog.addEventListener('cancel', event => { if (this.busy) event.preventDefault(); });
  }
  async abrir(id: number | null = null): Promise<void> {
    if (this.busy) return;
    this.id = id; this.form.reset(); el('#client-edit-error').hidden = true;
    this.dialog.showModal(); this.lock(true);
    try {
      if (id !== null) {
        const c = await obtenerCliente(id);
        for (const name of ['nombre','documento','tipoDocumento','condicionIvaReceptorId','domicilio'] as const)
          input(name, this.form).value = String(c[name] ?? '');
      }
    } catch (e) { this.error(e); }
    finally { this.lock(false); input('nombre', this.form).focus(); }
  }
  private lock(busy: boolean): void {
    this.busy = busy; el<HTMLFieldSetElement>('#client-edit-fields').disabled = busy;
    this.dialog.querySelectorAll<HTMLButtonElement>('button').forEach(b => { b.disabled = busy; });
  }
  private error(e: unknown): void { el('#client-edit-error').textContent = mensaje(e); el('#client-edit-error').hidden = false; }
  private async guardar(): Promise<void> {
    if (this.busy || !this.form.reportValidity()) return;
    this.lock(true); el('#client-edit-error').hidden = true;
    try {
      const cliente = await guardarCliente(this.id, {
        nombre: input('nombre',this.form).value.trim(), documento: input('documento',this.form).value.trim(),
        tipoDocumento: Number(input('tipoDocumento',this.form).value), condicionIvaReceptorId: Number(input('condicionIvaReceptorId',this.form).value),
        domicilio: input('domicilio',this.form).value.trim() || null
      });
      this.guardado(cliente); this.dialog.close();
    } catch (e) { this.error(e); }
    finally { this.lock(false); }
  }
}
