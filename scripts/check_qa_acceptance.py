#!/usr/bin/env python3
"""Verify that the QA acceptance matrix covers the traceability contract exactly once."""

from __future__ import annotations

import argparse
import re
import sys
from collections import Counter
from pathlib import Path


REQUIREMENT_PATTERN = re.compile(r"^\|\s*(REQ-[A-Z]+-\d{3})\s*\|")
ALLOWED_RESULTS = {"PASS", "PARTIAL", "MISSING", "NOT_RUN", "PM_REQUIRED"}


def requirement_ids(path: Path) -> list[str]:
    return [
        match.group(1)
        for line in path.read_text(encoding="utf-8").splitlines()
        if (match := REQUIREMENT_PATTERN.match(line))
    ]


def matrix_rows(path: Path) -> dict[str, list[str]]:
    rows: dict[str, list[str]] = {}
    duplicates: list[str] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        match = REQUIREMENT_PATTERN.match(line)
        if match is None:
            continue
        requirement_id = match.group(1)
        if requirement_id in rows:
            duplicates.append(requirement_id)
        rows[requirement_id] = [cell.strip() for cell in line.strip().strip("|").split("|")]
    if duplicates:
        raise ValueError(f"duplicate acceptance rows: {', '.join(sorted(set(duplicates)))}")
    return rows


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--requirements",
        type=Path,
        default=Path("docs/product/REQUIREMENTS_TRACEABILITY_KO.md"),
    )
    parser.add_argument(
        "--matrix",
        type=Path,
        default=Path("docs/qa/ACCEPTANCE_MATRIX_KO.md"),
    )
    args = parser.parse_args()

    required = requirement_ids(args.requirements)
    if not required:
        raise ValueError(f"no requirement rows found in {args.requirements}")
    duplicate_contract_ids = sorted(
        requirement_id
        for requirement_id, count in Counter(required).items()
        if count != 1
    )
    if duplicate_contract_ids:
        raise ValueError(f"duplicate requirement IDs: {', '.join(duplicate_contract_ids)}")

    rows = matrix_rows(args.matrix)
    missing = sorted(set(required) - set(rows))
    unexpected = sorted(set(rows) - set(required))
    if missing or unexpected:
        raise ValueError(
            "acceptance coverage mismatch; "
            f"missing={missing or 'none'}, unexpected={unexpected or 'none'}"
        )

    malformed: list[str] = []
    for requirement_id, cells in rows.items():
        if len(cells) != 7:
            malformed.append(f"{requirement_id}: expected 7 columns, found {len(cells)}")
            continue
        for label, value in zip(
            ("automated", "device", "demo", "operational"),
            cells[2:6],
        ):
            result = value.partition(":")[0].strip()
            if result not in ALLOWED_RESULTS:
                malformed.append(
                    f"{requirement_id}: {label} result {result!r} is not allowed"
                )
    if malformed:
        raise ValueError("; ".join(malformed))

    print(f"QA acceptance matrix covers {len(required)} requirements exactly once.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError) as error:
        print(f"QA acceptance check failed: {error}", file=sys.stderr)
        raise SystemExit(1)
