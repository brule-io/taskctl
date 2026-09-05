"""Acquire exact canonical GitLab blobs without hydrating or editing checkouts."""
import argparse
import hashlib
import json
from pathlib import Path
import urllib.error
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
SOURCES = [
    ("daemon-eng/os/daemon-os-workspace", "68e32a8b87932f6d69d34cc28f50b9b4102f1a79", ".agents/tasks/closed/process/TASK.process.006.platform-thin-ci-gradle-repo-reset-doctrine.md", "daemon-workspace-prose/2026"),
    ("daemon-eng/os/daemon-os-workspace", "68e32a8b87932f6d69d34cc28f50b9b4102f1a79", ".agents/tasks/closed/process/TASK.process.006.workflow-canon-backport.md", "daemon-workspace-prose/2026"),
    ("daemon-eng/net/daemon-net-workspace", "46c13b1d3a5223096d9d90a2ea0a4849bc9e7bea", ".agents/tasks/closed/TASK.M2.nginx.md", "daemon-net-triage/2026"),
]

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--token-file", required=True, type=Path)
    args = parser.parse_args()
    token = args.token_file.read_text(encoding="utf-8-sig").strip()
    if not token or "\n" in token or "\r" in token:
        raise SystemExit("Invalid credential file shape")
    out = ROOT / "conformance/src/test/resources/daemon"
    out.mkdir(parents=True, exist_ok=True)
    records = []
    for project, revision, source_path, dialect in SOURCES:
        quote = lambda value: urllib.parse.quote(value, safe="")
        endpoint = f"https://gitlab.com/api/v4/projects/{quote(project)}/repository/files/{quote(source_path)}/raw?ref={revision}"
        request = urllib.request.Request(endpoint, headers={"PRIVATE-TOKEN": token})
        try:
            with urllib.request.urlopen(request, timeout=45) as response:
                data = response.read()
        except urllib.error.HTTPError as failure:
            raise SystemExit(f"GitLab read failed for {project}: HTTP {failure.code}") from None
        except urllib.error.URLError:
            raise SystemExit(f"GitLab transport failed for {project}") from None
        name = Path(source_path).name
        (out / name).write_bytes(data)
        records.append({"file": name, "repository": f"https://gitlab.com/{project}", "revision": revision,
                        "source_path": source_path, "source_sha256": hashlib.sha256(data).hexdigest(),
                        "adapter": dialect, "adapter_version": "0.1.0-dev.1", "source_state": "closed",
                        "evidence_class": "historical-narrative", "native_protocol": False})
    (out / "sources.json").write_text(json.dumps({"schema": "tasking.fixture-provenance/v1", "records": records}, indent=2) + "\n", encoding="utf-8")
    print(f"Acquired {len(records)} exact GitLab source blobs; credentials not persisted.")

if __name__ == "__main__":
    main()
