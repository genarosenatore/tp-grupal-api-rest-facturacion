import { defineConfig, loadEnv } from 'vite';

// El navegador usa /api en su mismo origen; Vite reenvía a Spring Boot.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const proxy = { '/api': { target: env.API_PROXY_TARGET || 'http://127.0.0.1:8080' } };
  return { server: { port: 5173, strictPort: true, proxy }, preview: { port: 4173, proxy } };
});
