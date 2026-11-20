import { expect, type Page } from '@playwright/test';

export async function accionFactura(page: Page, id: number | string, accion: 'edit' | 'void' | 'delete'): Promise<void> {
  await page.locator(`[data-actions="${id}"]`).click();
  await expect(page.locator(`#invoice-actions-${id}`)).toBeVisible();
  await page.locator(`[data-${accion}="${id}"]`).click();
}

export async function agregarArticulo(page: Page, articuloId: number | string, lista = '1'): Promise<void> {
  await page.locator('#add-line').click();
  await page.locator('#price-filters [name=listaPrecioId]').selectOption(lista);
  await page.locator('#price-filters [name=q]').fill(''); await page.locator('#price-filters [name=iva]').fill('');
  await page.locator('#price-filters button[type=submit]').click();
  await page.locator(`#price-rows [data-price-article="${articuloId}"][data-price-list="${lista}"]`).click();
  await expect(page.locator('#price-dialog')).not.toBeVisible();
}
