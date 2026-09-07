# Third-party libraries

kmttg is distributed under the GNU General Public License v3 (see `release/LICENSE`).
It makes use of the following third-party libraries, with thanks to their authors.

## Downloaded from Maven Central

These are declared in `build.gradle` and fetched at build time (versions are the
`ext` properties there).

| Library | Version | License | Project |
| --- | --- | --- | --- |
| Apache HttpClient 5 | 5.6.1 | Apache License 2.0 | https://hc.apache.org/ |
| Apache HttpCore 5 | 5.4 (transitive) | Apache License 2.0 | https://hc.apache.org/ |
| Java-WebSocket | 1.6.0 | MIT License | https://github.com/TooTallNate/Java-WebSocket |
| FlatLaf | 3.7.1 | Apache License 2.0 | https://www.formdev.com/flatlaf/ |
| MigLayout (Swing) | 11.4.3 | BSD 3-Clause License | https://www.miglayout.com/ |
| jmDNS | 3.6.3 | Apache License 2.0 | https://github.com/jmdns/jmdns |
| Apache Commons Codec | 1.22.1 | Apache License 2.0 | https://commons.apache.org/proper/commons-codec/ |
| SLF4J API | 2.0.19 | MIT License | https://www.slf4j.org/ |
| Apache Commons CLI | 1.11.0 | Apache License 2.0 | https://commons.apache.org/proper/commons-cli/ |
| Logback Classic / Core | 1.6.3 | EPL 1.0 / LGPL 2.1 | https://logback.qos.ch/ |

Commons Codec and SLF4J come in transitively with TivoLibre. Commons CLI and Logback are there for
TivoLibre's `DecoderApp`, which kmttg runs as a separate process out of `kmttg.jar`; nothing in
kmttg compiles against them.

## TivoLibre, the TiVo recording decoder

TivoLibre is Todd Kulesza's project at https://github.com/fflewddur/tivolibre, itself derived from
Jeremy Drake's TivoDecode 0.4.4. kmttg consumes the maintained fork at
https://github.com/lart2150/tivolibre, which is published as `io.github.lart2150:tivo-libre` from
that repository's `maven` branch rather than to Maven Central. Same authorship, different
coordinate.

| Library | Version | License | Project |
| --- | --- | --- | --- |
| TivoLibre | 0.8.0 | GNU General Public License v3 | https://github.com/fflewddur/tivolibre |

## Bundled in `release/web/`

Served by the built-in web server and committed to the repository.

| Library | Version | License | Project |
| --- | --- | --- | --- |
| Pure CSS | 3.0.0 | BSD 3-Clause License | https://purecss.io/ |
| normalize.css | 8.0.1 (bundled in `pure-min.css`) | MIT License | https://necolas.github.io/normalize.css/ |

## Build tooling

| Tool | License | Project |
| --- | --- | --- |
| Gradle (wrapper) | Apache License 2.0 | https://gradle.org/ |

Full license texts are available from each project at the URLs above.
