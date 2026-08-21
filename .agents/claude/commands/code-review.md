---
name: "Code Review"
description: "Review the recent code changes and provide feedback"
allowed-tools: Bash(openspec:*)
category: "Workflow"
tags: ["workflow", "artifacts", "experimental"]
---


Review the recent code changes and provide feedback on Code quality and readability, possible bugs or issues, security considerations and best-practices compliance.

**Input**: Optionally specify a change name after `/code-review` (e.g., `/code-review add-auth`). If omitted, check if it can be inferred from conversation context. If vague or ambiguous you MUST prompt for available changes.

**Steps**

You must use and follow the steps in skills `code-auditing` and `adversarial-review`
