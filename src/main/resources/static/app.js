import { calcular, decimal, importe } from './importes.mjs';

const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
const escape = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const number = value => {
  const text = String(value);
  // Los importes calculados con BigInt se formatean sin convertirlos a double.
  if (/^\d+(\.\d{1,2})?$/.test(text)) {
    const [whole, fraction = ''] = text.split('.');
    return `${new Intl.NumberFormat('es-AR').format(BigInt(whole))},${fraction.padEnd(2,'0')}`;
  }
  return new Intl.NumberFormat('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value);
};
const money = (value, currency = 'ARS') => `${currency} ${number(value)}`;
const date = value => value ? new Intl.DateTimeFormat('es-AR').format(new Date(`${value}T12:00:00`)) : '—';
const today = () => { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`; };
const icon = name => `<svg aria-hidden="true"><use href="#i-${name}"/></svg>`;
const badge = status => `<span class="badge ${status === 'ANULADA' ? 'void' : ''}">${status === 'ANULADA' ? 'Anulada' : 'Emitida'}</span>`;
const reference = id => `#${String(id).padStart(6, '0')}`;
const state = { catalog: null, invoices: [], page: 1, pageSize: 8, editing: null, selected: null, busy: false, dirty: false, view: 'facturas', loading: false, loadVersion: 0 };
let toastTimer;

async function api(path, options = {}) {
  let response;
  try { response = await fetch(path, { ...options, headers: { 'Content-Type': 'application/json', ...options.headers }, signal: AbortSignal.timeout(20000) }); }
  catch { throw new Error('No se pudo conectar con el servidor. Comprobá que la aplicación y PostgreSQL estén iniciados e intentá nuevamente.'); }
  if (response.status === 204) return null;
  const body = await response.json().catch(() => null);
  if (!response.ok) throw new Error([body?.mensaje || `No se pudo completar la operación (HTTP ${response.status}).`, ...(Array.isArray(body?.errores) ? body.errores : [])].join('\n'));
  if (body === null) throw new Error('El servidor devolvió una respuesta inválida.');
  return body;
}
function showError(selector, error) { const el = $(selector); el.textContent = error.message; el.hidden = false; }
function notify(message) { $('#toast').textContent = message; $('#toast').hidden = false; clearTimeout(toastTimer); toastTimer = setTimeout(() => $('#toast').hidden = true, 5000); }
function connection(connected) { $('#connection').textContent = connected ? 'Servidor conectado' : 'Sin conexión'; $('#connection').classList.toggle('offline', !connected); }
function options(items, text, placeholder = '') { return `${placeholder ? `<option value="">${escape(placeholder)}</option>` : ''}${items.map(i => `<option value="${i.id}">${escape(text(i))}</option>`).join('')}`; }
function catalogReady() {
  const c = state.catalog, f = $('#invoice-form');
  $('#filter-client').innerHTML = options(c.clientes, i => i.nombre, 'Todos los clientes');
  $('[name=clienteId]', f).innerHTML = options(c.clientes, i => `${i.nombre} · ${i.documento}`, 'Seleccioná un cliente');
  $('[name=condicionIvaId]', f).innerHTML = options(c.condicionesIva, i => i.descripcion);
  $('[name=puntoVentaId]', f).innerHTML = options(c.puntosVenta, i => `${i.codigo} · ${i.descripcion}`);
  $('[name=tipoMonedaId]', f).innerHTML = options(c.monedas, i => `${i.codigo} · ${i.descripcion}`);
  $('#summary-currency').innerHTML = c.monedas.map(m => `<option>${escape(m.codigo)}</option>`).join('');
  $('#new-invoice').disabled = !c.clientes.length || !c.condicionesIva.length || !c.puntosVenta.length || !c.monedas.length || !c.articulos.length;
}
async function bootstrap() {
  $('#global-error').hidden = true;
  try { state.catalog = await api('/api/catalogos'); catalogReady(); connection(true); await loadInvoices(); renderCatalog(); }
  catch(error) { connection(false); showError('#global-error', error); $('#table-status').textContent = 'No se pudieron cargar los datos. Usá Actualizar para reintentar.'; }
}
async function loadInvoices() {
  const filters = new FormData($('#filters'));
  if (filters.get('desde') && filters.get('hasta') && filters.get('desde') > filters.get('hasta')) { showError('#global-error', new Error('La fecha desde debe ser anterior o igual a la fecha hasta.')); return; }
  const version = ++state.loadVersion;
  state.loading = true; $('#reload').disabled = true;
  $('#global-error').hidden = true; $('#table-status').hidden = false;
  $('#table-status').textContent = 'Cargando comprobantes…'; $('#invoice-table-wrap').hidden = true;
  const params = new URLSearchParams();
  for (const [key, value] of filters) if (value) params.set(key, value);
  try {
    const invoices = await api(`/api/facturas?${params}`);
    if (version !== state.loadVersion) return;
    state.invoices = invoices; state.page = 1; state.loading = false; connection(true); renderInvoices();
  } catch(error) {
    if (version !== state.loadVersion) return;
    connection(false); showError('#global-error', error); $('#table-status').textContent = 'No se pudieron cargar las facturas. Usá Actualizar para reintentar.';
    ['stat-total','stat-count','stat-issued','stat-void'].forEach(id => $(`#${id}`).textContent = '—');
    $('#page-info').textContent = 'Consulta sin actualizar'; $('#previous').disabled = true; $('#next').disabled = true;
  } finally { if (version === state.loadVersion) { state.loading = false; $('#reload').disabled = false; } }
}
function visibleInvoices() {
  const query = $('#search').value.trim().toLocaleLowerCase('es');
  return state.invoices.filter(f => !query || `${f.clienteNombre} ${f.id} ${reference(f.id)}`.toLocaleLowerCase('es').includes(query));
}
function renderInvoices() {
  if (state.loading) return;
  const invoices = visibleInvoices(), currency = $('#summary-currency').value || 'ARS';
  const issued = invoices.filter(f => f.estado === 'EMITIDA');
  const total = issued.filter(f => f.moneda === currency).reduce((sum, f) => sum + decimal(f.total, 2), 0n);
  $('#stat-total').textContent = number(importe(total)); $('#stat-currency').textContent = currency;
  $('#stat-count').textContent = invoices.length; $('#stat-issued').textContent = issued.length; $('#stat-void').textContent = invoices.length - issued.length;
  $('#result-count').textContent = invoices.length; $('#nav-count').textContent = state.invoices.length;
  const pages = Math.max(1, Math.ceil(invoices.length / state.pageSize)); state.page = Math.min(state.page, pages);
  const start = (state.page - 1) * state.pageSize;
  $('#invoice-rows').innerHTML = invoices.slice(start, start + state.pageSize).map(f => {
    const client = state.catalog.clientes.find(c => c.id === f.clienteId);
    const initials = f.clienteNombre.split(/\s+/).slice(0,2).map(s => s[0]).join('');
    return `<tr><td><div class="invoice-number"><span class="file-tile">${icon('file')}</span><div>Factura ${reference(f.id)}<span class="cell-sub">Comprobante de venta</span></div></div></td><td><div class="client-cell"><span class="client-initials">${escape(initials)}</span><div>${escape(f.clienteNombre)}<span class="cell-sub">${escape(client?.documento || `Cliente #${f.clienteId}`)}</span></div></div></td><td>${date(f.fecha)}</td><td>${badge(f.estado)}</td><td class="numeric amount">${number(f.total)} <small>${escape(f.moneda)}</small></td><td><button class="row-open" data-open="${f.id}" aria-label="Ver factura ${reference(f.id)}">${icon('arrow')}</button></td></tr>`;
  }).join('');
  $('#invoice-table-wrap').hidden = !invoices.length; $('#table-status').hidden = !!invoices.length;
  if (!invoices.length) $('#table-status').innerHTML = `<strong>${state.invoices.length ? 'No hay coincidencias' : 'No hay facturas para mostrar'}</strong>${$('#search').value || [...new FormData($('#filters')).values()].some(Boolean) ? 'Probá con otros filtros o limpiá la búsqueda.' : 'Creá tu primera factura para empezar a organizar tus ventas.'}`;
  $('#page-info').textContent = invoices.length ? `Mostrando ${start+1}–${Math.min(start + state.pageSize, invoices.length)} de ${invoices.length} comprobantes` : '0 comprobantes';
  $('#page-number').textContent = `${state.page} / ${pages}`;
  $('#previous').disabled = state.page <= 1; $('#next').disabled = state.page >= pages;
}
function renderCatalog() {
  if (!state.catalog || state.view === 'facturas') return;
  const clients = state.view === 'clientes', c = state.catalog;
  $('#catalog-title').textContent = `${clients ? 'Clientes' : 'Artículos'} disponibles`;
  $('#catalog-head').innerHTML = `<tr>${(clients ? ['Cliente','Documento','Identificador'] : ['Artículo','Código','IVA',...c.monedas.map(m => `Precio ${m.codigo}`)]).map(t => `<th>${escape(t)}</th>`).join('')}</tr>`;
  $('#catalog-rows').innerHTML = (clients ? c.clientes : c.articulos).map(i => clients
    ? `<tr><td>${escape(i.nombre)}</td><td>${escape(i.documento)}</td><td>#${i.id}</td></tr>`
    : `<tr><td>${escape(i.descripcion)}</td><td>${escape(i.codigo)}</td><td>${number(i.alicuotaIva)} %</td>${c.monedas.map(m => { const p = c.precios.find(p => p.articuloId === i.id && p.tipoMonedaId === m.id); return `<td>${p ? money(p.precio,m.codigo) : '<span class="cell-sub">Sin precio</span>'}</td>`; }).join('')}</tr>`).join('') || `<tr><td colspan="10">No hay registros disponibles.</td></tr>`;
}
function setView(view) {
  state.view = view;
  const invoice = view === 'facturas';
  $$('.nav-item').forEach(b => { b.classList.toggle('active', b.dataset.view === view); if (b.dataset.view === view) b.setAttribute('aria-current','page'); else b.removeAttribute('aria-current'); });
  $('#invoice-view').hidden = !invoice; $('#catalog-view').hidden = invoice; $('#new-invoice').hidden = !invoice;
  $('#breadcrumb-current').textContent = {facturas:'Facturas',clientes:'Clientes',articulos:'Artículos'}[view];
  $('#page-title').innerHTML = `${{facturas:'Facturas de venta',clientes:'Tus clientes',articulos:'Catálogo de artículos'}[view]}<span>.</span>`;
  $('#page-description').textContent = {facturas:'Organizá tus comprobantes y llevá el control de cada venta.',clientes:'Consultá los clientes disponibles para tus comprobantes.',articulos:'Revisá los artículos, sus precios vigentes y alícuotas.'}[view];
  renderCatalog();
}
function currency() { return state.catalog.monedas.find(m => m.id === Number($('[name=tipoMonedaId]', $('#invoice-form')).value))?.codigo || 'ARS'; }
function priceFor(id) { return state.catalog.precios.find(p => p.articuloId === Number(id) && p.tipoMonedaId === Number($('[name=tipoMonedaId]', $('#invoice-form')).value)); }
function addLine(articleId = '', quantity = 1) {
  if ($('#form-lines').children.length >= 100) return;
  const row = document.createElement('tr');
  row.innerHTML = `<td><select class="line-article" required aria-label="Artículo">${options(state.catalog.articulos, a => `${a.codigo} · ${a.descripcion}`, 'Seleccioná un artículo')}</select></td><td><input class="line-quantity" type="number" min="0.001" max="999999.999" step="0.001" value="${escape(quantity)}" required aria-label="Cantidad"></td><td class="numeric line-price">—</td><td class="numeric line-tax">—</td><td class="numeric line-total">—</td><td><button class="icon-button remove-line" type="button" aria-label="Quitar artículo">${icon('trash')}</button></td>`;
  $('#form-lines').append(row); $('.line-article',row).value = articleId; updatePreview();
}
function updatePreview() {
  let totals = {neto:0n,iva:0n,total:0n}, missing = false, incomplete = false;
  $$('#form-lines tr').forEach(row => {
    const select = $('.line-article',row), quantity = $('.line-quantity',row), price = priceFor(select.value);
    const article = state.catalog.articulos.find(a => a.id === Number(select.value));
    select.setCustomValidity(select.value && !price ? 'Este artículo no tiene precio en la moneda seleccionada.' : '');
    $('.line-price',row).textContent = price ? number(price.precio) : '—';
    $('.line-tax',row).textContent = article ? `${number(article.alicuotaIva)} %` : '—';
    $('.line-total',row).textContent = '—';
    if (article && !price) missing = true;
    if (!price || !quantity.validity.valid || !quantity.value) { incomplete = true; return; }
    try { const line = calcular(quantity.value,price.precio,article.alicuotaIva); for (const key in totals) totals[key] += line[key]; $('.line-total',row).textContent = number(importe(line.total)); }
    catch { incomplete = true; }
  });
  $('#price-warning').hidden = !missing;
  $('#price-warning').textContent = 'Hay artículos sin precio en la moneda elegida. Cambiá la moneda o quitá esos artículos para guardar.';
  $('#preview-net').textContent = money(importe(totals.neto),currency()); $('#preview-tax').textContent = money(importe(totals.iva),currency());
  $('#preview-total').textContent = incomplete ? 'Completá los artículos' : money(importe(totals.total),currency());
  $('#add-line').disabled = state.busy || $('#form-lines').children.length >= 100;
  $$('.remove-line').forEach(b => b.disabled = state.busy || $('#form-lines').children.length <= 1);
}
function openForm(invoice = null) {
  if (!state.catalog || state.busy) return;
  const form = $('#invoice-form'); form.reset(); state.editing = invoice?.id ?? null; state.dirty = false;
  $('#form-error').hidden = true; $('#form-lines').replaceChildren();
  $('#dialog-title').textContent = invoice ? `Editar factura ${reference(invoice.id)}` : 'Nueva factura';
  $('#save-invoice').textContent = invoice ? 'Guardar cambios' : 'Crear factura';
  $('[name=fecha]', form).value = invoice?.fecha || today();
  if (invoice) for (const key of ['clienteId','condicionIvaId','puntoVentaId','tipoMonedaId','observaciones']) $('[name='+key+']', form).value = invoice[key] ?? '';
  if (invoice) invoice.detalles.forEach(d => addLine(d.articuloId,d.cantidad)); else addLine();
  $('#invoice-dialog').showModal();
}
function closeDialog(id) {
  if (state.busy) return;
  if (id === 'invoice-dialog' && state.dirty) { $('#discard-dialog').showModal(); return; }
  $(`#${id}`).close();
}
function lockForm(locked) {
  state.busy = locked;
  $$('input,select,textarea,button', $('#invoice-form')).forEach(el => el.disabled = locked);
  if (!locked) updatePreview();
}
async function saveInvoice(event) {
  event.preventDefault(); if (state.busy) return;
  updatePreview(); const form = $('#invoice-form'); if (!form.reportValidity()) return;
  const data = new FormData(form);
  const payload = Object.fromEntries(['fecha','clienteId','condicionIvaId','puntoVentaId','tipoMonedaId','observaciones'].map(key => [key, key.endsWith('Id') ? Number(data.get(key)) : data.get(key)]));
  payload.detalles = $$('#form-lines tr').map(row => ({articuloId:Number($('.line-article',row).value),cantidad:Number($('.line-quantity',row).value)}));
  const editId = state.editing, button = $('#save-invoice'), original = button.textContent;
  $('#form-error').hidden = true; lockForm(true); button.textContent = 'Guardando…';
  try {
    const saved = await api(`/api/facturas${editId ? `/${editId}` : ''}`, {method:editId ? 'PUT':'POST',body:JSON.stringify(payload)});
    state.dirty = false; $('#invoice-dialog').close(); notify(`Factura ${reference(saved.id)} ${editId ? 'actualizada' : 'creada'} correctamente.`);
    await loadInvoices(); await showDetail(saved.id);
  } catch(error) { showError('#form-error',error); $('#form-error').scrollIntoView({block:'nearest'}); }
  finally { lockForm(false); button.textContent = original; }
}
async function showDetail(id) {
  $('#detail-title').textContent = `Factura ${reference(id)}`; $('#detail-content').textContent = 'Consultando comprobante…'; $('#detail-actions').replaceChildren();
  if (!$('#detail-dialog').open) $('#detail-dialog').showModal();
  try {
    const f = await api(`/api/facturas/${id}`); state.selected = f;
    const c = state.catalog, client = c.clientes.find(x => x.id === f.clienteId), condition = c.condicionesIva.find(x => x.id === f.condicionIvaId), point = c.puntosVenta.find(x => x.id === f.puntoVentaId);
    $('#detail-content').innerHTML = `<dl class="detail-grid"><div><dt>Cliente</dt><dd>${escape(f.clienteNombre)}<span class="cell-sub">${escape(client?.documento)}</span></dd></div><div><dt>Fecha</dt><dd>${date(f.fecha)}</dd></div><div><dt>Estado</dt><dd>${badge(f.estado)}</dd></div><div><dt>Condición de IVA</dt><dd>${escape(condition?.descripcion || f.condicionIvaId)}</dd></div><div><dt>Punto de venta</dt><dd>${escape(point?.codigo || f.puntoVentaId)}</dd></div><div><dt>Moneda</dt><dd>${escape(f.moneda)}</dd></div></dl>${f.fechaAnulacion ? `<p class="notice warning">Anulada el ${escape(new Intl.DateTimeFormat('es-AR',{dateStyle:'medium',timeStyle:'short'}).format(new Date(f.fechaAnulacion)))}</p>` : ''}<div class="table-wrap"><table><thead><tr><th>Artículo</th><th>Cantidad</th><th class="numeric">Precio unit.</th><th class="numeric">IVA</th><th class="numeric">Total</th></tr></thead><tbody>${f.detalles.map(d => `<tr><td>${escape(d.descripcion)}</td><td>${escape(d.cantidad)}</td><td class="numeric">${number(d.precioUnitario)}</td><td class="numeric">${number(d.alicuotaIva)} %</td><td class="numeric">${number(d.total)}</td></tr>`).join('')}</tbody></table></div><div class="totals detail-totals"><div><span>Neto</span><strong>${money(f.neto,f.moneda)}</strong></div><div><span>IVA</span><strong>${money(f.iva,f.moneda)}</strong></div><div class="grand-total"><span>Total</span><strong>${money(f.total,f.moneda)}</strong></div></div>${f.observaciones ? `<p class="detail-notes">${escape(f.observaciones)}</p>` : ''}<p class="form-hint">Identificador técnico ${reference(f.id)} · Comprobante sin validez fiscal.</p>`;
    $('#detail-actions').innerHTML = `<button class="button secondary" data-close="detail-dialog">Cerrar</button>${f.estado === 'EMITIDA' ? '<button id="void-invoice" class="button secondary">Anular factura</button><button id="edit-invoice" class="button primary">Editar factura</button>' : ''}`;
  } catch(error) { $('#detail-content').textContent = error.message; $('#detail-actions').innerHTML = '<button class="button secondary" data-close="detail-dialog">Cerrar</button>'; }
}
async function voidInvoice() {
  if (state.busy) return;
  const id = state.selected.id; state.busy = true; $('#confirm-void').disabled = true; $('#confirm-void').textContent = 'Anulando…'; $('#void-error').hidden = true;
  try { await api(`/api/facturas/${id}`,{method:'DELETE'}); $('#void-dialog').close(); notify(`Factura ${reference(id)} anulada. El historial se conservó.`); await loadInvoices(); await showDetail(id); }
  catch(error) { showError('#void-error',error); }
  finally { state.busy = false; $('#confirm-void').disabled = false; $('#confirm-void').textContent = 'Anular factura'; }
}

document.addEventListener('click', event => {
  const close = event.target.closest('[data-close]'); if (close) closeDialog(close.dataset.close);
  if (event.target.closest('#confirm-discard')) { state.dirty = false; $('#discard-dialog').close(); $('#invoice-dialog').close(); }
  const nav = event.target.closest('[data-view]'); if (nav) setView(nav.dataset.view);
  const open = event.target.closest('[data-open]'); if (open) showDetail(Number(open.dataset.open));
  if (event.target.closest('#edit-invoice')) { $('#detail-dialog').close(); openForm(state.selected); }
  if (event.target.closest('#void-invoice')) { $('#void-description').textContent = `Vas a anular la factura ${reference(state.selected.id)} de ${state.selected.clienteNombre}.`; $('#void-error').hidden = true; $('#void-dialog').showModal(); }
  const remove = event.target.closest('.remove-line'); if (remove && !state.busy && $('#form-lines').children.length > 1) { remove.closest('tr').remove(); state.dirty = true; updatePreview(); }
});
$$('dialog').forEach(dialog => dialog.addEventListener('cancel', event => { event.preventDefault(); closeDialog(dialog.id); }));
$('#new-invoice').addEventListener('click', () => openForm());
$('#invoice-form').addEventListener('submit', saveInvoice);
$('#invoice-form').addEventListener('input', () => { state.dirty = true; updatePreview(); });
$('#invoice-form').addEventListener('change', () => { state.dirty = true; updatePreview(); });
$('#add-line').addEventListener('click', () => { addLine(); state.dirty = true; $$('.line-article').at(-1).focus(); });
$('#confirm-void').addEventListener('click', voidInvoice);
$('#filters').addEventListener('submit', event => { event.preventDefault(); loadInvoices(); });
$('#filters').addEventListener('reset', () => { setTimeout(() => { $('#search').value = ''; loadInvoices(); },0); });
$('#reload').addEventListener('click', () => state.catalog ? loadInvoices() : bootstrap());
$('#search').addEventListener('input', () => { state.page = 1; renderInvoices(); });
$('#summary-currency').addEventListener('change', renderInvoices);
$('#previous').addEventListener('click', () => { state.page--; renderInvoices(); });
$('#next').addEventListener('click', () => { state.page++; renderInvoices(); });
window.addEventListener('beforeunload', event => { if (state.dirty) { event.preventDefault(); event.returnValue = ''; } });
bootstrap();
