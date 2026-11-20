import { el } from './utils/dom';

/** Menús en la capa superior: la tabla nunca recorta las acciones. */
export function iniciarAccionesFactura(): void {
  const filas = el('#invoice-rows');
  const disparador = (menu: HTMLElement): HTMLButtonElement => el<HTMLButtonElement>(`[aria-controls="${menu.id}"]`, filas);
  const items = (menu: HTMLElement): HTMLElement[] => [...menu.querySelectorAll<HTMLElement>('[role=menuitem]')];
  const posicionar = (menu: HTMLElement, boton: HTMLButtonElement): void => {
    const origen = boton.getBoundingClientRect(), caja = menu.getBoundingClientRect();
    menu.style.left = `${Math.max(8, Math.min(origen.right - caja.width, innerWidth - caja.width - 8))}px`;
    const top = origen.bottom + caja.height + 6 <= innerHeight - 8 ? origen.bottom + 6 : origen.top - caja.height - 6;
    menu.style.top = `${Math.max(8, Math.min(top, innerHeight - caja.height - 8))}px`;
  };
  const cerrar = (devolverFoco = false): void => {
    for (const menu of filas.querySelectorAll<HTMLElement>('.invoice-action-menu:popover-open')) {
      menu.hidePopover(); const boton = disparador(menu); boton.setAttribute('aria-expanded','false');
      if (devolverFoco) boton.focus();
    }
  };
  const abrir = (boton: HTMLButtonElement, ultimo = false): void => {
    const menu = el<HTMLElement>(`#${boton.getAttribute('aria-controls')}`, filas);
    cerrar(); menu.showPopover(); boton.setAttribute('aria-expanded','true');
    posicionar(menu,boton);
    const opciones = items(menu); opciones[ultimo ? opciones.length - 1 : 0]?.focus({ preventScroll:true });
  };
  filas.addEventListener('click', event => {
    if (!(event.target instanceof Element)) return;
    const boton = event.target.closest<HTMLButtonElement>('[data-actions]');
    if (boton) {
      event.preventDefault();
      const menu = el<HTMLElement>(`#${boton.getAttribute('aria-controls')}`, filas);
      if (menu.matches(':popover-open')) cerrar(true); else abrir(boton);
    } else if (event.target.closest('[role=menuitem]')) cerrar();
  });
  filas.addEventListener('keydown', event => {
    if (!(event.target instanceof Element)) return;
    const boton = event.target.closest<HTMLButtonElement>('[data-actions]');
    if (boton && ['ArrowDown','ArrowUp'].includes(event.key)) { event.preventDefault(); abrir(boton,event.key === 'ArrowUp'); return; }
    const menu = event.target.closest<HTMLElement>('.invoice-action-menu');
    if (!menu?.matches(':popover-open')) return;
    const opciones = items(menu), actual = opciones.indexOf(document.activeElement as HTMLElement);
    if (['ArrowDown','ArrowUp','Home','End'].includes(event.key)) {
      event.preventDefault();
      const indice = event.key === 'Home' ? 0 : event.key === 'End' ? opciones.length - 1 : (actual + (event.key === 'ArrowDown' ? 1 : -1) + opciones.length) % opciones.length;
      opciones[indice]?.focus();
    } else if (event.key === 'Escape') { event.preventDefault(); cerrar(true); }
    else if (event.key === 'Tab') cerrar(true);
  });
  filas.addEventListener('toggle', event => {
    if (event.target instanceof HTMLElement && event.target.classList.contains('invoice-action-menu'))
      disparador(event.target).setAttribute('aria-expanded',String(event.target.matches(':popover-open')));
  }, true);
  document.addEventListener('focusin', event => {
    const menu = filas.querySelector<HTMLElement>('.invoice-action-menu:popover-open');
    if (menu && event.target instanceof Node && !menu.contains(event.target) && !disparador(menu).contains(event.target)) cerrar();
  });
  window.addEventListener('resize', () => cerrar());
  document.addEventListener('scroll', event => {
    if (event.target instanceof Element && event.target.closest('.invoice-action-menu')) return;
    const menu = filas.querySelector<HTMLElement>('.invoice-action-menu:popover-open');
    if (menu) posicionar(menu,disparador(menu));
  }, { capture:true, passive:true });
}
