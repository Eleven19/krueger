import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';
import starlightConfig from './starlight.config.ts';

// Published at https://eleven19.github.io/krueger/
export default defineConfig({
  site: 'https://eleven19.github.io',
  base: '/krueger',
  trailingSlash: 'ignore',
  output: 'static',
  integrations: [starlight(starlightConfig)],
  vite: {
    // monaco-editor ships every language as its own module. The /try/
    // playground only uses the base editor + a Monarch-registered Elm
    // tokenizer, so pre-bundling the full monaco-editor dep tree into
    // Vite's scan step is wasted work. Exclude it; Vite will still follow
    // the imports at build time via the normal Rollup pipeline.
    optimizeDeps: {
      exclude: ['monaco-editor'],
    },
    // Monaco's `?worker` imports need ES-module worker output so they can
    // resolve their own dynamic imports at runtime.
    worker: {
      format: 'es',
    },
    build: {
      // Monaco is intentionally bundled for the Laminar playground; keep the
      // build output signal focused on unexpected warnings.
      chunkSizeWarningLimit: 5000,
    },
  },
});
