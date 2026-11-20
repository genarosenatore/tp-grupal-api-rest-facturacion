import { test, expect } from '@playwright/test';
import { agregarArticulo } from './seleccion';
import type { CatalogosDTO } from '../../src/models/catalogos.dto';

test('filtra comprobantes por receptor, exige confirmar al cambiar y conserva el tipo al editar', async ({ page }) => {
  const catalogos: CatalogosDTO = {
    homologacionHabilitada: true, listasPrecio: [{ id: 1, nombre: 'Minorista' }],
    clientes: [{ id: 1, nombre: 'Cliente DNI', documento: '12345678', tipoDocumento: 96, condicionIvaReceptorId: 5, domicilio: null }, { id: 3, nombre: 'Cliente CUIT de prueba', documento: '20123456786', tipoDocumento: 80, condicionIvaReceptorId: 1, domicilio: null }, { id: 4, nombre: 'Cliente monotributista', documento: '20999999995', tipoDocumento: 80, condicionIvaReceptorId: 6, domicilio: null }],
    puntosVenta: [{ id: 2, codigo: '0002', descripcion: 'Entorno de prueba' }],
    condicionesIva: [{ id: 1, descripcion: 'Consumidor final' }], monedas: [{ id: 1, codigo: 'ARS', descripcion: 'Pesos' }],
    articulos: [{ id: 1, codigo: 'A1', descripcion: 'Cuaderno', alicuotaIva: 21 }],
    precios: [{ articuloId: 1, tipoMonedaId: 1, precio: 1000, listaPrecioId: 1 }]
  };
  let creada: Record<string, unknown> | null = null;
  await page.route(/\/api\/catalogos(?:\?.*)?$/, route => route.fulfill({ json: catalogos }));
  await page.route('**/api/clientes/*', route => route.fulfill({ json: catalogos.clientes.find(c => c.id === Number(new URL(route.request().url()).pathname.split('/').pop())) }));
  await page.route('**/api/catalogos/precios/*', route => route.fulfill({ json: { id: 1, codigo: 'A1', descripcion: 'Cuaderno', alicuotaIva: 21, tipoMonedaId: 1, precio: 1000, listaPrecioId: 1 } }));
  await page.route('**/api/catalogos/precios?**', route => route.fulfill({ json: [{ id: 1, codigo: 'A1', descripcion: 'Cuaderno', alicuotaIva: 21, tipoMonedaId: 1, precio: 1000, listaPrecioId: 1 }] }));
  await page.route('**/api/facturas**', async route => {
    const request = route.request();
    if (request.method() === 'POST') {
      const dto = request.postDataJSON();
      expect(dto.tipoComprobante).toBe('1'); expect(dto.tipoDocumentoReceptor).toBeUndefined(); expect(dto.condicionIvaReceptorId).toBeUndefined();
      creada = { ...dto, id: 10, version: 0, clienteNombre: 'Cliente CUIT de prueba', clienteDocumento: '20123456786', condicionIvaDescripcion: 'Consumidor final', puntoVentaCodigo: '0002',
        moneda: 'ARS', estado: 'EMITIDA', fechaAnulacion: null, neto: 1000, iva: 210, total: 1210,
        numero: null, estadoArca: 'PENDIENTE', informadaArca: false, tipoAutorizacion: null, codigoAutorizacion: null, vencimientoAutorizacion: null, fechaAutorizacion: null,
        motivoAnulacion: null, editable: true, eliminable: true, ambienteArca: null, letraComprobante: 'A', tipoDocumentoReceptor: 80, condicionIvaReceptorId: 1, clienteDomicilio: null,
        subtotal: 1000, tipoDescuento: 'SIN_DESCUENTO', valorDescuento: 0, importeDescuento: 0,
        detalles: [{ id: 1, articuloId: 1, listaPrecioArticuloId: 1, descripcion: 'Cuaderno', cantidad: 1, precioUnitario: 1000, alicuotaIva: 21, neto: 1000, iva: 210, total: 1210, descuento: 0 }] };
      return route.fulfill({ status: 201, json: creada });
    }
    if (new URL(request.url()).pathname.endsWith('/10')) return route.fulfill({ json: creada });
    return route.fulfill({ json: { content: creada ? [creada] : [], page: 0, size: 8, totalElements: creada ? 1 : 0, totalPages: creada ? 1 : 0 } });
  });
  await page.goto('/'); await page.locator('#new-invoice').click();
  const form = page.locator('#invoice-form');
  await expect(form.locator('#invoice-letter')).toHaveCount(0);
  await form.locator('[name=clienteId]').selectOption('1');
  await expect(form.locator('[name=tipoComprobante] option[value="1"]')).toHaveCount(0);
  await expect(form.locator('[name=tipoComprobante] option[value="6"]')).toHaveCount(1);
  await expect(form.locator('[name=tipoComprobante] option[value="11"]')).toHaveCount(1);
  await form.locator('[name=tipoMonedaId]').selectOption('1');
  await agregarArticulo(page,1);
  await expect(page.locator('#preview-total')).toHaveText('ARS 1.000,00');
  await form.locator('[name=tipoComprobante]').selectOption('6');
  await expect(form.locator('[name=tipoComprobante]')).toHaveValue('6');
  await expect(page.locator('#preview-total')).toHaveText('ARS 1.210,00');
  await expect(page.locator('#client-data select')).toHaveCount(0);
  await expect(page.locator('#client-document')).toHaveText('DNI 12345678');
  await expect(page.locator('#client-condition')).toHaveText('Consumidor final');
  await form.locator('[name=clienteId]').selectOption('3');
  await expect(page.locator('#client-document')).toHaveText('CUIT 20123456786');
  await expect(page.locator('#client-condition')).toHaveText('IVA Responsable Inscripto');
  await expect(form.locator('[name=tipoComprobante] option[value="6"]')).toHaveCount(0);
  await expect(form.locator('[name=tipoComprobante]')).toHaveValue('seleccionar');
  await expect(page.locator('#receipt-options-hint')).toContainText('no corresponde');
  await expect(page.locator('#preview-total')).toHaveText('Completá los datos');
  await page.locator('#save-invoice').click();
  await expect(page.locator('#invoice-dialog')).toBeVisible(); expect(creada).toBeNull();
  await form.locator('[name=tipoComprobante]').selectOption('1');
  await form.locator('[name=clienteId]').selectOption('4');
  await expect(form.locator('[name=tipoComprobante]')).toHaveValue('1');
  await expect(page.locator('#selected-client')).toContainText('Responsable Monotributo');
  await expect(form.locator('[name=tipoComprobante] option[value="6"]')).toHaveCount(0);
  await form.locator('[name=clienteId]').selectOption('1');
  await expect(form.locator('[name=tipoComprobante]')).toHaveValue('seleccionar');
  await expect(form.locator('[name=tipoComprobante] option[value="1"]')).toHaveCount(0);
  await form.locator('[name=tipoComprobante]').selectOption('');
  await form.locator('[name=clienteId]').selectOption('3');
  await expect(form.locator('[name=tipoComprobante]')).toHaveValue('');
  await form.locator('[name=tipoComprobante]').selectOption('1');
  await expect(form.locator('#form-lines tr')).toHaveCount(1);
  await expect(form.locator('.line-quantity')).toHaveValue('1');
  await expect(page.locator('#preview-total')).toHaveText('ARS 1.210,00');
  expect(await form.locator('[name=clienteId]').evaluate((select: HTMLSelectElement) => select.validity.customError)).toBe(false);
  await expect(form.locator('[name=puntoVentaId]')).toHaveValue('2');
  await page.locator('#save-invoice').click();
  await expect(page.locator('#invoice-dialog')).not.toBeVisible();
  await expect(page.locator('#invoice-rows')).toContainText('Factura A · Homologación');
  await page.locator('[data-detail="10"]').click();
  await expect(page.locator('#authorize-invoice')).toBeVisible();
  await expect(page.locator('#detail-content')).toContainText('IVA Responsable Inscripto');
  await page.locator('#edit-invoice').click();
  await expect(form.locator('[name=tipoComprobante]')).toHaveValue('1');
  await expect(page.locator('#client-condition')).toHaveText('IVA Responsable Inscripto');
  await expect(page.locator('#preview-total')).toHaveText('ARS 1.210,00');
});
