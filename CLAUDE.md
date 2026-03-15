# CLAUDE.md

Purpose: keep Claude Code aligned with `AGENTS.md` and avoid duplicated repo instructions drifting over time.

## Source of Truth

1. `AGENTS.md` is the canonical instruction file for this repository.
2. Both Claude Code and Codex must read `AGENTS.md` first and follow it as the primary source of truth.
3. If `CLAUDE.md`, task notes, or older docs conflict with `AGENTS.md`, follow `AGENTS.md`.
4. At the start of each run, load `AGENTS.md` and the smallest relevant set of task files before making changes.

## Role of This File

Use this file only as a lightweight pointer layer for Claude Code.
Do not place repository rules, workflows, coding standards, or shared agent instructions in this file.
If shared guidance changes, update `AGENTS.md` only.

Keep this file intentionally short and limited to pointing Claude Code to `AGENTS.md`.
