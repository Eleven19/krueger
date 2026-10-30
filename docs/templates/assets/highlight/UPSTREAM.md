# highlight.js upstream

These files come unchanged from `@highlightjs/cdn-assets` 11.12.0 under the
[BSD 3-Clause license](LICENSE). The shared templates use local files so design
pages also work without a network connection.

The runtime and GitHub light/dark styles match the copies in
finos/morphir-moonbit at commit `db639506585e38326810722d4951e71f43c6c4f8`.
The Scala and Elm grammars come from the npm tarball for that same version.

The npm tarball's SHA-512 integrity was verified before extracting the grammars:

```text
sha512-KvOKXODaiFmId9xaq3xc5xCL66wVLUuOngDbO9B/kewbFTqdGbn2nJxNhN3H5R1cgDTVj6R8vH0zgiNDEGjpDw==
```

| Grammar | SHA-256 |
| --- | --- |
| `languages/scala.min.js` | `2d70612619556e1d39566e9d23bc0e890a75ec027911eca0d6ab7987430b201a` |
| `languages/elm.min.js` | `62187fe6b3eb0cfaea1961aad47d24801b58a4791f721fbdf3d75ec010177ec0` |

To upgrade, replace the files from a pinned version of
[`@highlightjs/cdn-assets`](https://www.npmjs.com/package/@highlightjs/cdn-assets),
verify its integrity, update this record and check both HTML template heads.
