# License decision pending

The owner has not selected a software license for taskctl. No project license
is granted by this file. Repository visibility and software licensing are separate
decisions.

Distributions include third-party components whose existing licenses remain in
their JAR metadata and, for JVM archives, the bundled Java runtime's `legal`
directory. `THIRD-PARTY-NOTICES.zip` preserves dependency notices separately;
native archives also include GraalVM's legal notices there, since the original
JAR/runtime containers are not shipped. Dependency
versions are locked in the source tree; distribution manifests identify the exact
bundled artifacts. Eclipse Temurin runtime sources are maintained at
https://github.com/adoptium/temurin21-binaries and https://github.com/openjdk/jdk21u.
Native Image toolchain identities and exact source/build inputs are recorded in
the distribution manifest. GraalVM Community sources and releases are maintained
at https://github.com/oracle/graal and https://github.com/graalvm/graalvm-ce-builds.
