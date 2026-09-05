# Reference generator

This small Python client demonstrates the bootstrap contract; it is not a
universal project generator. It invokes the extracted release's `init` command,
passes through the selected profile and optional explicit seed, then writes its
own Python source skeleton. It has no copied tasking records, templates or launcher
implementation, and does not inspect taskctl's internal filesystem layout.

```sh
python generate.py ./example --id brule.example \
  --taskctl /absolute/path/to/release/taskctl \
  --toolchain /absolute/path/to/toolchain.lock
cd example
./taskctl doctor
./taskctl frontier
```

On Windows pass the release's `taskctl.ps1` as `--taskctl` and use the generated
`taskctl.ps1` interface. Python is a dependency of this example generator only;
the generated repository's taskctl commands need neither Python nor source builds.

The [bootstrap contract](../../docs/BOOTSTRAP.md) defines the versioned command and
result. The packaged integration suite runs this client and verifies the generated
project through its local wrapper, using an optional caller-authored seed.
