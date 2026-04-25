import type { StarlightUserConfig } from '@astrojs/starlight/types';

const config: StarlightUserConfig = {
  title: 'Krueger',
  description:
    'An Elm dialect parser and compiler toolchain for Scala — JVM, Scala.js, and Scala Native.',
  logo: {
    src: './public/favicon.svg',
    replacesTitle: false,
  },
  social: [
    {
      icon: 'github',
      label: 'GitHub',
      href: 'https://github.com/Eleven19/krueger',
    },
  ],
  editLink: {
    baseUrl: 'https://github.com/Eleven19/krueger/edit/main/docs/',
  },
  customCss: ['./src/styles/custom.css'],
  components: {
    Header: './src/components/Header.astro',
  },
  // Note: the generated Scaladoc trees under `docs/public/api/{jvm,js,native}/`
  // are excluded from the Pagefind search index by stamping each emitted
  // HTML file with `data-pagefind-ignore="true"` during the Mill task
  // `docs.writeToDocsPublic`. Pagefind's own `excludeSelectors` is scoped to
  // DOM elements within indexed pages, not whole routes, so per-page opt-out
  // via the HTML attribute is the documented mechanism for whole-page exclusion.
  sidebar: [
    { label: 'Introduction', link: '/introduction/' },
    { label: 'Installation', link: '/installation/' },
    { label: 'Usage', link: '/usage/' },
    { label: 'Tooling', link: '/tooling/' },
    {
      label: 'API Reference',
      items: [
        { label: 'Overview', link: '/reference/api/' },
        {
          label: 'Full Scaladoc',
          link: '/api/',
          attrs: { target: '_blank', rel: 'noopener' },
        },
      ],
    },
    { label: 'Examples', link: '/examples/' },
    { label: 'Contributing', link: '/contributing/' },
  ],
};

export default config;
