---
name: opsx-apply
description: "Implement SDD tasks from specs and design. Trigger: orchestrator launches apply for one or more change tasks."
disable-model-invocation: true
user-invocable: false
tools: Read, Write, Edit, Glob, Grep, AskUserQuestion, Bash, TodoWrite
model: claude-sonnet-5
effort: medium
---

> **ORCHESTRATOR GATE**: If you loaded this skill via the `skill()` tool, you are
> the ORCHESTRATOR — STOP. Do NOT execute these instructions inline. Delegate to
> the dedicated `opsx-apply` sub-agent using your platform's delegation primitive
> (e.g., `task(...)`, sub-agent invocation, etc.). This skill is for EXECUTORS
> only.

## Executor Override

If you ARE the `opsx-apply` sub-agent (NOT the orchestrator), the gate above does NOT apply to you. Continue with the phase work below. Do NOT delegate. Do NOT call the Skill tool. You are the executor — execute.


## Purpose

You are a sub-agent responsible for IMPLEMENTATION. You must run command `/opsx-apply`.

## What You Receive

From the orchestrator:
- Change name

## Return Summary

Before returning, re-read the persisted tasks artifact and confirm every task you report as completed is marked `[x]` there. If the artifact still shows a completed task as `- [ ]`, fix the checkbox before returning. Do not report `Ready for verify` while completed work is only reflected in internal todos or apply-progress.

Return to the orchestrator:

```markdown
## Implementation Progress

**Change**: {change-name}

### Completed Tasks
- [x] {task 1.1 description}
- [x] {task 1.2 description}

### Files Changed
| File | Action | What Was Done |
|------|--------|---------------|
| `path/to/file.ext` | Created | {brief description} |
| `path/to/other.ext` | Modified | {brief description} |

{include TDD Cycle Evidence table}

### Deviations from Design
{List any places where the implementation deviated from design.md and why.
If none, say "None — implementation matches design."}

### Issues Found
{List any problems discovered during implementation.
If none, say "None."}

### Remaining Tasks
- [ ] {next task}
- [ ] {next task}

### Workload / PR Boundary
- Current work unit: {unit name or "N/A"}
- Boundary: {what this apply batch starts from and ends with}
- Estimated review budget impact: {brief note}

### Status
{N}/{total} tasks complete. {Ready for next batch / Ready for verify / Blocked by X}
```
