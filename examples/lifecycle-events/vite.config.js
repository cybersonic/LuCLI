import { resolve } from 'node:path';
import { defineConfig } from 'vite';

export default defineConfig({
  build: {
    lib: {
      entry: resolve(__dirname, 'src/js/main.js'),
      formats: ['iife'],
      name: 'LifecycleEventsDemo',
      fileName: () => 'bundle.js',
    },
    outDir: resolve(__dirname, 'webroot/assets'),
    emptyOutDir: false,
  },
});
