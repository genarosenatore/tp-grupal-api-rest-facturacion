import { test, expect } from '@playwright/test';
import type { CatalogosDTO } from '../../src/models/catalogos.dto';
import type { FacturaReporteDTO } from '../../src/models/factura.dto';

test('todas las filas tienen Consultar y Acciones; el menú respeta permisos, teclado y tamaño de pantalla', async ({ page, request }) => {
  const catalogo: CatalogosDTO = await (await request.get('/api/catalogos')).json(), precio = catalogo.precios[0];
  const respuesta = await request.post('/api/facturas', { data: { fecha:'2026-11-20', clienteId:catalogo.clientes[0].id,
    condicionIvaId:catalogo.condicionesIva[0].id, puntoVentaId:catalogo.puntosVenta[0].id, tipoMonedaId:precio.tipoMonedaId,
    detalles:[{ articuloId:precio.articuloId,cantidad:1 }] } });
  expect(respuesta.status()).toBe(201);
  const borrador: FacturaReporteDTO = await respuesta.json();
  const anulada: FacturaReporteDTO = { ...borrador,id:borrador.id + 10000,estado:'ANULADA',editable:false,eliminable:false };
  try {
    await page.route('**/api/facturas?**', route => route.fulfill({ json:{ content:[borrador,anulada],page:0,size:8,totalElements:2,totalPages:1 } }));
    await page.setViewportSize({ width:1366,height:1000 }); await page.goto('/');
    await expect(page.locator('#invoice-rows tr')).toHaveCount(2);
    for (const fila of await page.locator('#invoice-rows tr').all()) {
      await expect(fila.locator('.row-action-buttons button')).toHaveText(['Consultar','Acciones']);
      await expect(fila.locator('[role=menu]')).not.toBeVisible();
    }
    const boton = page.locator(`[data-actions="${borrador.id}"]`), menu = page.locator(`#invoice-actions-${borrador.id}`);
    await boton.click(); await expect(menu).toBeVisible(); await expect(boton).toHaveAttribute('aria-expanded','true');
    await expect(menu.getByRole('menuitem')).toHaveText(['Descargar PDF','Editar','Anular','Eliminar borrador']);
    await page.screenshot({ path:'../tmp/browser-results/acciones-factura-escritorio.png' });
    await boton.click(); await expect(menu).not.toBeVisible();
    await boton.focus(); await page.keyboard.press('ArrowDown');
    await expect(menu.getByRole('menuitem').first()).toBeFocused();
    await page.keyboard.press('ArrowDown'); await expect(menu.getByRole('menuitem',{ name:'Editar',exact:true })).toBeFocused();
    await page.keyboard.press('End'); await expect(menu.getByRole('menuitem',{ name:'Eliminar borrador' })).toBeFocused();
    await page.keyboard.press('Escape'); await expect(menu).not.toBeVisible(); await expect(boton).toBeFocused();
    await boton.click(); await page.locator('#search-title').click(); await expect(menu).not.toBeVisible();
    await boton.click(); await page.locator(`[data-actions="${anulada.id}"]`).click();
    await expect(menu).not.toBeVisible();
    const menuAnulada = page.locator(`#invoice-actions-${anulada.id}`);
    await expect(menuAnulada.getByRole('menuitem')).toHaveText(['Descargar PDF']);
    await expect(menuAnulada.locator('[data-edit],[data-void],[data-delete]')).toHaveCount(0);
    await page.keyboard.press('Escape');
    await page.setViewportSize({ width:390,height:844 }); await boton.click();
    await expect(menu).toBeVisible();
    expect(await menu.evaluate(n => { const r=n.getBoundingClientRect(); return r.left>=0 && r.right<=innerWidth && r.top>=0 && r.bottom<=innerHeight; })).toBe(true);
    await page.screenshot({ path:'../tmp/browser-results/acciones-factura-movil.png' });
    const descarga = page.waitForEvent('download'); await menu.getByRole('menuitem',{ name:'Descargar PDF' }).click();
    expect((await descarga).suggestedFilename()).toBe(`factura-${borrador.id}.pdf`);
    await expect(menu).not.toBeVisible();
  } finally { await request.delete(`/api/facturas/${borrador.id}`); }
});
