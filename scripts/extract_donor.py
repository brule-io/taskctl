"""Reproduce the historical extraction from an exact local Git object.

Never runs or edits the donor build. Original source bytes and provenance are
retained in an ignored oracle tree for the independent parity comparison.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
REVISION = "b932b0eecbc2b6053b3f2235ad2963fd31fbb4c4"

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("donor", type=Path)
    args = parser.parse_args()
    def git(*parts):
        return subprocess.check_output(["git", "-C", str(args.donor), *parts])
    paths = git("ls-tree", "-r", "--name-only", REVISION, "tooling/workflow/src", ".agents", "gradle/wrapper", "gradlew", "gradlew.bat").decode().splitlines()
    manifest = []
    for path in paths:
        data = git("show", f"{REVISION}:{path}")
        if path.startswith("tooling/workflow/src/"):
            tail = path.removeprefix("tooling/workflow/")
            if "/adr/" in tail or tail.endswith("/AdrCtl.kt"):
                continue
            # Git is used only by invariant tests to observe HEAD/index; it is
            # deliberately absent from the shipped task-control runtime.
            if tail.endswith("/GitClient.kt"):
                tail = tail.replace("src/main/", "src/test/")
            dest = ROOT / "compatibility" / tail
            oracle = ROOT / ".proof/oracle" / tail
            for target in (dest, oracle):
                target.parent.mkdir(parents=True, exist_ok=True)
                if target.exists():
                    raise SystemExit(f"refusing to overwrite {target.relative_to(ROOT)}")
                target.write_bytes(data)
            manifest.append({"source_path": path, "sha256": hashlib.sha256(data).hexdigest(), "extracted_path": str(dest.relative_to(ROOT)).replace('\\','/')})
        elif path.startswith(".agents/"):
            dest = ROOT / "conformance/src/test/resources/fantastikt" / path
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes(data)
        else:
            dest = ROOT / path
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes(data)
    (ROOT / "provenance").mkdir(exist_ok=True)
    (ROOT / "provenance/fantastikt.json").write_text(json.dumps({
        "schema": "tasking.extraction-source/v1",
        "repository": "https://github.com/brule-io/fantastikt",
        "revision": REVISION,
        "adapter": "fantastikt-loom-agent-2026",
        "adapter_version": "0.1.0-dev.1",
        "files": manifest,
    }, indent=2) + "\n", encoding="utf-8")
    print(f"Extracted {len(manifest)} source/test files; donor unchanged.")

if __name__ == "__main__":
    main()
