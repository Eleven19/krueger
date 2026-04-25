import adapter from '@sveltejs/adapter-static';
import { vitePreprocess } from '@sveltejs/vite-plugin-svelte';

const dev = process.env.NODE_ENV === 'development';

/** @type {import('@sveltejs/kit').Config} */
const config = {
  preprocess: vitePreprocess(),
  kit: {
    adapter: adapter({
      fallback: '404.html'
    }),
    paths: {
      base: dev ? '' : '/krueger/try'
    },
    prerender: {
      // SiteHeader links to /krueger/introduction/ and /krueger/api/, which
      // live in the Astro tree (mounted alongside this SvelteKit app at
      // build time), not in the SvelteKit prerender filesystem. Without
      // this, the crawler chases those links and fails the build with 404s
      // even though the deployed site at GitHub Pages serves them just
      // fine. The single SvelteKit route (`/`) is still emitted because
      // it's reachable from `entries`.
      crawl: false
    }
  }
};

export default config;
