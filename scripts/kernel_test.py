"""Run the kernel against an explicitly disposable database, never a deployment."""
import argparse, hashlib, json, os, platform, re, subprocess, time, uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
IMAGE = 'postgres@sha256:a426e44bac0b759c95894d68e1a0ac03ecc20b619f498a91aae373bf06d8508d'
parser = argparse.ArgumentParser()
parser.add_argument('--existing-disposable', action='store_true', help='CI-managed disposable database; explicit opt-in environment required')
args = parser.parse_args()
environment = os.environ.copy()
container = None
def run(*arguments, **kwargs):
    return subprocess.run(list(arguments), cwd=ROOT, check=True, text=True, encoding='utf-8', **kwargs)
try:
    if args.existing_disposable:
        assert environment.get('TASKCTL_KERNEL_DISPOSABLE') == '1'
        assert all(environment.get(key) for key in ('TASKCTL_KERNEL_JDBC_URL', 'TASKCTL_KERNEL_USER', 'TASKCTL_KERNEL_PASSWORD'))
    else:
        name = 'taskctl-kernel-test-' + uuid.uuid4().hex[:12]
        container = run('docker', 'run', '--rm', '--detach', '--name', name,
            '--label', 'io.brule.taskctl.experiment=remote-kernel',
            '-e', 'POSTGRES_PASSWORD=kernel-test-only', '-p', '127.0.0.1::5432', IMAGE, capture_output=True).stdout.strip()
        assert re.fullmatch('[0-9a-f]{64}', container)
        details = json.loads(run('docker', 'inspect', container, capture_output=True).stdout)[0]
        assert details['Config']['Labels']['io.brule.taskctl.experiment'] == 'remote-kernel'
        port = details['NetworkSettings']['Ports']['5432/tcp'][0]
        assert port['HostIp'] == '127.0.0.1'
        environment.update(TASKCTL_KERNEL_DISPOSABLE='1', TASKCTL_KERNEL_JDBC_URL=f'jdbc:postgresql://127.0.0.1:{int(port["HostPort"])}/postgres',
                           TASKCTL_KERNEL_USER='postgres', TASKCTL_KERNEL_PASSWORD='kernel-test-only')
        deadline = time.monotonic() + 60
        while subprocess.run(['docker', 'exec', container, 'pg_isready', '-U', 'postgres'], capture_output=True).returncode:
            if time.monotonic() > deadline: raise TimeoutError('disposable PostgreSQL did not become ready')
            time.sleep(1)
    gradle = ['cmd.exe', '/d', '/c', 'gradlew.bat'] if os.name == 'nt' else ['./gradlew']
    run(*gradle, ':kernel:test', ':kernel:integrationTest', ':kernel:jar', '--no-daemon', '--console=plain', '--max-workers=1', env=environment, timeout=600)
    proof = ROOT / 'build/proof'
    jar = next((ROOT / 'kernel/build/libs').glob('kernel-*.jar'))
    observation = dict(classification='observed-disposable-kernel-harness', database_image=IMAGE,
        source=run('git', 'rev-parse', 'HEAD', capture_output=True).stdout.strip(),
        source_dirty=bool(run('git', 'status', '--porcelain', capture_output=True).stdout.strip()),
        host=platform.system(), kernel_jar_sha256=hashlib.sha256(jar.read_bytes()).hexdigest(),
        jdbc_version='42.7.13', managed_container=not args.existing_disposable)
    (proof / 'kernel-harness.json').write_text(json.dumps(observation, indent=2) + '\n', encoding='utf-8', newline='\n')
finally:
    if container:
        assert re.fullmatch('[0-9a-f]{64}', container)
        details = json.loads(run('docker', 'inspect', container, capture_output=True).stdout)[0]
        assert details['Config']['Labels']['io.brule.taskctl.experiment'] == 'remote-kernel'
        run('docker', 'rm', '--force', container, capture_output=True)
        print('Disposed the exact test container; no persistent database or service remains.')
