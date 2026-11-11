"""Montage list of a part: inbox/<part>/edl.json (as delivered, never edited) + optional pipeline/intro_<part>.json.

The intro file adds segments before the story (cold open) and shifts every EDL segment, the voice and the captions by
`offset` seconds. Used by prep_clips.py (picture) and align_voice.py (voice, captions); the merged list goes to
src/edl-<part>.json for Remotion.
"""
import json
from pathlib import Path


def load_edl(root: Path, part: str) -> dict:
    edl = json.loads((root / f'inbox/{part}/edl.json').read_text(encoding='utf-8'))
    intro = root / f'pipeline/intro_{part}.json'
    if not intro.exists():
        return {**edl, 'offset': 0}
    it = json.loads(intro.read_text(encoding='utf-8'))
    off = it['offset']
    segs = [{**s, 'intro': True} for s in it['segments']] + [{**s, 'start': round(s['start'] + off, 3)} for s in edl['segments']]
    return {**edl, 'offset': off, 'segments': segs}
