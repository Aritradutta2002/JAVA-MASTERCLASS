#!/usr/bin/env python3
"""Validate SYSTEM_DESIGN structure using only the Python standard library."""
from __future__ import annotations
import re
import sys
from collections import Counter
from pathlib import Path
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[1]
ERRORS: list[str] = []
WARNINGS: list[str] = []
SECTION_DIRS = [
    "01-System-Design-Fundamentals", "02-Distributed-Systems",
    "03-Database-Design", "04-Caching", "05-Messaging-and-Event-Driven-Systems",
    "06-API-and-Communication-Design", "07-Security-Design",
    "08-Microservices-Architecture", "09-Cloud-and-Infrastructure-Design",
    "10-Observability-and-Reliability-Engineering", "11-HLD-Case-Studies",
    "12-LLD-Preparation", "13-Interview-Practice",
]
ROOT_FILES = ["README.md", "QUICK-REFERENCE.md", "STANDARD.md", "CURRICULUM.md",
              "LEARNING-PATHS.md", "GLOSSARY.md", "INTERVIEW-CHECKLIST.md"]
PLACEHOLDER = re.compile(r"\b(?:TODO|TBD|FIXME|coming soon|to be added|placeholder content)\b", re.I)
LINK_RE = re.compile(r"(?<!!)\[[^\]]*\]\(([^)]+)\)")
HEADING_RE = re.compile(r"^(#{1,6})\s+(.+?)\s*#*$")
FENCE_RE = re.compile(r"^```\s*([^\s`]*)")

def slugify(text: str) -> str:
    text = re.sub(r"<[^>]+>", "", text).lower().strip()
    text = text.replace("—", "").replace("–", "")
    text = re.sub(r"[^\w\- ]", "", text, flags=re.UNICODE)
    return text.replace(" ", "-")

def without_fenced_examples(text: str) -> str:
    output: list[str] = []
    fenced = False
    for line in text.splitlines():
        if FENCE_RE.match(line):
            fenced = not fenced
            continue
        if not fenced:
            output.append(line)
    return "\n".join(output)

def add_error(message: str) -> None: ERRORS.append(message)
def add_warning(message: str) -> None: WARNINGS.append(message)

def markdown_files() -> list[Path]: return sorted(ROOT.rglob("*.md"))

def check_required_structure() -> None:
    for name in ROOT_FILES:
        if not (ROOT / name).is_file(): add_error(f"missing required root file: {name}")
    for name in SECTION_DIRS:
        directory = ROOT / name
        if not directory.is_dir(): add_error(f"missing section directory: {name}")
        elif not (directory / "README.md").is_file(): add_error(f"missing section README: {name}/README.md")

def check_markdown(path: Path) -> None:
    try: text = path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        add_error(f"not UTF-8: {path.relative_to(ROOT)}"); return
    lines = text.splitlines(); headings: list[str] = []; in_fence = False
    fence_start = 0
    for number, line in enumerate(lines, 1):
        fence = FENCE_RE.match(line)
        if fence:
            if not in_fence: in_fence = True; fence_start = number
            else: in_fence = False
            continue
        if not in_fence:
            heading = HEADING_RE.match(line)
            if heading: headings.append(slugify(heading.group(2)))
    if in_fence: add_error(f"unclosed code fence from line {fence_start}: {path.relative_to(ROOT)}")
    for heading, count in Counter(headings).items():
        if count > 1 and heading: add_warning(f"duplicate heading '{heading}' ({count}x): {path.relative_to(ROOT)}")
    prose = without_fenced_examples(text)
    for match in PLACEHOLDER.finditer(prose):
        line = prose.count("\n", 0, match.start()) + 1
        add_error(f"placeholder marker '{match.group(0)}' at {path.relative_to(ROOT)}:{line}")
    anchors = set(headings)
    for match in LINK_RE.finditer(prose):
        target = match.group(1).strip()
        if not target or target.startswith(("#", "http://", "https://", "mailto:", "tel:")): continue
        parsed = urlsplit(target); file_part = unquote(parsed.path)
        if not file_part: continue
        resolved = (path.parent / file_part).resolve()
        if not resolved.exists(): add_error(f"broken link '{target}' in {path.relative_to(ROOT)}")
        if parsed.fragment and resolved.suffix.lower() == ".md":
            try: target_text = resolved.read_text(encoding="utf-8")
            except (OSError, UnicodeDecodeError): continue
            target_anchors = {slugify(m.group(2)) for m in (HEADING_RE.match(line) for line in target_text.splitlines()) if m}
            if unquote(parsed.fragment).lower() not in target_anchors: add_error(f"broken anchor '{target}' in {path.relative_to(ROOT)}")
    if path.name != "README.md" and path.parent != ROOT:
        relative = path.relative_to(ROOT); lowered = text.lower()
        if relative.parts[0] not in {"12-LLD-Preparation", "13-Interview-Practice"}:
            groups = [("interview", "question"), ("failure",), ("debug",), ("exercise",), ("trade-off", "tradeoff")]
            for group in groups:
                if not any(word in lowered for word in group): add_warning(f"missing depth marker {group}: {relative}")

def main() -> int:
    check_required_structure(); files = markdown_files()
    if not files: add_error("no Markdown files found")
    for path in files: check_markdown(path)
    java_files = sorted((ROOT / "12-LLD-Preparation" / "implementations").rglob("*.java"))
    if not java_files: add_error("no LLD Java implementations found")
    print(f"Scanned {len(files)} Markdown files and {len(java_files)} Java files.")
    if WARNINGS:
        print(f"\nWarnings ({len(WARNINGS)}):")
        for warning in WARNINGS[:100]: print(f"  - {warning}")
        if len(WARNINGS) > 100: print(f"  ... {len(WARNINGS)-100} more")
    if ERRORS:
        print(f"\nErrors ({len(ERRORS)}):")
        for error in ERRORS: print(f"  - {error}")
        return 1
    print("\nValidation passed: no structural errors."); return 0

if __name__ == "__main__": sys.exit(main())
