"""Build and serve the art review on this computer only."""
import argparse
import json
import subprocess
import sys
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--port', type=int, default=18765)
parser.add_argument('--batch', type=int)
args = parser.parse_args()
folder = Path(__file__).resolve().parent
if args.batch is None:
    args.batch = json.loads((folder.parent / 'generation-review.json').read_text(encoding='utf-8'))['currentBatch']
subprocess.run([sys.executable, str(folder / 'build.py'), '--batch', str(args.batch)], check=True)

class Handler(SimpleHTTPRequestHandler):
    def end_headers(self):
        self.send_header('Cache-Control', 'no-store')
        super().end_headers()

server = ThreadingHTTPServer(('127.0.0.1', args.port), partial(Handler, directory=str(folder / 'dist')))
print(f'Art review: http://127.0.0.1:{args.port}', flush=True)
try:
    server.serve_forever()
except KeyboardInterrupt:
    pass
finally:
    server.server_close()
