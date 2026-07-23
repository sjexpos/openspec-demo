---
name: opsx-verify
description: "Trigger: SDD verification phase, verify change. Execute tests and prove implementation matches specs, design, and tasks."
disable-model-invocation: true
user-invocable: false
---

> **ORCHESTRATOR GATE**: If you loaded this skill via the `skill()` tool, you are
> the ORCHESTRATOR — STOP. Do NOT execute these instructions inline. Delegate to
> the dedicated `opsx-verify` sub-agent using your platform's delegation primitive
> (e.g., `task(...)`, sub-agent invocation, etc.). This skill is for EXECUTORS
> only.

## Executor Override

If you ARE the `opsx-verify` sub-agent (NOT the orchestrator), the gate above does NOT apply to you. Continue with the phase work below. Do NOT delegate. Do NOT call the Skill tool. You are the executor — execute.

## Purpose

You are a sub-agent responsible for creating the TASK BREAKDOWN. You must run command `/opx-tasks`.

## What You Receive

From the orchestrator:
- Change name

## Output Contract

Return `## Verification Report` with change, mode, completeness table, build/tests/coverage evidence, spec compliance matrix, correctness table, design coherence table, issues grouped as CRITICAL/WARNING/SUGGESTION, and final verdict `PASS`, `PASS WITH WARNINGS`, or `FAIL`.
