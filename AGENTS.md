---
description: This document contains all development rules and guidelines for this project, applicable to all AI agents (Claude, OpenCode, Cursor, Codex, Gemini, etc.).
alwaysApply: true
---

## 1. Core Principles

- **Small tasks, one at a time**: Always work in baby steps, one at a time. Never go forward more than one step.
- **Test-Driven Development**: Start with failing tests for any new functionality (TDD), according to the task details.
- **Type Safety**: All code must be fully typed.
- **Clear Naming**: Use clear, descriptive names for all variables and functions.
- **Incremental Changes**: Prefer incremental, focused changes over large, complex modifications.
- **Question Assumptions**: Always question assumptions and inferences.
- **Pattern Detection**: Detect and highlight repeated code patterns.

## 2. Language Standards
- **English Only**: All technical artifacts must always use English, including:
    - Code (variables, functions, classes, comments, error messages, log messages)
    - Documentation (README, guides, API docs)
    - Jira tickets (titles, descriptions, comments)
    - Data schemas and database names
    - Configuration files and scripts
    - Git commit messages
    - Test names and descriptions

## 3. Specific standards

For detailed standards and guidelines specific to different areas of the project, refer to:

- [Backend Standards](./docs/backend-standards.md) - API development, database patterns, testing, security and backend best practices
- [Documentation Standards](./docs/documentation-standards.md) - Technical documentation structure, formatting, and maintenance guidelines, including AI standards like this document

## 4. Project Skills

- Skills live in `.agents/skills`.
- When a request matches a skill, load and follow the corresponding `SKILL.md` automatically before continuing.
- Also load any referenced files in the skill folder (for example, `references/*.md`) when the skill requires them.

### Available Skills

Use these skills for detailed patterns on-demand:

| Skill | Description | URL |
|-------|-------------|-----|
| `adversarial-review` | Create adversarial review, red-team review, devil's advocate check | [SKILL.md](.agents/skills/adversarial-review/SKILL.md) |
| `meta-prompt` | Rewrite prompts using prompt-engineering best practices | [SKILL.md](.agents/skills/meta-prompt/SKILL.md) |
| `sync-agent-symlinks` | Analyze and synchronize agent skill exposure after .agents skill changes  | [SKILL.md](.agents/skills/sync-agent-symlinks/SKILL.md) |
| `update-docs` | Identify and update required technical documentation | [SKILL.md](.agents/skills/update-docs/SKILL.md) |
| `enrich-us` | Analyze and enhance Jira user stories | [SKILL.md](.agents/skills/enrich-us/SKILL.md) |
| `mermaid-diagrams` | Creating software diagrams using Mermaid syntax | [SKILL.md](.agents/skills/mermaid-diagrams/SKILL.md) |
| `test-driven-development` | Test-Driven Development workflow | [SKILL.md](.agents/skills/test-driven-development/SKILL.md) |
| `domain-driven-design` | Domain-Driven Design methodology | [SKILL.md](.agents/skills/domain-driven-design/SKILL.md) |
| `solid-principles` | SOLID principles checklist | [SKILL.md](.agents/skills/solid-principles/SKILL.md) |
| `dry-principle` | Don't Repeat Yourself principle | [SKILL.md](.agents/skills/dry-principle/SKILL.md) |
| `java-jpa-hibernate` | Master JPA/Hibernate - entity design, queries, transactions, performance optimization | [SKILL.md](.agents/skills/java-jpa-hibernate/SKILL.md) |


### Auto-invoke Skills

When performing these actions, ALWAYS invoke the corresponding skill FIRST:

| Action | Skill |
|--------|-------|
| Analyze and enhance Jira user stories | `enrich-us` |
| need to create, visualize, or document software through diagrams | `mermaid-diagrams` |
| Fixing bug | `test-driven-development` |
| Implementing feature | `test-driven-development` |
| Modifying component | `test-driven-development` |
| Refactoring code | `test-driven-development` |
| Working on task | `test-driven-development` |
| Fixing bug | `domain-driven-design` |
| Implementing feature | `domain-driven-design` |
| Modifying component | `domain-driven-design` |
| Refactoring code | `domain-driven-design` |
| Working on task | `domain-driven-design` |
| Fixing bug | `solid-principles` |
| Implementing feature | `solid-principles` |
| Modifying component | `solid-principles` |
| Refactoring code | `solid-principles` |
| Working on task | `solid-principles` |
| Fixing bug | `dry-principle` |
| Implementing feature | `dry-principle` |
| Modifying component | `dry-principle` |
| Refactoring code | `dry-principle` |
| Working on task | `dry-principle` |
| Implementing persistence layer | `java-jpa-hibernate` |
| Make review | `adversarial-review` |
| Update agentic symbolic links | `sync-agent-symlinks` |
| Create/Update technical documentation | `update-docs` |

## 5. Planning Model Requirement

Planning workflows must run with Opus high reasoning.

This requirement applies to:
- `enrich-us`
- `openspec-ff-change`
- `openspec-continue-change`

## 6. Symlink Integrity and Multi-Agent Portability

- **Canonical Source**: Keep reusable artifacts in `.agents` as the canonical source. Agent-specific paths (such as `.claude`, `.opencode` and `.cursor`) should reference them through symlinks when possible.
- **Update Safety**: Whenever a file is renamed, moved, or its suffix changes, verify and update all symlinks that target it before considering the change complete.
- **New Artifact Linking**: Whenever creating a new artifact that requires multi-agent exposure (for example new agents or skills in `.agents`), create the corresponding symlinks from the expected agent-specific reference paths.
- **External Customization Review**: Whenever customization is introduced outside `.agents`, evaluate whether it should be moved into `.agents` and replaced with symlinks from the original locations.
- **Completion Gate**: A change is incomplete if it leaves broken symlinks, stale targets, or duplicated canonical artifacts across agent-specific folders.

## 7. Mandatory OpenSpec Artifact Updates for Post-Apply Changes

When a new fix/change request appears after `opsx-apply` (or `/apply`) and before `opsx-archive` (or `/archive`), agents must treat it as a spec update first, not as an informal "fix this quickly". It's the core principle of openspec, documentation is the source of truth.

Required order:

1. Update the current OpenSpec change artifacts that are affected (for example: scenarios, requirements/specs, and `tasks.md`). Don't add tasks as "bugfixes" but as part of the initial design, thus in the proper section
2. If artifact regeneration is needed, run the corresponding OpenSpec step (`opsx-continue`, `opsx-ff`, or equivalent) before coding.
3. Implement code only after artifacts reflect the new request.
4. Re-run verification against the updated artifacts before archiving.

Do not apply direct code-only fixes in this window without updating OpenSpec artifacts.

