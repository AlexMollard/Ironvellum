#!/usr/bin/env python
"""Run a batch of art prompts through tools/art.py, one per line.

Why a batch file rather than a shell loop: the image quota runs out mid-batch
(cloudcode-pa answers QUOTA_EXHAUSTED), and a loop leaves no record of which
lines landed. This skips ids whose PNG already exists, so re-running after the
quota resets finishes the set instead of regenerating what is already drawn.

Batch line format:  id|subject
The style suffix lives in the batch file's `# SUFFIX:` comment, so the prompt
that produced a given piece stays in version control beside it.
"""
import pathlib
import subprocess
import sys

STYLE = ["--style", "ink", "--alpha", "--size", "384"]


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    batch = pathlib.Path(sys.argv[1])
    out_dir = pathlib.Path(sys.argv[2]) if len(sys.argv) > 2 else pathlib.Path(".tmp")
    out_dir.mkdir(parents=True, exist_ok=True)
    lines = batch.read_text(encoding="utf-8").splitlines()
    suffix = next(
        (l.split("SUFFIX:", 1)[1].strip() for l in lines if l.startswith("# SUFFIX:")), ""
    )
    jobs = [l.split("|", 1) for l in lines if l.strip() and not l.startswith("#")]
    done = skipped = failed = 0
    for name, subject in jobs:
        out = out_dir / f"{batch.stem}_{name}.png"
        if out.exists():
            print(f"{name:10} already drawn, skipping")
            skipped += 1
            continue
        prompt = f"{subject}; {suffix}" if suffix else subject
        r = subprocess.run(
            [sys.executable, "tools/art.py", *STYLE, prompt, "-o", str(out)],
            capture_output=True,
            text=True,
        )
        blob = r.stdout + r.stderr
        if out.exists():
            verdict = next((l.strip() for l in blob.splitlines() if "backdrop" in l), "")
            print(f"{name:10} drawn — {verdict}")
            done += 1
        else:
            reason = "QUOTA_EXHAUSTED" if "QUOTA_EXHAUSTED" in blob else "failed"
            print(f"{name:10} {reason}")
            failed += 1
            if reason == "QUOTA_EXHAUSTED":
                print("quota gone; re-run this same command later to finish the set")
                break
    print(f"\ndrawn {done}, already had {skipped}, not drawn {failed}")
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
