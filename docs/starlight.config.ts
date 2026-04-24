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
  sidebar: [
    { label: 'Introduction', link: '/introduction/' },
    { label: 'Installation', link: '/installation/' },
    { label: 'Usage', link: '/usage/' },
    {
      label: 'API Reference',
      autogenerate: { directory: 'reference' },
    },
    { label: 'Examples', link: '/examples/' },
    { label: 'Contributing', link: '/contributing/' },
  ],
};

export default config;
