<script lang="ts">
  import { base } from '$app/paths';

  /**
   * Companion to `docs/src/components/Header.astro` — same visual layout
   * (logo + title at left, nav pushed right with a vertical separator
   * before the GitHub link), minus the search input which doesn't belong
   * on a playground surface.
   *
   * Cross-app URLs derive from SvelteKit's `base` so they resolve under
   * `/krueger/...` in the production GitHub Pages tree:
   *   prod base = "/krueger/try"  -> sibling root = "/krueger"
   *   dev base  = ""              -> sibling root = ""
   * (cross-app links won't resolve in `vite dev` of try-wasm in isolation
   * — Astro is the canonical host. Run `mill docs.prepareLocalDevSite`
   * + `astro dev` for a local mirror.)
   */

  const siblingsRoot = base.replace(/\/try$/, '');
  const repoUrl = 'https://github.com/Eleven19/krueger';

  const docsHref = `${siblingsRoot}/introduction/`;
  const tryHref = `${base}/`;
  const apiHref = `${siblingsRoot}/api/`;
  const logoHref = `${base}/favicon.svg`;
</script>

<!--
  `data-sveltekit-reload` tells SvelteKit's router (and the prerender
  crawler) that the link leaves the SvelteKit app boundary and should be
  followed by a full-page load rather than client-side navigation.
  Without it the prerenderer crawls /krueger/introduction/ and /krueger/api/
  and fails the build with 404s — those routes live in the Astro tree, not
  the SvelteKit one.
-->
<header class="site-header" aria-label="Site">
  <div class="title-wrapper">
    <a class="site-title" href={`${siblingsRoot}/`} data-sveltekit-reload>
      <img class="site-logo" src={logoHref} alt="" width="32" height="32" />
      <span>Krueger</span>
    </a>
  </div>

  <nav class="primary-nav" aria-label="Primary">
    <a class="nav-link" href={docsHref} data-sveltekit-reload>Docs</a>
    <a class="nav-link" data-active="true" href={tryHref}>Try</a>
    <a class="nav-link" href={apiHref} data-sveltekit-reload>API</a>
  </nav>

  <div class="right-group">
    <a
      class="social-link"
      href={repoUrl}
      rel="noopener"
      aria-label="Krueger on GitHub"
    >
      <svg
        aria-hidden="true"
        width="20"
        height="20"
        viewBox="0 0 24 24"
        fill="currentColor"
      >
        <path
          d="M12 .3a12 12 0 0 0-3.8 23.38c.6.12.83-.26.83-.57L9 21.07c-3.34.72-4.04-1.61-4.04-1.61-.55-1.39-1.34-1.76-1.34-1.76-1.08-.74.09-.73.09-.73 1.2.09 1.83 1.24 1.83 1.24 1.08 1.83 2.81 1.3 3.5 1 .1-.78.42-1.31.76-1.61-2.67-.3-5.47-1.33-5.47-5.93 0-1.31.47-2.38 1.24-3.22-.14-.3-.54-1.52.1-3.18 0 0 1-.32 3.3 1.23a11.5 11.5 0 0 1 6 0c2.28-1.55 3.29-1.23 3.29-1.23.64 1.66.24 2.88.12 3.18a4.65 4.65 0 0 1 1.23 3.22c0 4.61-2.8 5.63-5.48 5.92.42.36.81 1.1.81 2.22l-.01 3.29c0 .31.2.69.82.57A12 12 0 0 0 12 .3Z"
        />
      </svg>
    </a>
  </div>
</header>

<style>
  .site-header {
    display: flex;
    align-items: center;
    gap: 1.5rem;
    height: var(--kr-header-h, 3.75rem);
    padding: 0 1rem;
    background: var(--kr-panel-bg-strong);
    border-bottom: 1px solid var(--kr-border);
  }

  .title-wrapper {
    flex: 0 0 auto;
  }

  .site-title {
    display: inline-flex;
    align-items: center;
    gap: 0.625rem;
    color: var(--kr-text);
    font-weight: 600;
    font-size: 1.05rem;
    text-decoration: none;
  }

  .site-logo {
    width: 1.75rem;
    height: 1.75rem;
    border-radius: 0.375rem;
  }

  .primary-nav {
    margin-inline-start: auto;
    display: flex;
    align-items: center;
    gap: 1.25rem;
    font-size: 0.875rem;
  }

  .nav-link {
    display: inline-flex;
    align-items: center;
    padding: 0.25rem 0.5rem;
    color: var(--kr-muted);
    text-decoration: none;
    border-radius: 0.25rem;
    white-space: nowrap;
  }

  .nav-link:hover {
    color: var(--kr-text);
    background: var(--kr-border);
  }

  .nav-link[data-active='true'] {
    color: var(--kr-text);
    font-weight: 600;
  }

  .right-group {
    display: flex;
    align-items: center;
    gap: 1rem;
  }

  /* Vertical separator between the primary nav and the trailing GitHub
     icon — matches the Starlight Header override on the docs site. */
  .right-group::before {
    content: '';
    align-self: center;
    height: 2rem;
    border-inline-start: 1px solid var(--kr-border);
  }

  .social-link {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 2rem;
    height: 2rem;
    color: var(--kr-muted);
    border-radius: 0.25rem;
  }

  .social-link:hover {
    color: var(--kr-text);
    background: var(--kr-border);
  }
</style>
