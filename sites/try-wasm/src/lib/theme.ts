/**
 * Shared theme helper for the SvelteKit playground.
 *
 * Mirrors Starlight's `<starlight-theme-select>` semantics: a three-state
 * preference (auto / dark / light) persisted to `localStorage` under the
 * key `starlight-theme`, applied to the document via the `data-theme`
 * attribute. Hooking the same key + attribute is what keeps the
 * playground in sync with the docs site without sharing a CSS bundle.
 *
 * The DOM mutation is observable via a MutationObserver on the `<html>`
 * element — that's how the Monaco editor bridges its `vs` / `vs-dark`
 * theme to the page-level preference.
 */

export type ThemePreference = 'auto' | 'dark' | 'light';
export type ResolvedTheme = 'dark' | 'light';

const STORAGE_KEY = 'starlight-theme';

const PREFERENCES: ReadonlySet<string> = new Set(['auto', 'dark', 'light']);

export function getStoredPreference(): ThemePreference {
  if (typeof localStorage === 'undefined') return 'auto';
  const raw = localStorage.getItem(STORAGE_KEY);
  return raw && PREFERENCES.has(raw) ? (raw as ThemePreference) : 'auto';
}

export function setStoredPreference(preference: ThemePreference): void {
  if (typeof localStorage === 'undefined') return;
  if (preference === 'auto') {
    localStorage.removeItem(STORAGE_KEY);
  } else {
    localStorage.setItem(STORAGE_KEY, preference);
  }
}

export function resolvePreference(preference: ThemePreference): ResolvedTheme {
  if (preference === 'dark' || preference === 'light') return preference;
  if (typeof window === 'undefined' || typeof window.matchMedia === 'undefined') {
    return 'dark';
  }
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

export function applyTheme(preference: ThemePreference): ResolvedTheme {
  const resolved = resolvePreference(preference);
  if (typeof document !== 'undefined') {
    document.documentElement.dataset.theme = resolved;
  }
  return resolved;
}

export function readResolvedTheme(): ResolvedTheme {
  if (typeof document === 'undefined') return 'dark';
  return document.documentElement.dataset.theme === 'light' ? 'light' : 'dark';
}

/**
 * Subscribe to changes of the resolved theme on `<html data-theme>`.
 * Returns an unsubscribe function. Fires once with the current value
 * immediately, then on every subsequent mutation. Used by the Monaco
 * editor to keep its own theme in sync.
 */
export function subscribeToResolvedTheme(callback: (theme: ResolvedTheme) => void): () => void {
  if (typeof document === 'undefined' || typeof MutationObserver === 'undefined') {
    return () => {};
  }
  callback(readResolvedTheme());
  const observer = new MutationObserver(() => callback(readResolvedTheme()));
  observer.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ['data-theme']
  });
  return () => observer.disconnect();
}
