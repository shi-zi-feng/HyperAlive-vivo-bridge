"""Fetch pinned upstream assets, verify their Git hashes, and never access a phone."""
from pathlib import Path, PurePosixPath
import argparse
import concurrent.futures
import hashlib
import json
import os
import urllib.request

ROOT = Path(__file__).resolve().parent
COMMIT = '5671c5202d2f2cfa73e852377a26d42443cce884'


def blob_hash(data):
    return hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()


def fetch_assets():
    provenance = json.loads((ROOT / 'upstream-assets.json').read_text(encoding='utf-8'))
    cache = os.environ.get('VIVO_ALIVE_ASSET_CACHE')

    def fetch(entry):
        name = entry['path']
        parts = PurePosixPath(name).parts
        if parts[:4] != ('app', 'src', 'main', 'assets') or '..' in parts:
            raise ValueError('Invalid asset path: ' + name)
        destination = ROOT / name
        if destination.is_file() and blob_hash(destination.read_bytes()) == entry['sha1']:
            return
        candidate = Path(cache).joinpath(*parts[4:]) if cache else None
        if candidate and candidate.is_file() and blob_hash(candidate.read_bytes()) == entry['sha1']:
            data = candidate.read_bytes()
        else:
            url = 'https://raw.githubusercontent.com/Askt-clot/HyperAlive/' + COMMIT + '/' + name
            with urllib.request.urlopen(url, timeout=180) as response:
                data = response.read()
        if blob_hash(data) != entry['sha1']:
            raise RuntimeError('Upstream asset hash mismatch: ' + name)
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(data)

    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        list(pool.map(fetch, provenance))
    print('Verified', len(provenance), 'assets against the pinned upstream Git tree')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.parse_args()
    fetch_assets()
