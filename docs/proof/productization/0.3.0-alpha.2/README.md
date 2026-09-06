# 0.3.0-alpha.2 publication proof

[Canonical release](https://github.com/brule-io/taskctl/releases/tag/v0.3.0-alpha.2)
was published privately and immutably from source
`8933bac1c090d175dfc035d7d52a40d1101f5333`. All 28 asset digests and the source tag
were checked against the signed immutable release attestation; raw verification is
retained here. No separate CI OIDC build signature or SLSA level is claimed.

[Clean build CI](https://github.com/brule-io/taskctl/actions/runs/34063281606) and
[real GitHub transport CI](https://github.com/brule-io/taskctl/actions/runs/34064510580)
passed on Windows x86_64, Linux x86_64 and macOS arm64. Each platform passed the
same 95 behavioral tests on JVM and Native Image, three additional JVM source-policy
tests, 111 full process comparisons, 25 bootstrap checks per implementation, and
seven reads of the actual self-host graph per implementation. Transport CI repeated
bootstrap and process comparisons against the exact release URLs. See `ci/` and
`transport/`; no binary artifacts are duplicated in this proof directory.

| Platform | Implementation | Archive SHA-256 |
| --- | --- | --- |
| windows-x86_64 | native | `2922f715e64f9f27e3cd0b2de5e48a6fc3e89f1dd19accc8cc1cc3096202d31d` |
| windows-x86_64 | jvm | `29d394c1f43b799d7a595c8a6951881fdab7e6718b3ccdfab3c012e129d469a9` |
| linux-x86_64 | native | `6f8607c727e1cabfe00c680d0872aa74b4eb890cb37daf2b4bfb2b10f076d5a3` |
| linux-x86_64 | jvm | `094b85f40bda18ca4bc5250c3ec0d7af1ad312b5f53d5c1392811fa837ad1a97` |
| macos-aarch64 | native | `1e4e6868286d64e37d1d4fbe2b352b02d91b6b7c12dcb481ed4cddb005b4a319` |
| macos-aarch64 | jvm | `04c864715e3332ae0f043f76b533593f4c71ee849f450513346a47f3ef232ac8` |

The native lock SHA-256 is `a73195736ee727f70608cfdcde58e4f07491abf5635e7fbffcc8725cad254f99`.
`release-manifest.json` and archive provenance statements bind implementation,
GraalVM/toolchain/compiler/runtime identity, library and license digests.

The new reviewed Fantastikt import uses native alpha3 and origin-bearing revision/2
objects. Existing alpha1/alpha2 contracts and revision/1 identities keep their
meanings. Import creates no native receipts; historical closures and subsequent
actor reviews remain distinct. Native v1 is not frozen. This release adds no service
and does not migrate other consumers. The separately published alpha.1 Fedora RPM
remains available; no new RPM release is claimed here.

`selfhost-pin.json` records acquisition and inspection through the producer's
updated native pin, before closing the consumer-migration task. The final consumer
acceptance is recorded separately in the Fantastikt migration proof.
