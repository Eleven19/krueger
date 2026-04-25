/**
 * Client-side bootstrap for the `/try/` Laminar playground page.
 *
 * Runs in the browser only. Three responsibilities:
 *
 *   1. Configure `self.MonacoEnvironment` so monaco-editor can spin up its
 *      editor worker using a Vite-processed `?worker` import. Without this,
 *      Monaco throws "Could not create web worker(s)" at first editor
 *      instantiation.
 *   2. Dynamically import the Scala.js-emitted webapp ESModule, which is
 *      materialised by `./mill krueger.webapp.writeToDocsSrc` into
 *      `src/playground/generated/app/main.js`. A *dynamic* import is used so
 *      that `self.MonacoEnvironment` is installed before monaco-editor's
 *      main module is evaluated — ES module imports are otherwise hoisted and
 *      would race this assignment.
 *   3. Call the Scala.js-exported `mount(selector)` against
 *      `#try-krueger-root` to hand control to Laminar.
 *
 * Error paths surface on `console.error` with enough breadcrumbs to diagnose
 * the common failure modes (missing generated bundle, Monaco worker failure,
 * root element not found). The page itself also renders a `<noscript>`
 * fallback so users with JS disabled see a readable message.
 */

import EditorWorker from 'monaco-editor/esm/vs/editor/editor.worker?worker';

// Tell monaco-editor how to construct its workers. For the MVP we only
// register the generic `editorWorker`; language-service workers (TS, CSS,
// JSON, HTML) are not needed because the Elm language is contributed via
// a purely in-process Monarch tokenizer — see
// [ElmLanguage](../../krueger/webapp/src/io/eleven19/krueger/webapp/monaco/ElmLanguage.scala).
declare global {
    // eslint-disable-next-line no-var
    var MonacoEnvironment: { getWorker: (workerId: string, label: string) => Worker };
}

self.MonacoEnvironment = {
    getWorker(_workerId, _label) {
        return new EditorWorker();
    },
};

const ROOT_SELECTOR = '#try-krueger-root';

async function boot(): Promise<void> {
    const root = document.querySelector(ROOT_SELECTOR);
    if (root == null) {
        console.error(
            `try/bootstrap: could not find ${ROOT_SELECTOR} — page HTML is out of sync with this script.`
        );
        return;
    }

    try {
        // The path is relative to this file; Vite resolves it at build time
        // and emits a hashed bundle. The generated/ directory is populated by
        // Mill and gitignored; if the file is missing the dynamic import
        // throws a module-not-found error that is surfaced below.
        const mod = (await import('./generated/app/main.js')) as {
            mount?: (selector: string) => void;
        };

        if (typeof mod.mount !== 'function') {
            console.error(
                "try/bootstrap: generated webapp module did not export `mount` — did `@JSExportTopLevel(\"mount\")` change?"
            );
            return;
        }

        mod.mount(ROOT_SELECTOR);
    } catch (err) {
        console.error(
            'try/bootstrap: failed to mount Laminar playground. Run `./mill krueger.webapp.writeToDocsSrc` to regenerate the Scala.js bundle.',
            err
        );
    }
}

void boot();
