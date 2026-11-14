#!/usr/bin/env python3
"""Download every dataset listed in data/sources/datasets.json into data/raw/<id>/.

Each dataset gets its own directory and a source.json with URL, licence, size,
sha256 and fetch time. Downloads resume (curl -C -) and are skipped when the
remote size matches what is already on disk.

    python3 tools/fetch_datasets.py                # core tier
    python3 tools/fetch_datasets.py --tier all     # everything
    python3 tools/fetch_datasets.py tatoeba-por    # specific ids
"""
from __future__ import annotations

import argparse
import concurrent.futures as cf
import datetime as dt
import hashlib
import json
import pathlib
import subprocess
import sys
import urllib.parse

ROOT = pathlib.Path(__file__).resolve().parent.parent
MANIFEST = ROOT / "data" / "sources" / "datasets.json"
RAW = ROOT / "data" / "raw"
UA = "PT-PTDicioPlus-dataset-fetch/1.0"


def remote_size(url: str) -> int | None:
    out = subprocess.run(
        ["curl", "-sIL", "--max-time", "30", "-A", UA, url],
        capture_output=True, text=True,
    ).stdout
    size = None
    for line in out.splitlines():
        if line.lower().startswith("content-length:"):
            size = int(line.split(":", 1)[1].strip())
    return size


def sha256(path: pathlib.Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def fetch_one(url: str, dest_dir: pathlib.Path) -> dict:
    name = pathlib.PurePosixPath(urllib.parse.urlparse(url).path).name
    if url.endswith("/archive/refs/heads/master.tar.gz") or url.endswith("/archive/refs/heads/main.tar.gz"):
        name = url.split("/")[4] + "-" + name
    dest = dest_dir / name
    want = remote_size(url)
    if dest.exists() and want is not None and dest.stat().st_size == want:
        status = "cached"
    else:
        cmd = ["curl", "-fL", "--retry", "5", "--retry-delay", "5", "-A", UA, "-o", str(dest), url]
        # Resume only when the server told us a size and we have a partial file.
        if dest.exists() and want and dest.stat().st_size < want:
            cmd[1:1] = ["-C", "-"]
        elif dest.exists():
            dest.unlink()
        subprocess.run(cmd + ["-s", "-S"], check=True)
        status = "downloaded"
    return {
        "url": url,
        "file": name,
        "bytes": dest.stat().st_size,
        "sha256": sha256(dest),
        "status": status,
    }


def fetch_dataset(ds: dict) -> tuple[str, str]:
    dest_dir = RAW / ds["id"]
    dest_dir.mkdir(parents=True, exist_ok=True)
    try:
        files = [fetch_one(u, dest_dir) for u in [ds["url"], *ds.get("extra", [])]]
    except subprocess.CalledProcessError as e:
        return ds["id"], f"FAILED ({e})"
    meta = {k: ds[k] for k in ("id", "desc", "license", "attribution", "use") if k in ds}
    meta["fetched_at_utc"] = dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds")
    meta["files"] = files
    (dest_dir / "source.json").write_text(json.dumps(meta, indent=2, ensure_ascii=False) + "\n")
    total = sum(f["bytes"] for f in files) / 1e6
    return ds["id"], f"ok {total:,.1f} MB ({', '.join(f['status'] for f in files)})"


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("ids", nargs="*")
    ap.add_argument("--tier", choices=["core", "all"], default="core")
    ap.add_argument("-j", "--jobs", type=int, default=4)
    args = ap.parse_args()

    datasets = json.loads(MANIFEST.read_text())["datasets"]
    if args.ids:
        unknown = set(args.ids) - {d["id"] for d in datasets}
        if unknown:
            sys.exit(f"unknown ids: {', '.join(sorted(unknown))}")
        datasets = [d for d in datasets if d["id"] in args.ids]
    elif args.tier == "core":
        datasets = [d for d in datasets if d["tier"] == "core"]

    failed = 0
    with cf.ThreadPoolExecutor(args.jobs) as pool:
        for ds_id, result in pool.map(fetch_dataset, datasets):
            print(f"{ds_id:32} {result}", flush=True)
            failed += result.startswith("FAILED")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
