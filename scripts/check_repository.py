#!/usr/bin/env python3

from pathlib import Path
import re
import subprocess
import sys
from urllib.parse import unquote, urlsplit

def main() -> int:
    root = Path(__file__).resolve().parents[1]
    tracked = subprocess.check_output(["git", "-C", str(root), "ls-files", "-z"]).decode().split("\0")
    errors = []
    for name in filter(None, tracked):
        path = Path(name)
        if (path.name == ".DS_Store" or path.name.startswith("._")
                or (path.name == ".env" or path.name.startswith(".env.")) and path.name != ".env.example"
                or path.parts[0] in {".aws", ".codex", ".agents", "backups", "target"}
                or path.suffix.lower() in {".pem", ".key", ".p12", ".pfx", ".log", ".dump", ".backup"}):
            errors.append(f"Unexpected tracked local/private/generated file: {name}")

    documents = sorted(root.glob("*.md")) + sorted((root / "docs").rglob("*.md"))
    for document in documents:
        for number, line in enumerate(document.read_text(encoding="utf-8").splitlines(), 1):
            for match in re.finditer(r"\]\((<[^>]+>|[^\s)]+)(?:\s+\"[^\"]*\")?\)", line):
                target = match.group(1).strip("<>")
                parsed = urlsplit(target)
                if parsed.scheme or parsed.netloc or not parsed.path:
                    continue
                path = document.parent / unquote(parsed.path)
                if not path.exists():
                    errors.append(f"{document.relative_to(root)}:{number}: Missing linked file: {target}")

    for error in errors:
        print(error, file=sys.stderr)
    if errors:
        return 1
    print(f"Repository hygiene OK; local file links checked in {len(documents)} Markdown documents.")
    return 0

if __name__ == "__main__":
    sys.exit(main())
