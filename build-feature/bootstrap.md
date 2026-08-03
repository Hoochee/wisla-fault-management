# Bootstrap — Feature start

Run at the beginning of `/build-feature` when `.feature-state.json` does not exist or `phase === "bootstrap"`.

## Inputs

- Jira key: `WISLA-<number>` (**required** for branch naming when available; other project keys allowed if ticket uses them)
- Change name: kebab-case (derived from Jira title or user description)
- Base branch: always `main`

## Steps

1. **Read Jira** (if key provided)
   - MCP `user-chrome-devtools`: navigate to `https://support.wellink.ru/browse/<KEY>`
   - `take_snapshot` — extract title, description, acceptance criteria
   - If Jira key is missing — ask the user before creating a branch

2. **Derive names**
   - `changeName`: kebab-case, e.g. `console-column-sort`
   - `branch`: `feature/WISLA-<number>` (number from Jira key only — no kebab suffix; use full key if prefix ≠ WISLA)

3. **Git branch**
   ```bash
   git fetch origin main
   git switch -c feature/WISLA-<number> --no-track origin/main
   ```
   **Critical:** always use `--no-track`. Without it, the new branch inherits upstream `origin/main`, and IDE push becomes `feature/...:main` (rejected on protected `main`).

   Do **not** set upstream to `main`. First push of the feature branch (only when user asks to push):
   ```bash
   git push -u origin HEAD
   ```
   That creates `origin/feature/WISLA-<number>` and sets the correct upstream.

   Do not commit unless user explicitly asks.

4. **OpenSpec change**
   ```bash
   openspec new change "<changeName>"
   ```
   If change already exists — ask user to continue or pick another name.

5. **Create state file**

   Write `openspec/changes/<changeName>/.feature-state.json`:

   ```json
   {
     "phase": "discovery",
     "jiraKey": "WISLA-12345",
     "changeName": "my-feature",
     "branch": "feature/WISLA-12345",
     "modules": [],
     "approvals": {
       "scope": false,
       "artifacts": false,
       "readyForPr": false
     },
     "tests": {
       "backend": "pending",
       "frontend": "pending"
     },
     "testFixIterations": {
       "backend": 0,
       "frontend": 0
     },
     "codeReview": {
       "backend": { "status": "pending", "iterations": 0 },
       "frontend": { "status": "pending", "iterations": 0 }
     }
   }
   ```

6. Advance `phase` to `discovery` and report to user:
   - branch name
   - change path: `openspec/changes/<changeName>/`
   - next step: discovery questions or delegate System Analyst

## Re-entry

If `.feature-state.json` exists, read `phase` and continue from orchestrator-playbook — do not recreate branch or change.
