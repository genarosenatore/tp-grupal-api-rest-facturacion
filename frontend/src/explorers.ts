import type { CatalogosDTO, ClienteDTO } from './models/catalogos.dto';
import type { InvoiceForm } from './invoice-form';
import type { ProductoReporteDTO } from './models/producto.dto';
import { ProductForm } from './product-form';
import { ClientForm } from './client-form';
import { ChangeListForm } from './change-list-form';
import { PriceListForm } from './price-list-form';
import { buscarClientes, buscarPrecios, obtenerCatalogos, incorporarProducto, obtenerCliente } from './services/CatalogoService';
import { descripcionCondicion } from './utils/fiscal';
import { el, input, opciones } from './utils/dom';
import { escape, money, mensaje } from './utils/formatters';

const normal = (text: string): string => text.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLocaleLowerCase('es').trim();
export function filtrarClientes(c: CatalogosDTO, q: string, documento: string) {
  return c.clientes.filter(cliente => normal(`${cliente.nombre} ${cliente.documento}`).includes(normal(q)) && normal(cliente.documento).includes(normal(documento)));
}
export function filtrarPrecios(c: CatalogosDTO, q: string, moneda: string, iva: string, lista = '') {
  return c.precios.flatMap(precio => {
    const articulo = c.articulos.find(a => a.id === precio.articuloId);
    const currency = c.monedas.find(m => m.id === precio.tipoMonedaId);
    return articulo && currency && normal(`${articulo.codigo} ${articulo.descripcion}`).includes(normal(q))
      && (!moneda || precio.tipoMonedaId === Number(moneda)) && (iva === '' || articulo.alicuotaIva === Number(iva))
      && (!lista || precio.listaPrecioId === Number(lista))
      ? [{ ...precio, articulo, moneda: currency.codigo, lista: c.listasPrecio.find(l => l.id === precio.listaPrecioId)?.nombre ?? '' }] : [];
  });
}

type Explorer = 'client' | 'price';
export class Explorers {
  private versions = { client: 0, price: 0 };
  private selection = 0;
  private selecting = false;
  private targetLine?: number;
  private openVersion = 0;
  private adminMode = false;
  private adminFromPicker = false;
  private clientTarget: 'invoice' | 'filter' = 'invoice';
  private clientQueries = { invoice: { q:'', documento:'' }, filter: { q:'', documento:'' } };
  private appliedSearch: Partial<Record<Explorer,string>> = {};
  private searchStatus: Partial<Record<Explorer,string>> = {};
  constructor(private catalogo: () => CatalogosDTO, private editor: () => InvoiceForm, creado: (p: ProductoReporteDTO) => void, private actualizado: (c: CatalogosDTO) => void,
      private clienteFiltrado: (cliente: ClienteDTO | null) => void) {
    new ChangeListForm(catalogo,editor);
    const clientes = new ClientForm(cliente => {
      actualizado({ ...catalogo(), clientes: [...catalogo().clientes.filter(c => c.id !== cliente.id), cliente] });
      input('q', el('#client-filters')).value = cliente.documento;
      input('documento', el('#client-filters')).value = '';
      if (input('clienteId',this.editor().form).value === String(cliente.id)) void this.editor().seleccionarCliente(cliente.id).catch(e => { el('#client-status').textContent = mensaje(e); });
      void this.render('client');
    });
    el('#new-client').addEventListener('click', () => { void clientes.abrir(); });
    const listas = new PriceListForm(catalogo, actualizado, lista => {
      const f = el('#price-filters');
      opciones(el('[name=listaPrecioId]', f), catalogo().listasPrecio, l => l.nombre, this.adminMode ? 'Todas las listas' : 'Seleccioná una lista');
      input('listaPrecioId', f).value = String(lista); input('q', f).value = ''; input('iva', f).value = '';
      void this.render('price');
    });
    el('#new-price-list').addEventListener('click', () => listas.abrirLista());
    el('#set-price').addEventListener('click', () => listas.abrirPrecio(input('listaPrecioId', el('#price-filters')).value, input('monedaId', el('#price-filters')).value));
    const productos = new ProductForm(catalogo, producto => {
      creado(producto);
      const f = el('#price-filters'); input('q', f).value = producto.codigo;
      input('monedaId', f).value = String(producto.tipoMonedaId); input('iva', f).value = String(producto.alicuotaIva);
      input('listaPrecioId', f).value = String(producto.listaPrecioId ?? 1);
      void this.render('price').then(() => { el('#price-status').textContent += ` Producto ${producto.codigo} creado.`; });
    });
    el('#new-product').addEventListener('click', () => productos.abrir(
      input('tipoMonedaId', this.editor().form).value || input('monedaId', el('#price-filters')).value,
      input('listaPrecioId', el('#price-filters')).value || input('listaPrecioId', this.editor().form).value));
    for (const tipo of ['client','price'] as const) {
      const form = el<HTMLFormElement>(`#${tipo}-filters`);
      form.addEventListener('submit', event => {
        event.preventDefault();
        if (tipo === 'client' && !input('q',form).value.trim() && !input('documento',form).value.trim()) {
          el('#client-status').textContent = 'Completá nombre o documento para buscar, o elegí Ver todos los clientes.'; return;
        }
        void this.render(tipo);
      });
      form.addEventListener('reset', () => {
        const lista = tipo === 'price' && !this.adminMode ? input('listaPrecioId',form).value : null;
        this.versions[tipo]++; this.selection++;
        setTimeout(() => {
          if (lista !== null) input('listaPrecioId',form).value = lista;
          if (el<HTMLDialogElement>(`#${tipo}-dialog`).open) {
            if (tipo === 'client') this.prepararBusqueda(tipo); else void this.render(tipo);
          }
        }, 0);
      });
      form.addEventListener('input', () => this.prepararBusqueda(tipo));
      el(`#${tipo}-dialog`).addEventListener('close', () => {
        if (tipo === 'client') this.clientQueries[this.clientTarget] = { q:input('q',form).value, documento:input('documento',form).value };
        this.versions[tipo]++; this.selection++; this.openVersion++;
      });
    }
    this.editor().form.addEventListener('abrir-clientes', () => this.abrirClientes('invoice'));
    el('#filters').addEventListener('abrir-clientes', () => this.abrirClientes('filter'));
    el('#show-all-clients').addEventListener('click', () => {
      const form = el('#client-filters'); input('q',form).value = ''; input('documento',form).value = '';
      this.selection++; void this.render('client');
    });
    el('#all-clients').addEventListener('click', () => {
      if (this.clientTarget === 'filter') void this.elegir('client', el<HTMLButtonElement>('#all-clients'), async vigente => { if (vigente()) this.clienteFiltrado(null); });
    });
    el('#add-line').addEventListener('click', () => { void this.abrirPrecios(); });
    el('#manage-catalog').addEventListener('click', () => { void this.abrirPrecios(undefined,true); });
    el('#manage-from-picker').addEventListener('click', () => { this.adminFromPicker = true; this.modoCatalogo(true); void this.cargarCatalogosPrecios(false); });
    el('#back-to-selection').addEventListener('click', () => {
      this.openVersion++; this.modoCatalogo(false);
      const selector = el<HTMLSelectElement>('#price-filters [name=listaPrecioId]'), actual = selector.value;
      opciones(selector, this.catalogo().listasPrecio, l => l.nombre, 'Seleccioná una lista'); selector.value = actual;
      this.lockPriceFilters(false); this.prepararBusqueda('price');
    });
    this.editor().form.addEventListener('abrir-precios', event => { void this.abrirPrecios((event as CustomEvent<number>).detail); });
    el('#client-rows').addEventListener('click', event => {
      const edit = event.target instanceof Element ? event.target.closest<HTMLElement>('[data-edit-client]') : null;
      if (edit && this.clientTarget === 'invoice') { void clientes.abrir(Number(edit.dataset.editClient)); return; }
      const target = event.target instanceof Element ? event.target.closest<HTMLButtonElement>('[data-client]') : null;
      if (target) void this.elegir('client', target, this.clientTarget === 'invoice'
        ? vigente => this.editor().seleccionarCliente(Number(target.dataset.client), vigente)
        : async vigente => {
          const cliente = await obtenerCliente(Number(target.dataset.client));
          if (!vigente()) return;
          this.actualizado({ ...this.catalogo(), clientes:[...this.catalogo().clientes.filter(c => c.id !== cliente.id),cliente] });
          this.clienteFiltrado(cliente);
        });
    });
    el('#price-rows').addEventListener('click', event => {
      const edit = event.target instanceof Element ? event.target.closest<HTMLElement>('[data-edit-price]') : null;
      if (edit && this.adminMode) { listas.abrirPrecio(edit.dataset.list!, edit.dataset.currency!, edit.dataset.editPrice); return; }
      const target = event.target instanceof Element ? event.target.closest<HTMLButtonElement>('[data-price-article]') : null;
      if (target && !target.disabled && !this.adminMode) void this.elegir('price', target, vigente => this.editor().seleccionarPrecio(Number(target.dataset.priceArticle), Number(target.dataset.priceCurrency), Number(target.dataset.priceList), this.targetLine, vigente));
    });
  }
  private abrirClientes(destino: 'invoice' | 'filter'): void {
    const dialog = el<HTMLDialogElement>('#client-dialog'); if (dialog.open) return;
    this.clientTarget = destino;
    const consulta = this.clientQueries[destino], form = el('#client-filters');
    input('q', form).value = consulta.q; input('documento', form).value = consulta.documento;
    el('#new-client').hidden = destino === 'filter'; el('#all-clients').hidden = destino !== 'filter';
    el('#client-context').textContent = destino === 'filter' ? 'Elegí un cliente para consultar sus facturas. Después aplicá el filtro con Buscar en la pantalla principal.' : 'Seleccioná el cliente para esta factura.';
    dialog.showModal(); input('q', form).focus(); this.prepararBusqueda('client',true);
  }
  private async elegir(tipo: Explorer, boton: HTMLButtonElement, action: (vigente: () => boolean) => Promise<void>): Promise<void> {
    if (this.selecting) return;
    this.versions[tipo]++;
    this.selecting = true; const epoch = ++this.selection; boton.disabled = true;
    try {
      await action(() => epoch === this.selection && el<HTMLDialogElement>(`#${tipo}-dialog`).open);
      if (epoch === this.selection) {
        el<HTMLDialogElement>(`#${tipo}-dialog`).close();
        if (tipo === 'price') this.editor().enfocarCantidad();
      }
    } catch (e) {
      if (epoch === this.selection) {
        if (tipo === 'price') {
          const alerta = el('#price-alert'); alerta.textContent = mensaje(e); alerta.hidden = false;
          alerta.scrollIntoView({ block: 'nearest' }); alerta.focus();
        } else el('#client-status').textContent = mensaje(e);
      }
    }
    finally {
      this.selecting = false;
      boton.disabled = !!boton.closest(`#${tipo}-rows`) && this.appliedSearch[tipo] !== this.firmaBusqueda(tipo);
    }
  }
  private async abrirPrecios(index?: number, administrar = false): Promise<void> {
    this.targetLine = index;
    const dialog = el<HTMLDialogElement>('#price-dialog'); if (dialog.open) return;
    this.adminFromPicker = false; this.modoCatalogo(administrar);
    dialog.showModal();
    await this.cargarCatalogosPrecios(true);
  }
  private async cargarCatalogosPrecios(reiniciar: boolean): Promise<void> {
    const dialog = el<HTMLDialogElement>('#price-dialog'), version = ++this.openVersion, admin = this.adminMode;
    const f = el<HTMLFormElement>('#price-filters'), listaAnterior = input('listaPrecioId', f).value, monedaAnterior = input('monedaId', f).value;
    if (reiniciar) { input('q', f).value = ''; input('iva', f).value = ''; }
    this.lockPriceFilters(true);
    el('#price-alert').hidden = true;
    this.contextoPrecios();
    el('#price-status').textContent = 'Consultando las listas de precios…'; el('#price-rows').replaceChildren(); el('#price-results').hidden = true;
    try {
      const nuevos = await obtenerCatalogos(admin);
      if (!dialog.open || version !== this.openVersion) return;
      const catalogo = admin ? nuevos : { ...nuevos, articulos: this.catalogo().articulos, precios: this.catalogo().precios };
      this.actualizado(catalogo);
      opciones(el('[name=monedaId]', f), catalogo.monedas, m => m.codigo, 'Todas');
      input('monedaId', f).value = reiniciar ? input('tipoMonedaId', this.editor().form).value : monedaAnterior;
      opciones(el('[name=listaPrecioId]', f), catalogo.listasPrecio, l => l.nombre, admin ? 'Todas las listas' : 'Seleccioná una lista');
      input('listaPrecioId', f).value = reiniciar ? input('listaPrecioId', this.editor().form).value : listaAnterior;
      this.contextoPrecios();
      this.lockPriceFilters(false);
      this.prepararBusqueda('price',true);
    } catch (e) { if (dialog.open && version === this.openVersion) el('#price-status').textContent = mensaje(e); }
    finally { if (version === this.openVersion) this.lockPriceFilters(false); }
  }
  private modoCatalogo(admin: boolean): void {
    this.adminMode = admin; this.selection++; this.versions.price++;
    el('#catalog-actions').hidden = !admin; el('#manage-from-picker').hidden = admin;
    el('#back-to-selection').hidden = !admin || !this.adminFromPicker;
    el('#price-title').textContent = admin ? 'Administrar catálogo' : this.targetLine === undefined ? 'Agregar artículo' : 'Cambiar artículo';
    el('#price-alert').hidden = true;
    el('#price-rows').replaceChildren(); el('#price-results').hidden = true;
    this.contextoPrecios();
  }
  private lockPriceFilters(busy: boolean): void {
    el('#price-filters').querySelectorAll<HTMLInputElement | HTMLSelectElement | HTMLButtonElement>('input,select,button').forEach(c => { c.disabled = busy; });
    if (!busy) this.habilitarFiltrosPrecios();
  }
  private habilitarFiltrosPrecios(): void {
    const form = el('#price-filters'), sinLista = !this.adminMode && !input('listaPrecioId', form).value;
    form.querySelectorAll<HTMLInputElement | HTMLSelectElement | HTMLButtonElement>('[name=q],[name=monedaId],[name=iva],button:not([type]),button[type=submit]').forEach(c => { c.disabled = sinLista; });
  }
  private contextoPrecios(): void {
    el('#price-context').textContent = this.adminMode ? 'Creá productos y listas o actualizá sus precios. Las facturas guardadas conservan sus importes.' : this.editor().listaSeleccionada
      ? `Esta factura usa «${this.editor().nombreListaSeleccionada}». Todos sus artículos deben pertenecer a esa lista.`
      : 'Primero seleccioná una lista de precios. Después elegí uno de sus artículos.';
  }
  private firmaBusqueda(tipo: Explorer): string {
    const form = el(`#${tipo}-filters`), nombres = tipo === 'client' ? ['q','documento'] : ['q','listaPrecioId','monedaId','iva'];
    return JSON.stringify(nombres.map(n => input(n,form).value));
  }
  private prepararBusqueda(tipo: Explorer, nueva = false): void {
    this.versions[tipo]++; this.selection++;
    const filas = el(`#${tipo}-rows`);
    if (nueva) { filas.replaceChildren(); delete this.appliedSearch[tipo]; }
    const pendientes = this.appliedSearch[tipo] !== this.firmaBusqueda(tipo), hayResultados = !!filas.children.length;
    filas.classList.toggle('search-results-pending',pendientes && hayResultados);
    filas.querySelectorAll<HTMLButtonElement>('button').forEach(b => { b.disabled = pendientes; });
    if (tipo === 'price') {
      el('#price-results').hidden = !hayResultados; el('#price-alert').hidden = true; this.habilitarFiltrosPrecios();
      el('#price-status').textContent = !this.adminMode && !input('listaPrecioId',el('#price-filters')).value
        ? 'Seleccioná una lista de precios y presioná Buscar para consultar sus artículos.'
        : 'Presioná Buscar o Enter para consultar los artículos con estos filtros.';
    } else el('#client-status').textContent = 'Buscá por nombre o documento, o elegí Ver todos los clientes.';
    if (hayResultados) el(`#${tipo}-status`).textContent = pendientes
      ? tipo === 'price' && !this.adminMode && !input('listaPrecioId',el('#price-filters')).value
        ? 'Seleccioná una lista de precios y presioná Buscar para actualizar. Se muestran los resultados anteriores.'
        : 'Filtros modificados. Se muestran los resultados anteriores; presioná Buscar para actualizarlos.'
      : this.searchStatus[tipo] ?? '';
  }
  private async render(tipo: Explorer): Promise<void> {
    const version = ++this.versions[tipo], form = el(`#${tipo}-filters`);
    el(`#${tipo}-status`).textContent = 'Consultando la base de datos…';
    el(`#${tipo}-rows`).replaceChildren();
    el(`#${tipo}-rows`).classList.remove('search-results-pending'); delete this.appliedSearch[tipo];
    if (tipo === 'price') {
      el('#price-alert').hidden = true; el('#price-results').hidden = true; this.contextoPrecios(); this.habilitarFiltrosPrecios();
      if (!this.adminMode && !input('listaPrecioId',form).value) {
        el('#price-status').textContent = 'Seleccioná una lista de precios para consultar sus artículos.'; return;
      }
    }
    try {
      if (tipo === 'client') {
        const rows = await buscarClientes(input('q',form).value, input('documento',form).value);
        if (version !== this.versions[tipo]) return;
        el('#client-rows').innerHTML = rows.map(c => `<tr><td data-label="Nombre">${escape(c.nombre)}</td><td data-label="Documento">${escape(c.documento)}</td><td data-label="Condición fiscal">${escape(descripcionCondicion(c.condicionIvaReceptorId))}</td><td><button type="button" class="button secondary" data-client="${c.id}">Seleccionar</button>${this.clientTarget === 'invoice' ? `<button type="button" class="button secondary" data-edit-client="${c.id}">Editar ficha</button>` : ''}</td></tr>`).join('');
        el('#client-status').textContent = rows.length ? `${rows.length} clientes (máximo 100).` : 'No hay clientes para estos filtros.';
      } else {
        const rows = await buscarPrecios(input('q',form).value, input('listaPrecioId',form).value, input('monedaId',form).value, input('iva',form).value);
        if (version !== this.versions[tipo]) return;
        if (!this.adminMode) {
          let actualizado = this.catalogo();
          for (const precio of rows) actualizado = incorporarProducto(actualizado, precio);
          this.actualizado(actualizado);
        }
        const c = this.catalogo(), moneda = Number(input('tipoMonedaId',this.editor().form).value), lista = Number(input('listaPrecioId',this.editor().form).value);
        el('#price-rows').innerHTML = rows.map(p => {
          const currency = c.monedas.find(m => m.id === p.tipoMonedaId)?.codigo ?? '', nombre = c.listasPrecio.find(l => l.id === p.listaPrecioId)?.nombre ?? '';
          const otraLista = !this.adminMode && !!lista && lista !== p.listaPrecioId;
          const aviso = otraLista ? 'Este precio pertenece a otra lista' : moneda && moneda !== p.tipoMonedaId ? 'Este precio está en otra moneda' : '';
          const accion = this.adminMode
            ? `<button type="button" class="button secondary" data-edit-price="${p.id}" data-list="${p.listaPrecioId}" data-currency="${p.tipoMonedaId}">Editar precio</button>`
            : `<button type="button" class="button primary" data-price-article="${p.id}" data-price-currency="${p.tipoMonedaId}" data-price-list="${p.listaPrecioId}" ${aviso ? `title="${aviso}"` : ''}>${this.targetLine === undefined ? 'Agregar' : 'Elegir'}</button>`;
          return `<tr><td data-label="Código">${escape(p.codigo)}</td><td data-label="Artículo">${escape(p.descripcion)}</td><td data-label="Lista">${escape(nombre)}${otraLista ? '<span class="cell-sub">Otra lista</span>' : ''}</td><td data-label="Moneda">${escape(currency)}</td><td data-label="Precio">${escape(money(p.precio,currency))}</td><td data-label="IVA">${p.alicuotaIva} %</td><td>${accion}</td></tr>`;
        }).join('');
        el('#price-status').textContent = rows.length ? `${rows.length} precios encontrados (máximo 100).` : this.adminMode ? 'No hay precios para estos filtros. Usá Cargar precio para agregar uno.' : 'No hay artículos con precio para estos filtros. Probá otra búsqueda o revisá el catálogo.';
        el('#price-results').hidden = !rows.length;
      }
      this.appliedSearch[tipo] = this.firmaBusqueda(tipo);
      this.searchStatus[tipo] = el(`#${tipo}-status`).textContent ?? '';
    } catch (e) { if (version === this.versions[tipo]) el(`#${tipo}-status`).textContent = mensaje(e); }
  }
}
