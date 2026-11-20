import type { CatalogosDTO, ClienteDTO } from './models/catalogos.dto';
import type { FacturaDetalleRequestDTO, FacturaReporteDTO, FacturaRequestDTO } from './models/factura.dto';
import { el, input, opciones } from './utils/dom';
import { escape, hoy, money, referencia } from './utils/formatters';
import { calcular, descontar, importe } from './utils/importes';
import { obtenerCliente, obtenerPrecio, incorporarProducto, consultarPrecios } from './services/CatalogoService';
import { validarFactura } from './utils/validators';
import { esFiscal, condicionesReceptor, alicuotaFiscalValida, descripcionCondicion } from './utils/fiscal';

const tiposComprobante = [
  { id: '11', descripcion: 'Factura C · Homologación' },
  { id: '1', descripcion: 'Factura A · Homologación' },
  { id: '6', descripcion: 'Factura B · Homologación' },
  { id: '', descripcion: 'Comprobante X · Sin envío fiscal' }
];

export interface VistaCambioLista {
  actual: string; nueva: string; moneda: string; totalActual: string | null; totalNuevo: string;
  filas: { descripcion: string; cantidad: number; precioActual: number | null; precioNuevo: number }[];
}

export class InvoiceForm {
  readonly form = el<HTMLFormElement>('#invoice-form');
  private lines: (FacturaDetalleRequestDTO & { fraccionaria?: boolean })[] = [];
  private original?: FacturaReporteDTO;
  get editing(): number | null { return this.original?.id ?? null; }
  get listaSeleccionada(): number | null { return Number(input('listaPrecioId',this.form).value) || null; }
  get nombreListaSeleccionada(): string { return this.catalog.listasPrecio.find(l => l.id === this.listaSeleccionada)?.nombre ?? ''; }
  dirty = false;
  private busy = false;
  private pending = 0;
  private revision = 0;
  private clientVersion = 0;
  private clientSnapshot?: ClienteDTO;
  private clientError = '';
  private priceVersions = new Map<FacturaDetalleRequestDTO, number>();
  private lineErrors = new Map<FacturaDetalleRequestDTO, string>();
  private cambioLista?: { firma: string; listaId: number; catalogo: CatalogosDTO };
  private ultimaSeleccion = -1;
  constructor(private catalog: CatalogosDTO) {
    this.actualizarCatalogos(catalog);
    this.form.addEventListener('input', () => { this.dirty = true; this.readLines(); this.preview(); });
    this.form.addEventListener('change', event => {
      if (event.target instanceof HTMLSelectElement && event.target.name === 'clienteId') {
        void this.seleccionarCliente(Number(event.target.value)).catch(e => this.mostrarError(e)); return;
      }
      if (event.target instanceof HTMLSelectElement && event.target.classList.contains('line-article')) {
        this.readLines(); const index = el<HTMLTableSectionElement>('#form-lines').rows.length ? event.target.closest('tr')!.sectionRowIndex : -1;
        void this.refrescarLinea(index).catch(e => this.mostrarError(e)); return;
      }
      if (event.target instanceof HTMLSelectElement && event.target.name === 'tipoDescuento' && event.target.value === 'SIN_DESCUENTO') input('valorDescuento', this.form).value = '0';
      if (event.target instanceof HTMLSelectElement && event.target.name === 'tipoMonedaId') {
        this.revision++; this.readLines(); this.dirty = true; this.renderLines(); return;
      }
      this.dirty = true; this.readLines(); this.preview();
    });
    el('#form-lines').addEventListener('click', event => {
      const search = event.target instanceof Element ? event.target.closest<HTMLButtonElement>('[data-search-line]') : null;
      if (search) this.form.dispatchEvent(new CustomEvent('abrir-precios', { detail: Number(search.dataset.searchLine) }));
      const button = event.target instanceof Element ? event.target.closest<HTMLButtonElement>('[data-remove]') : null;
      if (button && !this.busy) {
        const index = Number(button.dataset.remove);
        this.revision++; this.lines.splice(index, 1); this.dirty = true; this.renderLines();
        const controls = this.form.querySelectorAll<HTMLSelectElement>('.line-article');
        (controls[Math.min(index, controls.length - 1)] ?? el('#add-line')).focus();
      }
    });
    const abrirCombo = (target: HTMLSelectElement): void => {
      target.focus();
      if (target.name === 'clienteId') this.form.dispatchEvent(new CustomEvent('abrir-clientes'));
      if (target.classList.contains('line-article')) this.form.dispatchEvent(new CustomEvent('abrir-precios', { detail: target.closest('tr')!.sectionRowIndex }));
    };
    this.form.addEventListener('pointerdown', event => {
      if (event.target instanceof HTMLSelectElement && (event.target.name === 'clienteId' || event.target.classList.contains('line-article'))) { event.preventDefault(); abrirCombo(event.target); }
    });
    this.form.addEventListener('keydown', event => {
      if (['Enter',' ','ArrowDown'].includes(event.key) && event.target instanceof HTMLSelectElement && (event.target.name === 'clienteId' || event.target.classList.contains('line-article'))) { event.preventDefault(); abrirCombo(event.target); }
    });
    this.reset();
  }
  actualizarCatalogos(catalog: CatalogosDTO): void {
    this.catalog = catalog;
    const values = ['clienteId', 'puntoVentaId', 'condicionIvaId', 'tipoMonedaId'].map(name => [name, input(name, this.form).value]);
    const select = (name: string): HTMLSelectElement => el(`[name="${name}"]`, this.form);
    opciones(select('clienteId'), catalog.clientes, c => `${c.nombre} · ${c.documento}`, 'Seleccioná un cliente');
    opciones(select('puntoVentaId'), catalog.puntosVenta, p => `${p.codigo} · ${p.descripcion}`, 'Seleccioná un punto de venta');
    opciones(select('condicionIvaId'), catalog.condicionesIva, c => c.descripcion, 'Seleccioná una condición');
    opciones(select('tipoMonedaId'), catalog.monedas, m => `${m.codigo} · ${m.descripcion}`, 'Seleccioná una moneda');
    values.forEach(([name, value]) => { input(name, this.form).value = value; });
    if (!input('puntoVentaId', this.form).value && catalog.puntosVenta.length === 1)
      input('puntoVentaId', this.form).value = String(catalog.puntosVenta[0].id);
    this.renderLines();
  }
  reset(invoice?: FacturaReporteDTO): void {
    this.revision++; this.clientVersion++; this.cambioLista = undefined; this.form.reset(); this.original = invoice;
    this.clientError = ''; this.lineErrors.clear(); this.priceVersions.clear();
    this.clientSnapshot = invoice ? { id: invoice.clienteId, nombre: invoice.clienteNombre, documento: invoice.clienteDocumento,
      tipoDocumento: invoice.tipoDocumentoReceptor, condicionIvaReceptorId: invoice.condicionIvaReceptorId, domicilio: invoice.clienteDomicilio } : undefined;
    input('listaPrecioId', this.form).value = String(invoice?.listaPrecioId ?? '');
    input('fecha', this.form).value = invoice?.fecha ?? hoy();
    if (!invoice && this.catalog.puntosVenta.length === 1)
      input('puntoVentaId', this.form).value = String(this.catalog.puntosVenta[0].id);
    this.actualizarTiposComprobante(invoice?.tipoComprobante ?? (invoice ? '' : this.catalog.homologacionHabilitada ? '11' : ''));
    input('condicionVenta', this.form).value = invoice?.condicionVenta ?? '';
    input('tipoDescuento', this.form).value = invoice?.tipoDescuento ?? 'SIN_DESCUENTO';
    input('valorDescuento', this.form).value = String(invoice?.valorDescuento ?? 0);
    input('condicionIvaId', this.form).value = String(invoice?.condicionIvaId ?? this.catalog.condicionesIva[0]?.id ?? '');
    input('tipoMonedaId', this.form).value = String(invoice?.tipoMonedaId ?? this.catalog.monedas.find(m => m.codigo === 'ARS')?.id ?? '');
    if (invoice) for (const key of ['clienteId', 'puntoVentaId', 'condicionIvaId', 'tipoMonedaId', 'observaciones'] as const) input(key, this.form).value = String(invoice[key] ?? '');
    this.lines = invoice ? invoice.detalles.map(d => ({ articuloId: d.articuloId, cantidad: d.cantidad, fraccionaria: !Number.isInteger(d.cantidad) })) : [];
    this.dirty = false; el('#form-error').hidden = true;
    el('#invoice-list-status').hidden = true;
    el('#form-title').textContent = invoice ? `Editar factura ${referencia(invoice.id)}` : 'Nueva factura';
    el('#save-invoice').textContent = invoice ? 'Guardar cambios' : 'Crear factura';
    el('#reset-invoice').textContent = invoice ? 'Restablecer cambios' : 'Limpiar formulario';
    this.renderLines(); this.lock(false);
  }
  descartarCambios(): void { this.reset(this.original); }
  lock(busy: boolean): void {
    this.busy = busy;
    el<HTMLFieldSetElement>('#invoice-fields').disabled = busy;
    el<HTMLButtonElement>('[data-close=invoice-dialog]').disabled = busy;
    el<HTMLButtonElement>('#add-line').disabled = busy || (this.lines.length >= 100 && this.lines.every(l => l.articuloId));
    el<HTMLButtonElement>('#save-invoice').disabled = busy || this.pending > 0 || !this.lines.length;
    el<HTMLButtonElement>('#change-price-list').disabled = busy || this.pending > 0;
  }
  async seleccionarCliente(id: number, vigente: () => boolean = () => true): Promise<void> {
    const version = ++this.clientVersion;
    this.pending++; this.lock(this.busy); this.clientError = 'Consultando los datos del cliente…'; this.preview();
    try {
      if (!id) {
        this.clientSnapshot = undefined; this.clientError = '';
        return;
      }
      const c = await obtenerCliente(id);
      if (version !== this.clientVersion) return;
      if (!vigente()) { this.clientError = ''; return; }
      this.catalog.clientes = [...this.catalog.clientes.filter(v => v.id !== id), c];
      opciones(el<HTMLSelectElement>('[name=clienteId]',this.form), this.catalog.clientes, v => `${v.nombre} · ${v.documento}`, 'Seleccioná un cliente');
      this.clientSnapshot = c; input('clienteId', this.form).value = String(id);
      this.clientError = ''; this.dirty = true;
    } catch (e) { if (version === this.clientVersion) this.clientError = 'No se pudieron obtener los datos del cliente. Volvé a seleccionarlo.'; throw e; }
    finally { this.pending--; this.lock(this.busy); this.preview(); }
  }
  async seleccionarPrecio(articuloId: number, monedaId: number, listaPrecioId: number, indice?: number, vigente: () => boolean = () => true): Promise<void> {
    this.readLines();
    if (indice === undefined && this.lines.length >= 100 && this.lines.every(l => l.articuloId)) throw new Error('La factura admite hasta 100 renglones.');
    const actual = Number(input('tipoMonedaId', this.form).value);
    this.validarLista(listaPrecioId);
    if (actual && actual !== monedaId) throw new Error('Seleccioná un precio en la moneda de la factura.');
    const revision = this.revision;
    this.pending++; this.lock(this.busy);
    try {
      const precio = await obtenerPrecio(articuloId, monedaId, listaPrecioId);
      if (!vigente()) return;
      if (revision !== this.revision) throw new Error('La factura cambió durante la consulta. Volvé a seleccionar el artículo.');
      this.validarLista(listaPrecioId);
      const vacio = indice !== undefined ? indice : this.lines.findIndex(l => !l.articuloId);
      if (vacio >= this.lines.length) throw new Error('El renglón ya no existe.');
      this.catalog = Object.assign(this.catalog, incorporarProducto(this.catalog, precio));
      input('listaPrecioId', this.form).value = String(listaPrecioId);
      input('tipoMonedaId', this.form).value = String(monedaId);
      if (vacio >= 0) this.lines[vacio] = { articuloId, cantidad: this.lines[vacio].cantidad, fraccionaria: this.lines[vacio].fraccionaria };
      else this.lines.push({ articuloId, cantidad: 1 });
      this.ultimaSeleccion = vacio >= 0 ? vacio : this.lines.length - 1;
      this.dirty = true; this.renderLines();
    } finally { this.pending--; this.lock(this.busy); }
  }
  private validarLista(id: number): void {
    if (this.listaSeleccionada && this.listaSeleccionada !== id) {
      const otra = this.catalog.listasPrecio.find(l => l.id === id)?.nombre ?? String(id);
      throw new Error(`No se pueden mezclar listas de precios. Esta factura usa «${this.nombreListaSeleccionada}» y el artículo elegido pertenece a «${otra}». Elegí un precio de «${this.nombreListaSeleccionada}» o usá «Cambiar lista» en la factura para revisar todos los precios antes de confirmar.`);
    }
  }
  enfocarCantidad(): void {
    this.form.querySelectorAll<HTMLInputElement>('.line-quantity')[this.ultimaSeleccion]?.focus();
  }
  descartarCambioLista(): void { this.cambioLista = undefined; }
  private firmaPrecios(): string {
    return JSON.stringify([this.revision, this.lines, ...['listaPrecioId','tipoMonedaId','tipoComprobante','tipoDescuento','valorDescuento'].map(n => input(n,this.form).value)]);
  }
  private resumenConLista(catalogo: CatalogosDTO, listaId: number) {
    const moneda = Number(input('tipoMonedaId',this.form).value), tipo = input('tipoComprobante',this.form).value;
    return descontar(this.lines.map(linea => {
      const p = catalogo.precios.find(p => p.articuloId === linea.articuloId && p.tipoMonedaId === moneda && p.listaPrecioId === listaId);
      const a = catalogo.articulos.find(a => a.id === linea.articuloId);
      if (!p || !a) throw new Error('Faltan precios para completar el cálculo.');
      if (esFiscal(tipo) && tipo !== '11' && !alicuotaFiscalValida(a.alicuotaIva)) throw new Error(`El IVA de «${a.descripcion}» no es compatible con este comprobante.`);
      return { neto: calcular(linea.cantidad,p.precio,0).neto, alicuota: tipo === '11' ? 0 : a.alicuotaIva };
    }), input('tipoDescuento',this.form).value,input('valorDescuento',this.form).value);
  }
  async prepararCambioLista(listaId: number, vigente: () => boolean): Promise<VistaCambioLista | null> {
    if (this.busy || this.pending) throw new Error('Esperá a que terminen las consultas en curso.');
    this.readLines(); this.cambioLista = undefined;
    if (!this.listaSeleccionada || !this.lines.length) throw new Error('Agregá al menos un artículo antes de cambiar de lista.');
    if (listaId === this.listaSeleccionada || !this.catalog.listasPrecio.some(l => l.id === listaId)) throw new Error('Elegí otra lista de precios.');
    if ([...this.form.querySelectorAll<HTMLInputElement>('.line-quantity')].some(i => !i.validity.valid)) throw new Error('Revisá las cantidades antes de cambiar de lista.');
    const firma = this.firmaPrecios(), actual = this.listaSeleccionada, monedaId = Number(input('tipoMonedaId',this.form).value);
    this.pending++; this.lock(this.busy);
    try {
      const precios = await consultarPrecios(this.lines.map(l => l.articuloId),listaId,monedaId);
      if (!vigente()) return null;
      if (firma !== this.firmaPrecios()) throw new Error('La factura cambió durante la consulta. Volvé a consultar los precios.');
      const faltantes = [...new Set(this.lines.filter(l => !precios.some(p => p.id === l.articuloId && p.listaPrecioId === listaId && p.tipoMonedaId === monedaId)).map(l => this.catalog.articulos.find(a => a.id === l.articuloId)?.descripcion ?? `Artículo ${l.articuloId}`))];
      if (faltantes.length) throw new Error(`La nueva lista no tiene precio en esta moneda para: ${faltantes.join(', ')}. Se conserva toda la factura sin cambios.`);
      let catalogo = this.catalog;
      for (const p of precios) catalogo = incorporarProducto(catalogo,p);
      const nuevo = this.resumenConLista(catalogo,listaId);
      let anterior: string | null = null;
      try { anterior = importe(this.resumenConLista(this.catalog,actual).total); } catch { /* La nueva lista puede recuperar una factura cuyo precio actual falta. */ }
      this.cambioLista = { firma, listaId, catalogo };
      return {
        actual: this.nombreListaSeleccionada, nueva: catalogo.listasPrecio.find(l => l.id === listaId)!.nombre,
        moneda: catalogo.monedas.find(m => m.id === monedaId)?.codigo ?? '', totalActual: anterior, totalNuevo: importe(nuevo.total),
        filas: this.lines.map(l => ({ descripcion: catalogo.articulos.find(a => a.id === l.articuloId)!.descripcion, cantidad: l.cantidad,
          precioActual: this.catalog.precios.find(p => p.articuloId === l.articuloId && p.listaPrecioId === actual && p.tipoMonedaId === monedaId)?.precio ?? null,
          precioNuevo: precios.find(p => p.id === l.articuloId)!.precio }))
      };
    } finally { this.pending--; this.lock(this.busy); }
  }
  aplicarCambioLista(): void {
    this.readLines(); const cambio = this.cambioLista;
    if (!cambio || cambio.firma !== this.firmaPrecios()) throw new Error('La factura cambió. Consultá nuevamente antes de aplicar la lista.');
    this.resumenConLista(cambio.catalogo,cambio.listaId);
    Object.assign(this.catalog,cambio.catalogo); input('listaPrecioId',this.form).value = String(cambio.listaId);
    this.revision++; this.lineErrors.clear(); this.cambioLista = undefined; this.dirty = true; this.renderLines();
  }
  private async refrescarLinea(index: number): Promise<void> {
    const linea = this.lines[index]; if (!linea?.articuloId) { this.preview(); return; }
    let lista = this.listaSeleccionada;
    if (!lista) {
      const moneda = Number(input('tipoMonedaId',this.form).value);
      const opciones = this.catalog.precios.filter(p => p.articuloId === linea.articuloId && p.tipoMonedaId === moneda);
      if (opciones.length === 1) lista = opciones[0].listaPrecioId;
      else {
        linea.articuloId = 0; this.renderLines();
        this.form.dispatchEvent(new CustomEvent('abrir-precios', { detail: index })); return;
      }
    }
    const revision = this.revision, version = (this.priceVersions.get(linea) ?? 0) + 1;
    this.priceVersions.set(linea, version); this.pending++; this.lock(this.busy); this.lineErrors.set(linea,'Consultando precio…'); this.preview();
    try {
      const p = await obtenerPrecio(linea.articuloId, Number(input('tipoMonedaId',this.form).value), lista);
      if (revision !== this.revision || this.priceVersions.get(linea) !== version) return;
      this.validarLista(lista);
      input('listaPrecioId',this.form).value = String(lista);
      Object.assign(this.catalog, incorporarProducto(this.catalog,p)); this.lineErrors.delete(linea);
    } catch (e) { this.lineErrors.set(linea, 'No se pudo consultar el precio. Volvé a seleccionar el artículo.'); throw e; }
    finally { this.pending--; this.lock(this.busy); this.dirty = true; this.preview(); }
  }
  private mostrarError(e: unknown): void { el('#form-error').textContent = e instanceof Error ? e.message : 'No se pudo consultar la selección.'; el('#form-error').hidden = false; }
  private readLines(): void {
    this.lines.forEach((line, index) => {
      const row = el<HTMLTableSectionElement>('#form-lines').rows[index];
      line.articuloId = Number(el<HTMLSelectElement>('.line-article', row).value);
      line.cantidad = Number(el<HTMLInputElement>('.line-quantity', row).value);
    });
  }
  private renderLines(): void {
    if (!this.lines.some(l => l.articuloId)) input('listaPrecioId',this.form).value = '';
    if (!this.lines.length) el('#invoice-list-status').hidden = true;
    el('#form-lines').replaceChildren(...this.lines.map((line, index) => {
      const row = document.createElement('tr');
      // Sólo plantillas locales y valores escapados; las etiquetas se insertan con Option.
      const limites = line.fraccionaria ? 'min="0.001" max="999999.999" step="0.001"' : 'min="1" max="999999" step="1"';
      row.innerHTML = `<td data-label="Artículo"><div class="article-picker"><select class="line-article" required aria-label="Cambiar artículo ${index + 1}"></select></div></td>
        <td data-label="Cantidad"><input class="line-quantity" type="number" ${limites} required value="${escape(line.cantidad)}" aria-label="Cantidad ${index + 1}"></td>
        <td data-label="Precio unitario" class="numeric line-price">—</td><td data-label="IVA" class="numeric line-tax">—</td>
        <td data-label="Total" class="numeric line-total">—</td>
        <td><button type="button" class="button danger subtle" data-remove="${index}" aria-label="Quitar artículo ${index + 1}">Quitar</button></td>`;
      const select = el<HTMLSelectElement>('.line-article', row);
      const listaId = Number(input('listaPrecioId', this.form).value), monedaId = Number(input('tipoMonedaId', this.form).value);
      const disponibles = this.catalog.articulos.filter(a => a.id === line.articuloId || this.catalog.precios.some(p => p.articuloId === a.id && (!listaId || p.listaPrecioId === listaId) && (!monedaId || p.tipoMonedaId === monedaId)));
      opciones(select, disponibles, a => `${a.codigo} · ${a.descripcion}`, 'Seleccioná un artículo');
      select.value = line.articuloId ? String(line.articuloId) : '';
      return row;
    }));
    el('#invoice-lines').hidden = !this.lines.length; el('#invoice-empty').hidden = !!this.lines.length;
    el('#line-hint').textContent = this.lines.some(line => line.fraccionaria)
      ? 'Los renglones con cantidades fraccionarias admiten hasta tres decimales. Los nuevos se cargan en unidades enteras.'
      : this.lines.length >= 100 ? 'Llegaste al límite de 100 artículos.' : 'Podés ajustar las cantidades de cada artículo.';
    el('#line-hint').hidden = !this.lines.length;
    el<HTMLButtonElement>('#add-line').disabled = this.busy || (this.lines.length >= 100 && this.lines.every(l => l.articuloId));
    this.preview();
  }
  private actualizarTiposComprobante(preferido = input('tipoComprobante', this.form).value): void {
    const cliente = this.clientSnapshot;
    const permitidos = tiposComprobante.filter(tipo => !cliente || tipo.id === '' || (
      condicionesReceptor(tipo.id).some(c => c.id === cliente.condicionIvaReceptorId)
      && [80,96,99].includes(cliente.tipoDocumento ?? 0)
      && (!(tipo.id === '1' || [1,4,6].includes(cliente.condicionIvaReceptorId ?? 0)) || cliente.tipoDocumento === 80)
    ));
    const selector = el<HTMLSelectElement>('[name=tipoComprobante]', this.form);
    const ids = ['seleccionar', ...permitidos.map(t => t.id)];
    if (JSON.stringify([...selector.options].map(o => o.value)) !== JSON.stringify(ids)) {
      const pendiente = new Option('Seleccioná un comprobante', 'seleccionar');
      pendiente.disabled = true; pendiente.hidden = true;
      selector.replaceChildren(pendiente, ...permitidos.map(t => new Option(t.descripcion, t.id)));
    }
    selector.value = permitidos.some(t => t.id === preferido) ? preferido : 'seleccionar';
    const pendiente = selector.value === 'seleccionar';
    selector.setCustomValidity(pendiente ? 'Elegí un comprobante compatible con la condición fiscal del cliente.' : '');
    el('#receipt-options-hint').textContent = !cliente ? 'Seleccioná el cliente para ver los comprobantes compatibles.'
      : pendiente ? 'El comprobante anterior no corresponde a este cliente. Elegí una de las opciones disponibles y revisá el total.'
      : 'Se muestran los comprobantes compatibles con la condición fiscal del cliente.';
  }
  private preview(): void {
    this.actualizarTiposComprobante();
    const tipo = input('tipoComprobante', this.form).value, fiscal = esFiscal(tipo), facturaC = tipo === '11';
    el('#invoice-list-context').textContent = this.listaSeleccionada
      ? `Lista de precios: ${this.nombreListaSeleccionada}`
      : 'Elegí la lista de precios al agregar el primer artículo.';
    el('#change-price-list').hidden = !this.listaSeleccionada;
    el('#local-iva-field').hidden = true;
    if (!input('condicionIvaId', this.form).value && this.catalog.condicionesIva.length)
      input('condicionIvaId', this.form).value = String(this.catalog.condicionesIva[0].id);
    el('#circuit-description').textContent = tipo === 'seleccionar' ? 'Seleccioná el tipo de comprobante para calcular los importes.' : facturaC
      ? 'Factura C en pesos. El precio de lista es final y el documento debe coincidir con el del cliente.' + (this.catalog.homologacionHabilitada ? '' : ' El envío a ARCA está deshabilitado en esta instancia.')
      : fiscal ? 'Factura A/B en pesos. El precio de lista es neto: se suma el IVA del artículo al total. Factura A requiere un cliente con CUIT.' + (this.catalog.homologacionHabilitada ? '' : ' El envío a ARCA está deshabilitado en esta instancia.')
        : 'Comprobante X. Documento no válido como factura, sin envío fiscal. Se aplican los precios y las alícuotas del catálogo.';
    const cliente = this.clientSnapshot;
    el('#client-data').hidden = !cliente;
    const tipoDocumento = cliente?.tipoDocumento === 80 ? 'CUIT' : cliente?.tipoDocumento === 96 ? 'DNI' : 'Documento';
    const identificacion = cliente?.tipoDocumento === 99 ? 'Sin identificar' : `${tipoDocumento} ${cliente?.documento ?? ''}`;
    el('#client-document').textContent = cliente ? identificacion : '';
    el('#client-condition').textContent = cliente ? descripcionCondicion(cliente.condicionIvaReceptorId) : '';
    el('#client-address').textContent = cliente ? cliente.domicilio?.trim() || 'No informado' : '';
    el('#selected-client').textContent = cliente ? `${cliente.nombre} · ${identificacion} · ${descripcionCondicion(cliente.condicionIvaReceptorId)}` : '';
    const documento = cliente?.documento.replaceAll('-', '') ?? '';
    const docTipo = String(cliente?.tipoDocumento ?? '');
    let problemaCliente = this.clientError;
    if (fiscal && cliente && !problemaCliente) {
      if (!cliente.tipoDocumento || !cliente.condicionIvaReceptorId) problemaCliente = 'Completá los datos fiscales en la ficha del cliente.';
      else if ((tipo === '1' || [1,4,6].includes(cliente.condicionIvaReceptorId)) && docTipo !== '80') problemaCliente = 'Elegí un cliente con CUIT para este comprobante.';
      else if (!condicionesReceptor(tipo).some(c => c.id === cliente.condicionIvaReceptorId)) problemaCliente = 'La condición fiscal del cliente no corresponde al tipo de comprobante.';
      else if ((docTipo === '80' && !/^\d{11}$/.test(documento)) || (docTipo === '96' && !/^\d{7,8}$/.test(documento))) problemaCliente = 'El documento no coincide con el tipo registrado en la ficha del cliente.';
    }
    el<HTMLSelectElement>('[name=clienteId]', this.form).setCustomValidity(problemaCliente);
    const monedaId = Number(input('tipoMonedaId', this.form).value);
    const moneda = this.catalog.monedas.find(m => m.id === monedaId)?.codigo ?? '';
    el<HTMLSelectElement>('[name=tipoMonedaId]', this.form).setCustomValidity(fiscal && moneda && moneda !== 'ARS' ? 'Para homologación seleccioná pesos argentinos (ARS).' : '');
    let neto = 0n, iva = 0n, total = 0n, missing = false, incomplete = !this.lines.length || !moneda || tipo === 'seleccionar';
    const bases = this.lines.map(() => ({ neto: 0n, alicuota: 0 }));
    this.form.querySelectorAll<HTMLTableRowElement>('#form-lines tr').forEach((row, i) => {
      const line = this.lines[i], select = el<HTMLSelectElement>('.line-article', row), quantity = el<HTMLInputElement>('.line-quantity', row);
      const article = this.catalog.articulos.find(a => a.id === line.articuloId);
      const price = this.catalog.precios.find(p => p.articuloId === line.articuloId && p.tipoMonedaId === monedaId && p.listaPrecioId === Number(input('listaPrecioId', this.form).value));
      const sinPrecio = !!article && !!moneda && !price;
      const ivaInvalido = fiscal && !facturaC && !!article && !alicuotaFiscalValida(article.alicuotaIva);
      select.setCustomValidity(this.lineErrors.get(line) || (sinPrecio ? 'El artículo no tiene precio en esta lista y moneda.' : ivaInvalido ? 'La alícuota del artículo no es admitida en homologación A/B.' : '')); missing ||= sinPrecio;
      el('.line-price', row).textContent = price ? money(price.precio, moneda) : '—';
      el('.line-tax', row).textContent = tipo === 'seleccionar' ? '—' : facturaC ? 'Sin discriminar' : article ? `${article.alicuotaIva} %` : '—';
      el('.line-total', row).textContent = '—';
      if (tipo === 'seleccionar' || !article || !price || !quantity.validity.valid || ivaInvalido) { incomplete = true; return; }
      try {
        const values = calcular(quantity.value, price.precio, facturaC ? 0 : article.alicuotaIva);
        neto += values.neto; iva += values.iva; total += values.total;
        bases[i] = { neto: values.neto, alicuota: facturaC ? 0 : article.alicuotaIva };
        el('.line-total', row).textContent = money(importe(values.total), moneda);
      } catch { incomplete = true; }
    });
    const descuentoTipo = input('tipoDescuento', this.form).value;
    const valor = el<HTMLInputElement>('[name=valorDescuento]',this.form);
    valor.disabled = descuentoTipo === 'SIN_DESCUENTO';
    valor.max = descuentoTipo === 'PORCENTAJE' ? '100' : '99999999999999999.99';
    el('#discount-hint').textContent = descuentoTipo === 'MONTO_FIJO' ? `Monto en ${moneda || 'la moneda de la factura'}, antes de IVA.` : 'Porcentaje de 0 a 100. Se aplica antes de IVA.';
    valor.setCustomValidity('');
    let subtotal = neto, descuento = 0n;
    try {
      const resumen = descontar(bases, descuentoTipo, valor.value || '0');
      ({ neto, iva, total, subtotal } = resumen); descuento = resumen.descuento;
      this.form.querySelectorAll<HTMLTableRowElement>('#form-lines tr').forEach((row,i) => {
        if (this.lines[i].articuloId && bases[i]) el('.line-total',row).textContent = money(importe(resumen.detalles[i].total),moneda);
      });
    } catch (e) {
      if (!incomplete) valor.setCustomValidity(e instanceof Error ? e.message : 'Revisá el descuento.');
      incomplete = true;
    }
    el('#price-warning').hidden = !missing;
    el('#preview-net').textContent = money(importe(neto), moneda); el('#preview-tax').textContent = money(importe(iva), moneda);
    el('#preview-subtotal').textContent = money(importe(subtotal),moneda);
    el('#preview-discount').textContent = money(importe(descuento),moneda);
    el('#preview-total').textContent = incomplete ? 'Completá los datos' : money(importe(total), moneda);
  }
  payload(): FacturaRequestDTO | null {
    if (!this.lines.length) { this.mostrarError(new Error('Agregá al menos un artículo para crear la factura.')); return null; }
    if (this.pending) { this.mostrarError(new Error('Esperá a que termine la consulta de los datos seleccionados.')); return null; }
    this.readLines(); this.preview();
    if (!this.form.reportValidity()) return null;
    const dto: FacturaRequestDTO = {
      fecha: input('fecha', this.form).value, clienteId: Number(input('clienteId', this.form).value),
      puntoVentaId: Number(input('puntoVentaId', this.form).value), condicionIvaId: Number(input('condicionIvaId', this.form).value),
      tipoMonedaId: Number(input('tipoMonedaId', this.form).value), observaciones: input('observaciones', this.form).value,
      condicionVenta: input('condicionVenta', this.form).value,
      listaPrecioId: Number(input('listaPrecioId', this.form).value),
      tipoDescuento: input('tipoDescuento',this.form).value as FacturaRequestDTO['tipoDescuento'],
      valorDescuento: Number(input('valorDescuento',this.form).value),
      detalles: this.lines.map(({ articuloId, cantidad }) => ({ articuloId, cantidad }))
    };
    const tipo = input('tipoComprobante', this.form).value;
    if (esFiscal(tipo)) {
      dto.tipoComprobante = tipo;
    }
    validarFactura(dto); return dto;
  }
}
