import { cp, mkdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

// Publicar la misma UI TypeScript en Spring y Vite. No eliminar archivos ajenos.
const origen = fileURLToPath(new URL('../dist/', import.meta.url));
const destino = fileURLToPath(new URL('../../src/main/resources/static/', import.meta.url));
await mkdir(destino, { recursive: true });
await cp(origen, destino, { recursive: true });
console.log('Frontend compilado publicado en los recursos estáticos de Spring.');
