"""Bundle consumer guides; bind unbundled source/evidence links to an exact commit."""
import re
from pathlib import Path
from urllib.parse import quote, unquote, urlsplit


def render_document(root, path, included, revision):
    root = root.resolve()
    if not re.fullmatch(r'[0-9a-f]{40}', revision):
        raise ValueError('documentation links require an exact source commit')
    source = path.read_text(encoding='utf-8')

    def replace(match):
        target = match.group(1)
        parsed = urlsplit(target)
        if parsed.scheme or parsed.netloc or not parsed.path:
            return match.group(0)
        resolved = (path.parent / unquote(parsed.path)).resolve()
        if not resolved.is_relative_to(root) or not resolved.exists():
            raise ValueError(f'missing or external relative guide link in {path.name}: {target}')
        relative = resolved.relative_to(root).as_posix()
        if relative in included:
            return match.group(0)
        kind = 'tree' if resolved.is_dir() else 'blob'
        url = f'https://github.com/brule-io/taskctl/{kind}/{revision}/{quote(relative, safe="/")}'
        if parsed.fragment:
            url += '#' + parsed.fragment
        return '](' + url + ')'

    # Leave fenced examples and inline code verbatim; they can contain syntax
    # such as timestamp [fractions](offsets) that is not a Markdown link.
    chunks = re.split(r'(```[\s\S]*?```|~~~[\s\S]*?~~~|`[^`\n]*`)', source)
    return ''.join(chunk if index % 2 else re.sub(r'\]\(([^)]+)\)', replace, chunk)
                   for index, chunk in enumerate(chunks))


def write_guides(root, stage, revision):
    documents = [root / 'README.md', *sorted((root / 'docs').glob('*.md')),
                 *sorted((root / 'docs/spec').glob('*.md'))]
    index = root / 'docs/spec/candidate.json'
    included = {'LICENSE', 'NOTICE.md', 'docs/spec/candidate.json'} | {
        path.relative_to(root).as_posix() for path in documents}
    for path in documents:
        target = stage / path.relative_to(root)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(render_document(root, path, included, revision), encoding='utf-8', newline='\n')
    (stage / 'docs/spec/candidate.json').write_bytes(index.read_bytes())
