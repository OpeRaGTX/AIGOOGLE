"""Montage list: inbox/edl.json (as delivered, never edited) + optional pipeline/intro.json.

Copied from lore-lulu-vs-nautilus. A cold open can also come inside the delivered EDL: then the EDL has
`voice_offset` and the segments before it are marked `intro`. This video has neither (offset 0).

The intro file adds segments before the story (cold open) and shifts every EDL segment, the voice and the captions by
`offset` seconds. Used by prep_clips.py (picture) and align_voice.py (voice, captions); the merged list goes to
src/edl.json for Remotion.
"""
import json
from pathlib import Path


def load_edl(root: Path) -> dict:
    edl = json.loads((root / 'inbox/edl.json').read_text(encoding='utf-8'))
    intro = root / 'pipeline/intro.json'
    if not intro.exists():
        # cold open already inside the EDL: `voice_offset` s of picture before the voice; those segments are the intro
        off = edl.get('voice_offset', 0)
        segs = [{**s, 'intro': True} if s['start'] < off - 1e-6 else s for s in edl['segments']]
        return {**edl, 'offset': off, 'segments': segs}
    it = json.loads(intro.read_text(encoding='utf-8'))
    off = it['offset']
    segs = [{**s, 'intro': True} for s in it['segments']] + [{**s, 'start': round(s['start'] + off, 3)} for s in edl['segments']]
    return {**edl, 'offset': off, 'segments': segs}
