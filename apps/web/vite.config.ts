import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // Las peticiones al API van por el proxy: así el navegador no necesita CORS en dev.
    proxy: {
      '/api': 'http://localhost:8081',
    },
  },
});
