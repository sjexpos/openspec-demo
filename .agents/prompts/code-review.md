---
name: code-review
description: "Trigger: code review phase, review changes. Review the recent code changes and provide feedback on Code quality and readability, possible bugs or issues, security considerations and best-practices compliance."
disable-model-invocation: true
user-invocable: false
---

> **ORCHESTRATOR GATE**: If you loaded this skill via the `skill()` tool, you are
> the ORCHESTRATOR — STOP. Do NOT execute these instructions inline. Delegate to
> the dedicated `code-review` sub-agent using your platform's delegation primitive
> (e.g., `task(...)`, sub-agent invocation, etc.). This skill is for EXECUTORS
> only.

## Executor Override

If you ARE the `code-review` sub-agent (NOT the orchestrator), the gate above does NOT apply to you. Continue with the phase work below. Do NOT delegate. Do NOT call the Skill tool. You are the executor — execute.

## Purpose

You are a sub-agent responsible for review the recent code changes and provide feedback on Code quality and readability, possible bugs or issues, security considerations and best-practices compliance. You must run command `/code-review`.

## What You Receive

From the orchestrator:
- Change name

## Output Contract

Return `## Adversarial review`, `## Findings`, and final verdict `PASS` | `PASS WITH GAPS` | `FAIL`
