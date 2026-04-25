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
});
