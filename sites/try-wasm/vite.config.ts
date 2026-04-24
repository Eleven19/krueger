/// <reference types="vitest" />

import { sveltekit } from '@sveltejs/kit/vite';
import { defineConfig } from 'vite';

const isVitest = process.env.VITEST === 'true';

export default defineConfig({
  plugins: [sveltekit()],
  build: {
    // Monaco is loaded via dynamic import in the editor component; keep CI quiet
    // for that intentionally split editor chunk while preserving normal warnings
    // for unexpectedly large app code.
    chunkSizeWarningLimit: 3000
  },
  ...(isVitest
    ? {
        resolve: {
          conditions: ['browser']
        }
      }
    : {})
});
