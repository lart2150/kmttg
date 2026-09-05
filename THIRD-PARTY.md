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

## Bundled in `lib/tivo-libre.jar`

`tivo-libre.jar` is a kmttg-specific TiVo recording decoder that is not published to
Maven Central, so it is committed to the repository. It is an "uber" jar that also
bundles the following libraries:

| Library | Version | License | Project |
| --- | --- | --- | --- |
| TivoLibre | (bundled) | refer to project | https://github.com/fflewddur/tivolibre |
| Apache Commons CLI | 1.3.1 | Apache License 2.0 | https://commons.apache.org/proper/commons-cli/ |
| Apache Commons Codec | 1.10 | Apache License 2.0 | https://commons.apache.org/proper/commons-codec/ |
| SLF4J API | 1.7.21 | MIT License | https://www.slf4j.org/ |
| Logback Classic | 1.1.7 | EPL 1.0 / LGPL 2.1 | https://logback.qos.ch/ |
| Logback Core | 1.1.7 | EPL 1.0 / LGPL 2.1 | https://logback.qos.ch/ |

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

Full license texts are available from each project at the URLs above. The Apache
Commons license/notice files are also included inside `tivo-libre.jar` under
`META-INF/`.
