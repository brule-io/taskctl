"""Prepare the exact donor CLI in an isolated build for parity comparisons."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('donor', type=Path)
    args = parser.parse_args()
    provenance = json.loads((ROOT / 'provenance/fantastikt.json').read_text())
    oracle = ROOT / '.proof/oracle'
    for entry in provenance['files']:
        data = subprocess.check_output(['git', '-C', str(args.donor), 'show', provenance['revision'] + ':' + entry['source_path']])
        assert hashlib.sha256(data).hexdigest() == entry['sha256']
        target = oracle / entry['extracted_path'].removeprefix('compatibility/')
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
    (oracle / 'settings.gradle.kts').write_text('rootProject.name = "fantastikt-oracle"\n')
    (oracle / 'build.gradle.kts').write_text('''plugins { kotlin("jvm") version "2.4.10"; application }
repositories { mavenCentral() }
kotlin { jvmToolchain(21) }
application { mainClass.set("io.brule.workflow.cli.TaskCtlKt") }
dependencies {
    implementation("com.github.ajalt.clikt:clikt:5.1.0")
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")
}
tasks.test { useJUnitPlatform() }
''')
    print('Prepared exact donor sources in .proof/oracle; no donor writes.')

if __name__ == '__main__':
    main()
