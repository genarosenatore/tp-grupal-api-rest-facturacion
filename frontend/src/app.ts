import type { CatalogosDTO } from './models/catalogos.dto';
import type { EstadoFactura, FacturaFiltrosDTO, FacturaReporteDTO } from './models/factura.dto';
import { FacturaService } from './services/FacturaService';
import { obtenerCatalogos, incorporarProducto, consultarPrecios } from './services/CatalogoService';
import { InvoiceForm } from './invoice-form';
import { el, input, opciones } from './utils/dom';
import { escape, fecha, money, referencia, mensaje } from './utils/formatters';
import { fechaValida } from './utils/validators';
import { Explorers } from './explorers';
import { iniciarAccionesFactura } from './invoice-actions';
import { API_BASE } from './services/http';
import { esFiscal, descripcionCondicion } from './utils/fiscal';

const servicio = new FacturaService();
let catalogos: CatalogosDTO;
let editor: InvoiceForm | undefined;
let invoices: FacturaReporteDTO[] = [], selected: FacturaReporteDTO | null = null;
let page = 0, total = 0, pages = 0, busy = false, loading = false, queryVersion = 0, detailVersion = 0;
let activeFilters: FacturaFiltrosDTO = {};
const pageSize = 8;
const dialog = (id: string): HTMLDialogElement => el(`#${id}`);
const error = (id: string, value: unknown): void => { el(id).textContent = mensaje(value); el(id).hidden = false; };
const notify = (text: string): void => { el('#notice').textContent = text; el('#notice').hidden = false; };

/** Promesa de confirmación propia: teclado, Escape y foco gestionados por dialog. */
function confirmar(text: string): Promise<boolean> {
  el('#confirm-description').textContent = text;
  const d = dialog('confirm-dialog'); d.returnValue = ''; d.showModal();
  return new Promise(resolve => d.addEventListener('close', () => resolve(d.returnValue === 'yes'), { once: true }));
}
function connection(ok: boolean): void { el('#connection').textContent = ok ? '● API conectada' : '● Sin conexión'; el('#connection').classList.toggle('offline', !ok); }

function readFilters(): FacturaFiltrosDTO | null {
  const f = el<HTMLFormElement>('#filters');
  const desde = input('desde', f).value, hasta = input('hasta', f).value;
  if ((desde && !fechaValida(desde)) || (hasta && !fechaValida(hasta)) || (desde && hasta && desde > hasta)) { error('#global-error', new Error('Revisá las fechas: desde debe ser anterior o igual a hasta.')); return null; }
  if (!f.reportValidity()) return null;
  const filters: FacturaFiltrosDTO = {};
  if (input('clienteId', f).value) filters.clienteId = Number(input('clienteId', f).value);
  if (input('listaPrecioId', f).value) filters.listaPrecioId = Number(input('listaPrecioId', f).value);
  if (input('estado', f).value) filters.estado = input('estado', f).value as EstadoFactura;
  if (desde) filters.desde = desde; if (hasta) filters.hasta = hasta;
  if (input('numero', f).value) filters.numero = Number(input('numero', f).value);
  if (input('informadaArca', f).value) filters.informadaArca = input('informadaArca', f).value === 'true';
  return filters;
}
async function search(): Promise<void> {
  const filters = readFilters(); if (!filters) return;
  activeFilters = filters; page = 0; await loadInvoices();
}
function actualizarFiltrosPendientes(): void {
  const nombres = ['numero','clienteId','listaPrecioId','estado','informadaArca','desde','hasta'] as const;
  const pendientes = nombres.some(n => input(n,el('#filters')).value !== String(activeFilters[n] ?? ''));
  el('#filter-instructions').textContent = pendientes ? 'Hay cambios sin aplicar. Presioná Buscar o Enter.' : 'Completá los filtros y presioná Buscar o Enter.';
  el('#filter-instructions').classList.toggle('search-pending',pendientes);
  for (const boton of el('#filter-chips').querySelectorAll<HTMLButtonElement>('[data-remove-filter]')) {
    const nombre = boton.dataset.removeFilter as typeof nombres[number];
    const cambiado = input(nombre,el('#filters')).value !== String(activeFilters[nombre] ?? '');
    boton.classList.toggle('filter-chip-pending',cambiado); boton.disabled = cambiado;
    boton.title = cambiado ? 'Cambio pendiente: presioná Buscar para aplicarlo.' : 'Quitar este filtro y preparar la búsqueda.';
  }
}
function renderFiltrosAplicados(): void {
  const f = activeFilters, filtros: [string,string][] = [];
  if (f.numero) filtros.push(['numero', `Factura n.º ${f.numero}`]);
  if (f.clienteId) filtros.push(['clienteId', `Cliente: ${catalogos.clientes.find(c => c.id === f.clienteId)?.nombre ?? f.clienteId}`]);
  if (f.listaPrecioId) filtros.push(['listaPrecioId', `Lista: ${catalogos.listasPrecio.find(l => l.id === f.listaPrecioId)?.nombre ?? f.listaPrecioId}`]);
  if (f.estado) filtros.push(['estado', f.estado === 'ANULADA' ? 'Anuladas' : 'Emitidas']);
  if (f.informadaArca !== undefined) filtros.push(['informadaArca', f.informadaArca ? 'Informadas a ARCA' : 'No informadas a ARCA']);
  if (f.desde) filtros.push(['desde', `Desde: ${fecha(f.desde)}`]);
  if (f.hasta) filtros.push(['hasta', `Hasta: ${fecha(f.hasta)}`]);
  el('#filter-summary').textContent = filtros.length ? `${filtros.length} ${filtros.length === 1 ? 'filtro aplicado' : 'filtros aplicados'}` : 'Todas las facturas';
  el('#filter-chips').replaceChildren(...filtros.map(([name, descripcion]) => {
    const button = document.createElement('button'); button.type = 'button'; button.className = 'filter-chip';
    button.dataset.removeFilter = name; button.setAttribute('aria-label', `Quitar filtro: ${descripcion}`);
    const texto = document.createElement('span'); texto.textContent = descripcion;
    const quitar = document.createElement('span'); quitar.textContent = '×'; quitar.setAttribute('aria-hidden','true');
    button.append(texto, quitar); return button;
  }));
  actualizarFiltrosPendientes();
}
async function loadInvoices(): Promise<void> {
  const version = ++queryVersion; loading = true;
  renderFiltrosAplicados(); el('#filters').setAttribute('aria-busy','true');
  el('#global-error').hidden = true; el('#table-status').hidden = false; el('#table-status').textContent = 'Cargando facturas…'; el('#invoice-table-wrap').hidden = true;
  el<HTMLButtonElement>('#search-button').disabled = true;
  el('#search-button-label').textContent = 'Buscando…';
  el<HTMLButtonElement>('#previous').disabled = true; el<HTMLButtonElement>('#next').disabled = true;
  try {
    const response = await servicio.obtenerPaginaFacturas({ ...activeFilters, page, size: pageSize });
    if (version !== queryVersion) return;
    invoices = response.content; total = response.totalElements; pages = response.totalPages; page = response.page;
    if (!invoices.length && page > 0 && page >= pages) { page = Math.max(0, pages - 1); await loadInvoices(); return; }
    connection(true); renderInvoices();
  } catch (e) {
    if (version !== queryVersion) return;
    connection(false); error('#global-error', e); el('#table-status').textContent = 'No se pudo actualizar el listado. Intentá nuevamente.';
    el('#page-info').textContent = 'Consulta sin actualizar'; el('#result-count').textContent = '—';
    el<HTMLButtonElement>('#previous').disabled = true; el<HTMLButtonElement>('#next').disabled = true;
  } finally { if (version === queryVersion) { loading = false; el<HTMLButtonElement>('#search-button').disabled = false; el('#search-button-label').textContent = 'Buscar'; el('#filters').setAttribute('aria-busy','false'); } }
}
function puedeAnular(f: FacturaReporteDTO): boolean {
  return f.estado === 'EMITIDA' && !['EN_PROCESO', 'INCIERTA'].includes(f.estadoArca)
    && (!f.informadaArca || catalogos.homologacionHabilitada);
}
function estadoFiscal(f: FacturaReporteDTO): string {
  if (f.informadaArca) return 'Informada a ARCA';
  if (f.estado === 'ANULADA') return 'Anulada sin autorización fiscal';
  if (!f.tipoComprobante) return 'Comprobante X · Sin envío fiscal';
  const leyendas = { PENDIENTE: 'Pendiente de autorización', RECHAZADA: 'Rechazada por ARCA',
    EN_PROCESO: 'Autorización en proceso', INCIERTA: 'Requiere conciliación', AUTORIZADA: 'Autorizada' };
  return leyendas[f.estadoArca];
}
function renderInvoices(): void {
  const start = page * pageSize;
  el('#invoice-rows').innerHTML = invoices.map(f => `<tr>
    <td data-label="Comprobante"><strong>${f.numero === null ? 'Sin número' : escape(f.numero)}</strong><span class="cell-sub">ID ${f.id} · ${fecha(f.fecha)}</span><span class="cell-sub">${f.letraComprobante === 'X' ? 'Comprobante X' : `Factura ${escape(f.letraComprobante)} · Homologación`}</span></td>
    <td data-label="Cliente">${escape(f.clienteNombre)}</td><td data-label="Neto" class="numeric">${escape(money(f.neto, f.moneda))}</td>
    <td data-label="IVA" class="numeric">${escape(money(f.iva, f.moneda))}</td><td data-label="Total" class="numeric amount">${escape(money(f.total, f.moneda))}</td>
    <td data-label="Estado"><span class="badge ${f.estado === 'ANULADA' ? 'void' : ''}">${f.estado === 'ANULADA' ? 'Anulada' : 'Emitida'}</span><span class="cell-sub">${estadoFiscal(f)}</span></td>
    <td class="row-actions"><div class="row-action-buttons"><button type="button" class="button secondary compact" data-detail="${f.id}">Consultar</button>
    <button type="button" class="button secondary compact" data-actions="${f.id}" popovertarget="invoice-actions-${f.id}" aria-haspopup="menu" aria-expanded="false" aria-controls="invoice-actions-${f.id}">Acciones <svg class="menu-arrow" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true" focusable="false"><path d="m4 6 4 4 4-4"/></svg></button></div>
    <div id="invoice-actions-${f.id}" class="invoice-action-menu" popover="auto" role="menu" aria-label="Acciones de la factura ID ${f.id}">
      <a class="action-menu-item" role="menuitem" href="${API_BASE}/facturas/${f.id}/pdf" download>Descargar PDF</a>
      ${f.editable ? `<button type="button" class="action-menu-item" role="menuitem" data-edit="${f.id}">Editar</button>` : ''}
      ${puedeAnular(f) || f.eliminable ? `<div class="action-menu-danger" role="group" aria-label="Acciones de anulación y eliminación">
        ${puedeAnular(f) ? `<button type="button" class="action-menu-item" role="menuitem" data-void="${f.id}">Anular</button>` : ''}
        ${f.eliminable ? `<button type="button" class="action-menu-item" role="menuitem" data-delete="${f.id}">Eliminar borrador</button>` : ''}</div>` : ''}
    </div></td></tr>`).join('');
  el('#result-count').textContent = String(total); el('#invoice-table-wrap').hidden = !invoices.length;
  el('#table-status').hidden = !!invoices.length; el('#table-status').textContent = 'No hay facturas para estos filtros. Limpiá la búsqueda o registrá una nueva.';
  el('#page-info').textContent = total ? `${start + 1}–${start + invoices.length} de ${total} comprobantes` : '0 comprobantes';
  el('#page-number').textContent = `${page + 1} / ${Math.max(1, pages)}`;
  el<HTMLButtonElement>('#previous').disabled = page <= 0; el<HTMLButtonElement>('#next').disabled = page + 1 >= pages;
}
function actualizarListasFiltro(): void {
  const select = el<HTMLSelectElement>('#filter-price-list'), actual = select.value;
  opciones(select, catalogos.listasPrecio, l => l.nombre, 'Todas las listas'); select.value = actual;
}
async function bootstrap(): Promise<void> {
  el<HTMLButtonElement>('#reload').disabled = true; el<HTMLButtonElement>('#new-invoice').disabled = true; el<HTMLSelectElement>('#filter-client').disabled = true;
  el('#global-error').hidden = true;
  el('#table-status').hidden = false; el('#table-status').textContent = 'Cargando datos…';
  el('#invoice-table-wrap').hidden = true;
  try {
    const nuevos = await obtenerCatalogos(false);
    catalogos = { ...nuevos, articulos: catalogos?.articulos ?? [], precios: catalogos?.precios ?? [] }; actualizarListasFiltro();
    const current = el<HTMLSelectElement>('#filter-client').value;
    opciones(el('#filter-client'), catalogos.clientes, c => c.nombre, 'Todos los clientes');
    el<HTMLSelectElement>('#filter-client').value = current;
    // No reinstalar listeners ni perder un borrador durante una actualización.
    if (!editor) { editor = new InvoiceForm(catalogos); new Explorers(() => catalogos, () => editor!, producto => {
      catalogos = incorporarProducto(catalogos, producto); editor!.actualizarCatalogos(catalogos);
    }, nuevos => { catalogos = nuevos; editor!.actualizarCatalogos(catalogos); actualizarListasFiltro(); }, cliente => {
      const selector = el<HTMLSelectElement>('#filter-client');
      if (cliente) {
        const opcion = [...selector.options].find(o => o.value === String(cliente.id));
        if (opcion) opcion.textContent = cliente.nombre; else selector.add(new Option(cliente.nombre,String(cliente.id)));
      }
      selector.value = cliente ? String(cliente.id) : ''; actualizarFiltrosPendientes();
    }); }
    else {
      editor.actualizarCatalogos(catalogos);
      if (!editor.dirty && editor.editing === null) editor.reset();
    }
    el<HTMLButtonElement>('#manage-catalog').disabled = false;
    if (![catalogos.clientes, catalogos.puntosVenta, catalogos.condicionesIva, catalogos.monedas].every(a => a.length)) {
      editor.lock(true); throw new Error('Faltan clientes, puntos de venta, condiciones de IVA o monedas. Completá los catálogos para registrar una factura.');
    }
    editor.lock(false); el<HTMLButtonElement>('#new-invoice').disabled = false; el<HTMLSelectElement>('#filter-client').disabled = false;
    connection(true); await loadInvoices();
  } catch (e) { connection(false); error('#global-error', e); el('#table-status').textContent = 'No se pudieron cargar los datos. Usá Actualizar datos para reintentar.'; el('#page-info').textContent = 'Consulta sin actualizar'; el('#result-count').textContent = '—'; }
  finally { el<HTMLButtonElement>('#reload').disabled = false; }
}

async function saveInvoice(event: SubmitEvent): Promise<void> {
  event.preventDefault(); if (!editor || busy) return;
  el('#form-error').hidden = true;
  try {
    const payload = editor.payload(); if (!payload) return;
    const id = editor.editing; busy = true; editor.lock(true); el('#save-invoice').textContent = 'Guardando…';
    const saved = id === null ? await servicio.crearFactura(payload) : await servicio.actualizarFactura(id, payload);
    dialog('invoice-dialog').close(); editor.reset(); editor.lock(true);
    notify(`Factura ${referencia(saved.id)} ${id === null ? 'creada' : 'actualizada'}. Total confirmado: ${money(saved.total, saved.moneda)}.`);
    await loadInvoices();
  } catch (e) { error('#form-error', e); el('#form-error').scrollIntoView({ block: 'nearest' }); }
  finally { busy = false; editor.lock(false); el('#save-invoice').textContent = editor.editing === null ? 'Crear factura' : 'Guardar cambios'; }
}
async function showDetail(id: number, action?: 'edit' | 'void' | 'delete'): Promise<void> {
  const version = ++detailVersion; selected = null;
  el('#detail-title').textContent = `Factura ${referencia(id)}`; el('#detail-content').textContent = 'Consultando factura…'; el('#detail-actions').replaceChildren();
  if (!dialog('detail-dialog').open) dialog('detail-dialog').showModal();
  try {
    const f = await servicio.obtenerFactura(id); if (version !== detailVersion || !dialog('detail-dialog').open) return; selected = f;
    const point = catalogos.puntosVenta.find(p => p.id === f.puntoVentaId);
    const condition = catalogos.condicionesIva.find(c => c.id === f.condicionIvaId);
    el('#detail-content').innerHTML = `<dl class="detail-grid">
      <div><dt>Cliente</dt><dd>${escape(f.clienteNombre)}</dd></div><div><dt>Documento</dt><dd>${escape(f.clienteDocumento)}</dd></div>
      <div><dt>Domicilio</dt><dd>${escape(f.clienteDomicilio || 'No informado')}</dd></div>
      <div><dt>Fecha</dt><dd>${fecha(f.fecha)}</dd></div><div><dt>Estado</dt><dd>${escape(f.estado)}</dd></div>
      <div><dt>Punto de venta</dt><dd>${escape(point?.codigo ?? f.puntoVentaId)}</dd></div>
      <div><dt>Condición fiscal</dt><dd>${escape(f.condicionIvaReceptorId !== null ? descripcionCondicion(f.condicionIvaReceptorId) : condition?.descripcion ?? f.condicionIvaId)}</dd></div>
      <div><dt>Moneda</dt><dd>${escape(f.moneda)}</dd></div><div><dt>Lista de precios</dt><dd>${escape(catalogos.listasPrecio.find(l => l.id === f.listaPrecioId)?.nombre ?? f.listaPrecioId)}</dd></div></dl>
      ${f.fechaAnulacion ? `<p class="notice warning">Anulada el ${escape(new Intl.DateTimeFormat('es-AR', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(f.fechaAnulacion)))}</p>` : ''}
      <div class="table-wrap"><table><thead><tr><th>Artículo</th><th>Cantidad</th><th>Precio</th><th>Neto</th><th>IVA</th><th>Total</th></tr></thead><tbody>
      ${f.detalles.map(d => `<tr><td data-label="Artículo">${escape(d.descripcion)}</td><td data-label="Cantidad">${escape(d.cantidad)}</td><td data-label="Precio">${escape(money(d.precioUnitario, f.moneda))}</td><td data-label="Neto">${escape(money(d.neto, f.moneda))}</td><td data-label="IVA">${escape(money(d.iva, f.moneda))} (${escape(d.alicuotaIva)} %)</td><td data-label="Total">${escape(money(d.total, f.moneda))}</td></tr>`).join('')}
      </tbody></table></div><dl class="totals"><div><dt>Neto</dt><dd>${escape(money(f.neto, f.moneda))}</dd></div><div><dt>IVA</dt><dd>${escape(money(f.iva, f.moneda))}</dd></div><div class="grand-total"><dt>Total</dt><dd>${escape(money(f.total, f.moneda))}</dd></div></dl><p class="notes">${escape(f.observaciones || 'Sin observaciones.')}</p>`;
    el('#detail-content').insertAdjacentHTML('afterbegin', `<p class="notice ${f.informadaArca ? '' : 'warning'}">${f.informadaArca ? `Informada a ARCA · ${escape(f.ambienteArca ?? '')} · ${escape(f.tipoComprobante)} ${escape(f.puntoVentaCodigo)}-${escape(f.numero)} · ${escape(f.tipoAutorizacion)} ${escape(f.codigoAutorizacion)} · Vto: ${escape(f.vencimientoAutorizacion)}` : `${estadoFiscal(f)} · Sin validez fiscal`}</p>${f.motivoAnulacion ? `<p class="notes">Motivo de anulación: ${escape(f.motivoAnulacion)}</p>` : ''}`);
    el('#detail-content').insertAdjacentHTML('afterbegin', `<p><strong>${f.letraComprobante === 'X' ? 'Comprobante X · Documento no válido como factura' : `Factura ${escape(f.letraComprobante)}`}</strong></p>`);
    el('#detail-content .totals').insertAdjacentHTML('afterbegin', `<div><dt>Subtotal antes de descuento</dt><dd>${escape(money(f.subtotal,f.moneda))}</dd></div><div><dt>Descuento general${f.tipoDescuento === 'PORCENTAJE' ? ` (${f.valorDescuento} %)` : ''}</dt><dd>${escape(money(f.importeDescuento,f.moneda))}</dd></div>`);
    el('#detail-content').querySelectorAll<HTMLTableRowElement>('tbody tr').forEach((row,i) => {
      if (f.detalles[i].descuento > 0) row.cells[0].append(Object.assign(document.createElement('span'), { className: 'cell-sub', textContent: `Descuento: ${money(f.detalles[i].descuento,f.moneda)}` }));
    });
    if (!catalogos.homologacionHabilitada && (esFiscal(f.tipoComprobante ?? '') || f.informadaArca)) {
      el('#detail-content').insertAdjacentHTML('beforeend', '<p class="hint">El envío a ARCA está deshabilitado en esta instancia. La autorización, la conciliación y el ajuste de una factura autorizada requieren el modo de homologación.</p>');
    }
    el('#detail-actions').innerHTML = `<a class="button secondary" href="${API_BASE}/facturas/${f.id}/pdf" download>Descargar PDF</a>
      ${f.editable ? '<button class="button secondary" id="edit-invoice">Editar factura</button>' : ''}
      ${f.editable && esFiscal(f.tipoComprobante ?? '') && catalogos.homologacionHabilitada ? '<button class="button secondary" id="authorize-invoice">Informar a ARCA</button>' : ''}
      ${f.eliminable ? '<button class="button danger" id="delete-invoice">Eliminar borrador</button>' : ''}
      ${puedeAnular(f) ? '<button class="button danger" id="void-invoice">Anular factura</button>' : ''}
      ${catalogos.homologacionHabilitada && ['EN_PROCESO','INCIERTA'].includes(f.estadoArca) ? '<button class="button secondary" id="reconcile-invoice">Conciliar ARCA</button>' : ''}`;
    if (action === 'edit') await editar();
    if (action === 'void' && puedeAnular(f)) stateForm();
    if (action === 'delete') await eliminar();
  } catch (e) { if (version === detailVersion) el('#detail-content').textContent = mensaje(e); }
}
async function editar(): Promise<void> {
  if (!selected || !selected.editable || !editor || busy) return;
  if (editor.dirty && !await confirmar('Hay datos sin guardar en el formulario. ¿Querés descartarlos para editar esta factura?')) return;
  const factura = selected, version = detailVersion;
  try {
    lockActions(true);
    const precios = await consultarPrecios(factura.detalles.map(d => d.articuloId), factura.listaPrecioId, factura.tipoMonedaId);
    if (version !== detailVersion || !dialog('detail-dialog').open || selected?.id !== factura.id) return;
    for (const precio of precios) catalogos = incorporarProducto(catalogos, precio);
    // Conserva el nombre histórico aunque un artículo ya no tenga precio vigente.
    for (const detalle of factura.detalles) if (!catalogos.articulos.some(a => a.id === detalle.articuloId))
      catalogos.articulos.push({ id: detalle.articuloId, codigo: String(detalle.articuloId), descripcion: detalle.descripcion, alicuotaIva: detalle.alicuotaIva });
    editor.actualizarCatalogos(catalogos);
    editor.reset(factura); dialog('detail-dialog').close(); dialog('invoice-dialog').showModal(); input('clienteId', editor.form).focus();
  } catch (e) { error('#global-error', e); }
  finally { lockActions(false); }
}
async function eliminar(): Promise<void> {
  if (!selected || busy) return;
  const id = selected.id;
  if (!selected.eliminable || !await confirmar(`¿Eliminar definitivamente el borrador ID ${id}? Se eliminarán también sus detalles.`)) return;
  busy = true; lockActions(true);
  try { await servicio.eliminarFactura(id); dialog('detail-dialog').close(); selected = null; notify(`Borrador ID ${id} eliminado.`); await loadInvoices(); }
  catch (e) { error('#global-error', e); el('#detail-content').prepend(Object.assign(document.createElement('p'), { className: 'notice error', textContent: mensaje(e) })); }
  finally { busy = false; lockActions(false); }
}
function lockActions(locked: boolean): void {
  document.querySelectorAll<HTMLButtonElement | HTMLTextAreaElement>('#detail-dialog button, #state-form button, #state-form textarea').forEach(b => { b.disabled = locked; });
}
function stateForm(): void {
  if (!selected || busy) return;
  const form = el<HTMLFormElement>('#state-form'); form.reset();
  el('#state-error').hidden = true; el('#state-description').textContent = `Factura ${referencia(selected.id)}`; dialog('state-dialog').showModal();
}
async function saveState(event: SubmitEvent): Promise<void> {
  event.preventDefault(); if (!selected || busy) return;
  const id = selected.id, form = el<HTMLFormElement>('#state-form');
  if (!form.reportValidity() || !await confirmar('¿Confirmás la anulación? Se conservarán el motivo, el comprobante y sus detalles. Si fue autorizada, se requiere el circuito fiscal de ajuste.')) return;
  busy = true; lockActions(true); el('#state-error').hidden = true;
  try { await servicio.actualizarEstado(id, 'ANULADA', input('motivo', form).value); dialog('state-dialog').close(); notify(`Factura ${referencia(id)} anulada. Se conservó el historial.`); await loadInvoices(); await showDetail(id); }
  catch (e) { error('#state-error', e); }
  finally { busy = false; lockActions(false); }
}

async function fiscalAction(conciliar: boolean): Promise<void> {
  if (!selected || busy) return;
  const id = selected.id;
  if (!conciliar && !await confirmar(`¿Solicitar autorización fiscal de la factura ID ${id}? Se utilizará el circuito ARCA configurado.`)) return;
  busy = true; lockActions(true);
  let failure: unknown;
  try { await (conciliar ? servicio.conciliarFactura(id) : servicio.autorizarFactura(id)); notify('Operación ARCA completada.'); }
  catch (e) { failure = e; }
  finally {
    await loadInvoices(); await showDetail(id);
    if (failure) el('#detail-content').prepend(Object.assign(document.createElement('p'), { className: 'notice error', textContent: mensaje(failure) }));
    busy = false; lockActions(false);
  }
}

export async function iniciar(): Promise<void> {
  iniciarAccionesFactura();
  el<HTMLFormElement>('#invoice-form').addEventListener('submit', event => { void saveInvoice(event); });
  el<HTMLFormElement>('#state-form').addEventListener('submit', event => { void saveState(event); });
  el<HTMLFormElement>('#filters').addEventListener('submit', event => { event.preventDefault(); void search(); });
  el('#filters').addEventListener('input', actualizarFiltrosPendientes);
  el('#filters').addEventListener('change', actualizarFiltrosPendientes);
  const filtroCliente = el<HTMLSelectElement>('#filter-client');
  const abrirFiltroCliente = (): void => { if (!filtroCliente.disabled) { filtroCliente.focus(); el('#filters').dispatchEvent(new CustomEvent('abrir-clientes')); } };
  filtroCliente.addEventListener('pointerdown', event => { event.preventDefault(); abrirFiltroCliente(); });
  filtroCliente.addEventListener('keydown', event => {
    if (['Enter',' ','ArrowDown'].includes(event.key)) { event.preventDefault(); abrirFiltroCliente(); }
  });
  el('#filter-chips').addEventListener('click', event => {
    const button = event.target instanceof Element ? event.target.closest<HTMLButtonElement>('[data-remove-filter]') : null;
    if (button?.dataset.removeFilter) { input(button.dataset.removeFilter, el('#filters')).value = ''; actualizarFiltrosPendientes(); }
  });
  // El evento reset ocurre antes de restaurar los controles: consultar en la tarea siguiente.
  el('#filters').addEventListener('reset', () => { setTimeout(() => { void search(); }, 0); });
  el('#reload').addEventListener('click', () => { if (!busy) void bootstrap(); });
  el('#previous').addEventListener('click', () => { if (!loading && page > 0) { page--; void loadInvoices(); } });
  el('#next').addEventListener('click', () => { if (!loading && page + 1 < pages) { page++; void loadInvoices(); } });
  el('#new-invoice').addEventListener('click', async () => {
    if (busy || !editor) return;
    if (editor.editing !== null) {
      if (editor.dirty && !await confirmar('¿Descartar los cambios pendientes para registrar una nueva factura?')) return;
      editor.reset();
    }
    dialog('invoice-dialog').showModal();
  });
  el('#confirm-yes').addEventListener('click', () => dialog('confirm-dialog').close('yes'));
  el('#confirm-no').addEventListener('click', () => dialog('confirm-dialog').close('no'));
  el('#reset-invoice').addEventListener('click', async () => { if (editor && !busy && (!editor.dirty || await confirmar('¿Descartar los datos sin guardar del formulario?'))) editor.descartarCambios(); });
  document.addEventListener('click', event => {
    if (!(event.target instanceof Element)) return;
    const close = event.target.closest<HTMLElement>('[data-close]'); if (close && !busy) dialog(close.dataset.close!).close();
    const detail = event.target.closest<HTMLElement>('[data-detail]'); if (detail && !busy) void showDetail(Number(detail.dataset.detail));
    const edit = event.target.closest<HTMLElement>('[data-edit]'); if (edit && !busy) void showDetail(Number(edit.dataset.edit), 'edit');
    const anular = event.target.closest<HTMLElement>('[data-void]'); if (anular && !busy) void showDetail(Number(anular.dataset.void), 'void');
    const quitar = event.target.closest<HTMLElement>('[data-delete]'); if (quitar && !busy) void showDetail(Number(quitar.dataset.delete), 'delete');
    if (event.target.closest('#edit-invoice')) void editar();
    if (event.target.closest('#delete-invoice')) void eliminar();
    if (event.target.closest('#void-invoice')) stateForm();
    if (event.target.closest('#authorize-invoice')) void fiscalAction(false);
    if (event.target.closest('#reconcile-invoice')) void fiscalAction(true);
  });
  dialog('detail-dialog').addEventListener('close', () => {
    if (!dialog('detail-dialog').open) { detailVersion++; selected = null; }
  });
  document.querySelectorAll('dialog').forEach(d => d.addEventListener('cancel', event => { if (busy) event.preventDefault(); }));
  window.addEventListener('beforeunload', event => { if (editor?.dirty) event.preventDefault(); });
  await bootstrap();
}

