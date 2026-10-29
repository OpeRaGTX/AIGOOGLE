# New edit — template

Copy this folder to `video/<edit-name>/`, put the materials in, then brief Claude (see BRIEF.md).

| Folder | What goes in |
|---|---|
| `input/gameplay/` | game recordings (mp4/mov), any length; name them by moment: `01_pantheon.mp4` |
| `input/music/` | the track (mp3/wav), exactly one file |
| `input/models/` | 3D models for 3D scenes (`.glb`, with animations if available: Death, Attack, Spell…) |
| `input/refs/` | references: edits you like, screenshots, styles, fonts, logos |
| `out/` | Claude writes the finished video here |

Big files in `input/` are not committed to git — keep a copy elsewhere.
