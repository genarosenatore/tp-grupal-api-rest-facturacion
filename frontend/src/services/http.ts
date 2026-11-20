export const API_BASE = (import.meta.env?.VITE_API_BASE_URL || '/api').replace(/\/$/, '');

export class ApiError extends Error {
  constructor(message: string, public readonly status: number) { super(message); }
}

/** Único punto de fetch. Nunca muestra trazas ni el HTML de un proxy. */
export async function http(path: string, method = 'GET', body?: unknown): Promise<unknown> {
  let response: Response;
  try {
    response = await fetch(`${API_BASE}${path}`, {
      method, headers: { Accept: 'application/json', ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) },
      body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(20000)
    });
  } catch {
    throw new ApiError('No se pudo conectar. Verificá Spring Boot, PostgreSQL y la configuración de la API.', 0);
  }
  if (response.status === 204 && response.ok) return undefined;
  const json: unknown = await response.json().catch(() => null);
  if (!response.ok) {
    const defaults: Record<number, string> = {
      400: 'Revisá los datos ingresados.', 404: 'No se encontró el recurso solicitado.',
      409: 'La operación entra en conflicto con el estado o los precios actuales.'
    };
    let message = response.status >= 500 ? 'El servidor no pudo completar la operación. Intentá nuevamente.' : (defaults[response.status] || `Error HTTP ${response.status}.`);
    if (response.status < 500 && isRecord(json) && typeof json.mensaje === 'string') {
      message = [json.mensaje, ...(Array.isArray(json.errores) ? json.errores.filter((v): v is string => typeof v === 'string') : [])].join('\n');
    }
    throw new ApiError(message, response.status);
  }
  if (json === null) throw new ApiError('La API devolvió una respuesta JSON inválida.', response.status);
  return json;
}

export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
