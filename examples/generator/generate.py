"""Reference composition client. Owns project source, never tasking templates."""
import argparse, json, os, subprocess, sys
from pathlib import Path

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('destination',type=Path)
    parser.add_argument('--id',required=True)
    parser.add_argument('--taskctl',type=Path,required=True,help='Standalone taskctl or taskctl.ps1 from an extracted release')
    parser.add_argument('--toolchain',type=Path,required=True)
    parser.add_argument('--profile',default='minimal/alpha1')
    parser.add_argument('--seed',type=Path)
    args=parser.parse_args()
    destination=args.destination.absolute()
    if destination.exists(): parser.error('reference generator requires a new destination')
    executable=args.taskctl.absolute()
    if executable.suffix=='.ps1':
        shell=Path(os.environ['SystemRoot'])/'System32/WindowsPowerShell/v1.0/powershell.exe'
        command=[str(shell),'-NoLogo','-NoProfile','-ExecutionPolicy','Bypass','-File',str(executable)]
    else: command=[str(executable)]
    inputs=['init','--contract','taskctl.init/alpha1','--repo',str(destination),'--id',args.id,
            '--profile',args.profile,'--toolchain',str(args.toolchain.absolute()),'--format','json']
    if args.seed: inputs+=['--seed',str(args.seed.absolute())]
    completed=subprocess.run(command+inputs,capture_output=True,text=True,encoding='utf-8')
    if completed.returncode:
        sys.stdout.write(completed.stdout); sys.stderr.write(completed.stderr)
        return completed.returncode
    result=json.loads(completed.stdout)
    if result.get('api')!='taskctl.cli/alpha1' or result['result'].get('contract')!='taskctl.init/alpha1':
        raise RuntimeError('unsupported initialization result contract')
    # Only the generator's own source skeleton is authored here. taskctl already
    # initialized its substrate and validated the caller-supplied plan.
    (destination/'src').mkdir()
    (destination/'src/main.py').write_text('def greeting(name):\n    return f"Hello, {name}!"\n',encoding='utf-8',newline='\n')
    (destination/'README.md').write_text('# Generated project\n\nRead AGENTS.md and run the repository taskctl doctor to begin.\n',encoding='utf-8',newline='\n')
    print(json.dumps(dict(contract='taskctl.reference-generator/1',destination=str(destination),initialization=result['result'],source_files=['src/main.py','README.md'])))
    return 0

if __name__=='__main__': sys.exit(main())
