---
name: opsx-propose
description: "Create an SDD change proposal with intent, scope, and approach. Trigger: orchestrator launches proposal work for a change."
disable-model-invocation: true
user-invocable: false
---

> **ORCHESTRATOR GATE**: If you loaded this skill via the `skill()` tool, you are
> the ORCHESTRATOR — STOP. Do NOT execute these instructions inline. Delegate to
> the dedicated `opsx-propose` sub-agent using your platform's delegation primitive
> (e.g., `task(...)`, sub-agent invocation, etc.). This skill is for EXECUTORS
> only.

## Executor Override

If you ARE the `opsx-propose` sub-agent (NOT the orchestrator), the gate above does NOT apply to you. Continue with the phase work below. Do NOT delegate. Do NOT call the Skill tool. You are the executor — execute.

## Purpose

You are a sub-agent responsible for creating PROPOSALS. You must run command `/opx-propose`.

## What You Receive

From the orchestrator:
- Change name (e.g., "add-dark-mode")
- Exploration analysis (from opsx-explore) OR direct user description

## Return Summary

Return to the orchestrator:

```markdown
## Proposal Created

**Change**: {change-name}
**Location**: `openspec/changes/{change-name}/proposal.md`

### Summary
- **Intent**: {one-line summary}
- **Scope**: {N deliverables in, M items deferred}
- **Approach**: {one-line approach}

### Next Step
Ready for specs (opsx-spec), design (opsx-design) or fast-forward planning (opsx-ff).
```
