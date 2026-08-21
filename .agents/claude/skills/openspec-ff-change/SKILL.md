---
name: openspec-ff-change
description: Fast-forward through OpenSpec artifact creation. Use when the user wants to quickly create all artifacts needed for implementation without stepping through each one individually.
allowed-tools: Bash(openspec:*)
license: MIT
compatibility: Requires openspec CLI.
metadata:
  author: openspec
  version: "1.0"
  generatedBy: "1.10.0"
---

Fast-forward through artifact creation - generate everything needed to start implementation in one go.

**Store selection:** If the user names a store (a store is a standalone OpenSpec repo registered on this machine) or the work lives in one, run `openspec store list --json` to discover registered store ids, then pass `--store <id>` on the commands that read or write specs and changes (`new change`, `status`, `instructions`, `list`, `show`, `validate`, `archive`, `doctor`, `context`, `schemas`, `view`). Once selected, treat `--store <id>` as sticky for the rest of the workflow. Every unscoped example of those commands below is shorthand: before running it, append the flag. For example, run `openspec status --change "<name>" --json --store "<id>"`, not the unscoped form shown below. Other commands do not take the flag. Hints printed by commands already carry the flag; keep it on follow-ups. Without a store, commands act on the nearest local `openspec/` root.

**Input**: The user's request should include:
- A Jira ticket ID (e.g., `SCRUM-123`) - will fetch ticket content using Jira MCP
- A change name (kebab-case) - will use that name directly
- A description of what they want to build - will derive a kebab-case name

**Steps**

1. **Determine input type and get context**

   a. **If input looks like a Jira ticket ID** (matches pattern like `SCRUM-123`, `PROJ-456`, etc.):
      - Use `getAccessibleAtlassianResources` MCP tool to get the cloudId
      - Use `getJiraIssue` MCP tool with:
        - `cloudId`: from step above
        - `issueIdOrKey`: the provided ticket ID
      - Extract ticket content (title, description, acceptance criteria, etc.)
      - **Derive a kebab-case change name from the ticket title**:
        - Convert ticket title to lowercase
        - Replace spaces and special characters with hyphens
        - Remove any leading/trailing hyphens
        - Example: "Update Position API" → `update-position-api`, "Add User Auth" → `add-user-auth`
        - If ticket title is unclear or too long, use a shortened meaningful version
      - Use the derived kebab-case name as `<name>` for the change directory
      - Use ticket content as context for creating artifacts
      - Store ticket ID for reference (e.g., in proposal or as metadata)

   b. **If input is a change name** (kebab-case format):
      - Use the provided name directly
      - Check if change already exists, if so ask user if they want to continue it

   c. **If input is a description**:
      - Derive a kebab-case name (e.g., "add user authentication" → `add-user-auth`)

   d. **If no input provided**:
      - Use the **AskUserQuestion tool** (open-ended, no preset options) to ask:
        > "What change do you want to work on? Provide a Jira ticket ID (e.g., SCRUM-123), change name, or describe what you want to build."

   **IMPORTANT**: Do NOT proceed without understanding what the user wants to build.

2. **Create the change directory**
   ```bash
   openspec new change "<name>"
   ```
   This creates a scaffolded change at `openspec/changes/<name>/`.

2.5. **Handle attached files (if any)**
   If the user has attached files to this conversation:
   - Check for any files in the conversation context (attached files will be visible in the file list)
   - For each attached file:
     - Read the file to get its current path
     - Move it to the root of the change directory: `openspec/changes/<name>/<filename>`
     - Use the file system tools to copy/move the file, preserving the original filename
   - If files were moved, inform the user: "Moved N attached file(s) to the change directory root."

3. **Get the artifact build order**
   ```bash
   openspec status --change "<name>" --json
   ```
   Parse the JSON to get:
   - `applyRequires`: array of artifact IDs needed before implementation (e.g., `["tasks"]`)
   - `artifacts`: list of all artifacts, each with its `status` and its `requires` edges (the artifact IDs it directly depends on)
   - `planningHome`, `changeRoot`, `artifactPaths`, and `actionContext`: path and scope context. Use these instead of assuming repo-local paths.

4. **Create every artifact in the required set**

   Use the **TodoWrite tool** to track progress through the artifacts.

   Loop through artifacts in dependency order (artifacts with no pending dependencies first):

   a. **For each artifact that is `ready` (dependencies satisfied)**:
      - Get instructions:
        ```bash
        openspec instructions <artifact-id> --change "<name>" --json
        ```
      - The instructions JSON includes:
        - `context`: Project background (constraints for you - do NOT include in output)
        - `rules`: Artifact-specific rules (constraints for you - do NOT include in output)
        - `template`: The structure to use for your output file
        - `instruction`: Schema-specific guidance for this artifact type
        - `skipped`/`warning`: present when the change declares skip_specs and this artifact must NOT be created - stop and pick another artifact
        - `resolvedOutputPath`: Resolved path or pattern to write the artifact
        - `dependencies`: Completed artifacts to read for context
      - **CRITICAL for tasks artifact**: If creating `tasks.md`:
        - Read `openspec/config.yaml` to get backend-specific rules (mandatory steps, branch naming, etc.)
        - Task structure requirements
        - All mandatory steps that MUST be included (e.g., Step 0: Create Feature Branch)
      - **If Jira ticket was provided**: Use ticket content to inform artifact creation (especially proposal and tasks)
      - Read any completed dependency files for context - always re-read them from disk, even if you saw them earlier in the conversation (the user may have edited them)
      - If the `instruction` field delegates creation to a specific skill or command, invoke it to produce the artifact instead of writing the file yourself, then verify the artifact file exists at `resolvedOutputPath`
      - Otherwise create the artifact file using `template` as the structure and write it to `resolvedOutputPath`. If `resolvedOutputPath` is a glob, follow `instruction` to choose the concrete file path
      - Apply `context` and `rules` as constraints - but do NOT copy them into the file
      - **For tasks artifact**: Ensure all mandatory steps from `config.yaml` and the rule file are included:
        - Step 0: Create Feature Branch (MUST be first step for backend changes)
        - Review and Update Existing Unit Tests (MANDATORY)
        - Run Unit Tests and Verify Database State (MANDATORY)
        - Manual Endpoint Testing with curl (MANDATORY - AGENT MUST EXECUTE)
        - Update Technical Documentation (MANDATORY)
      - **For manual testing tasks**: Include sub-tasks that make it clear the agent must execute tests (e.g., "Test GET endpoints with curl", "Restore database state", etc.)
      - Show brief progress: "✓ Created <artifact-id>"

   b. **Continue until every artifact in the required set exists (not just `apply.requires`)**
      - After creating each artifact, re-run `openspec status --change "<name>" --json`
      - The required set is `applyRequires` plus every artifact reachable from those by following the `requires` edges in `status --json` - walk them transitively (spec-driven closes over proposal, specs, design, tasks). Leave artifacts outside that set alone
      - `status` is file-existence only, so an `applyRequires` artifact reading `done` does NOT mean its dependencies exist - writing `tasks.md` early marks `tasks` done while `specs` was never written. Use each artifact's `requires` edges, not its `status`, to build the required set: a `done` artifact still lists what it depends on
      - An artifact already reading `status: "skipped"` is satisfied: the change declares `skip_specs` in `.openspec.yaml`, so its files must NOT exist. Never try to create one
      - Create every artifact in the required set that is missing, then re-check - creating one can unblock others
      - Skip one only when `status` already reports it `skipped`, or when its own `instruction` says it is conditional: run `openspec instructions <artifact-id> --change "<name>" --json` and skip only if its `instruction` field marks it optional (e.g. "create only if..."). Spec-driven's `design.md` qualifies; `specs` qualifies only via the `skipped` status above, never by your own judgment. Tell the user, and do not reconsider it
      - Dependencies are enablers, not gates: if a required artifact is still `blocked` only because you skipped a conditional dependency, write it anyway
      - Stop when every artifact in the required set is `done`, `skipped`, or was deliberately skipped

   c. **If an artifact requires user input** (unclear context):
      - Use **AskUserQuestion tool** to clarify
      - Then continue with creation

5. **Show final status**
   ```bash
   openspec status --change "<name>"
   ```

**Output**

After completing all artifacts, summarize:
- Change name and location
- List of artifacts created with brief descriptions, plus any conditional artifact you skipped and why
- What's ready: "All artifacts needed for implementation are ready."
- Prompt: "Run `/opsx-apply` or ask me to implement to start working on the tasks."

**Artifact Creation Guidelines**

- Follow the `instruction` field from `openspec instructions` for each artifact type - it is the authoritative guidance, even for familiar artifact names
- If the `instruction` field directs you to use a specific skill or command to create the artifact, invoke it instead of writing the artifact directly
- The schema defines what each artifact should contain - follow it
- Read dependency artifacts for context before creating new ones
- Use `template` as the structure for your output file - fill in its sections
- **IMPORTANT**: `context` and `rules` are constraints for YOU, not content for the file
  - Do NOT copy `<context>`, `<rules>`, `<project_context>` blocks into the artifact
  - These guide what you write, but should never appear in the output

**Guardrails**
- Create every artifact the apply phase transitively depends on, not just the ids listed in `apply.requires`
- Always read dependency artifacts before creating a new one - re-read from disk, not from conversation memory (files may have changed since you last saw them)
- If context is critically unclear, ask the user - but prefer making reasonable decisions to keep momentum
- If a change with that name already exists, suggest continuing that change instead
- Verify each artifact file exists after writing before proceeding to next
