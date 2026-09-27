---
name: plan-first
description: >
  Plan-and-clarify workflow with token economy. Use at the START of any non-trivial task
  in this repo — a new feature, game system, map, UI screen, refactor, or anything that
  takes more than a couple of edits (e.g. "сделай систему инвентаря", "добавь монстра",
  "build a lobby", "create a crafting system"). Structures the task, asks the user the
  key questions, agrees on a plan, then executes while spending as few tokens as possible.
  Skip it for trivial one-step requests (a typo, a rename, a single question).
---

# Plan first, then build — cheaply

Three phases, in order. Do not start writing code or building in Studio before phase 2 ends.

## Phase 1 — Understand (cheap reconnaissance)

1. Restate the goal in one sentence for yourself.
2. Look only at what the task needs:
   - `Glob`/`Grep` first to locate files; then `Read` with `offset`/`limit` on the relevant part.
   - Do not read whole reference files of other skills — open the one section you need.
   - Do not re-read files already read in this conversation.
3. Pick which specialist skills apply (e.g. `roblox-dev-skill` for code,
   `building-maps` for maps, `roblox-ui` for UI, `game-ai` for monsters) — load only those.

## Phase 2 — Structure and ask

1. Break the task into 3–7 concrete steps (systems, files, scripts, assets).
2. Find the decisions only the user can make: gameplay rules, scope, style, platform
   (mobile/PC), numbers (health, timers, prices), what already exists.
3. Ask them with **one** `AskUserQuestion` call: 1–4 questions, each with 2–4 options,
   the recommended option first and marked "(Рекомендуется)". Ask in the user's language
   (Russian by default). Do not ask what you can find in the code or decide by a sane default —
   state those defaults instead.
4. Show a short plan (the steps + the chosen answers + defaults) — no more than ~10 lines —
   and proceed. Do not ask "can I start?" separately; answering the questions is the go-ahead.

## Phase 3 — Execute economically

- Follow the plan step by step; if it turns out wrong, adjust and say so in one line.
- Make independent tool calls in parallel in one message.
- Trim command output: `| head`, `| tail`, `grep -c`, `--quiet`. Never dump huge logs or API dumps.
- Prefer `Edit` of a few lines over rewriting whole files.
- Do not spawn subagents unless the user asks — each one re-reads the context from scratch.
- No narration between steps. The final report: what was done, where (file paths),
  what to check in Studio, what is left — in a few bullets.
- Verify once at the end (tests/lint/run) instead of after every tiny change.

## Token-saving answers

- Answer concisely; no repeating the question, no long introductions or summaries of
  things the user already knows.
- Code: show only new/changed parts, not whole unchanged files.
- If the user asks a quick question mid-task, answer it briefly and continue the plan.
