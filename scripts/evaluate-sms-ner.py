"""Optional, local GLiNER experiment. Install gliner==0.2.24 in a separate environment.
Only model files are downloaded. The sample text is processed locally.
This is not part of Android's production parsing pipeline.
"""
import os
os.environ.setdefault("USE_TF", "0")
os.environ.setdefault("USE_FLAX", "0")
import argparse
import json
from pathlib import Path
import time
import shutil

parser = argparse.ArgumentParser()
parser.add_argument('--model', default='urchade/gliner_small-v2.1')
parser.add_argument('--cache', default='work/gliner-cache')
parser.add_argument('--output', default='work/gliner-evaluation.json')
parser.add_argument('--local-only', action='store_true')
args = parser.parse_args()
report = {'model': args.model, 'production_enabled': False}
try:
    cache = Path(args.cache)
    cache.mkdir(parents=True, exist_ok=True)
    if not args.local_only and shutil.disk_usage(cache).free < 2 * 1024**3:
        raise RuntimeError("At least 2 GiB free disk space is required for this model trial")
    from gliner import GLiNER
    import torch
    torch.set_num_threads(2)
    model = GLiNER.from_pretrained(args.model, cache_dir=args.cache, local_files_only=args.local_only)
    labels = ['transaction amount', 'balance', 'date', 'person or company name', 'bank account', 'transaction reference']
    sample = 'ICIC BANK Account XX110 credited Rs. 10 on 01-Oct-26. Info NEFT-HDFCH-CHO1297600205-CEL EBA.AVAILABLE BALANCE is Rs. 20.'
    started = time.perf_counter()
    entities = model.predict_entities(sample, labels, threshold=0.5)
    report.update(status='completed', seconds=time.perf_counter() - started, entities=entities,
                  expected={'transaction amount': 'Rs. 10', 'date': '01-Oct-26', 'person or company name': 'CEL EBA', 'balance': 'Rs. 20'})
except Exception as error:
    report.update(status='unavailable', error=f'{type(error).__name__}: {error}')
output = Path(args.output)
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps(report, indent=2, ensure_ascii=False) + '\n')
print(json.dumps(report, indent=2, ensure_ascii=False))
