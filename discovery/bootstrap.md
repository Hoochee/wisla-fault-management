# Bootstrap — Discovery start

Run at the beginning of `/discovery` when `.discovery-state.json` does not exist or `phase === "bootstrap"`.

## Inputs

- Jira key: `WISLA-<number>` (or another project key if the ticket uses one) — an Epic or Issue. Sets `source: "jira"`.
- Or a free-text product idea from the user. Sets `source: "manual"`.
- Slug: kebab-case, derived from the Jira title or the idea.

## Steps

1. **Intake**
   - **Jira** (if a Jira key is provided):
     - MCP `user-chrome-devtools`: navigate to `https://support.wellink.ru/browse/<KEY>`
     - `take_snapshot` — extract title, description, goal, any acceptance hints
     - Set `jiraKey` and `source: "jira"`
   - **Manual idea** (free-text): capture the raw idea as the seed; set `jiraKey: null` and `source: "manual"`
   - If both a Jira key and an idea are missing — ask the user for one before creating anything.

2. **Derive names**
   - `slug`: kebab-case, e.g. `duty-shift-handover` or `console-noise-suppression`
   - `title`: short human title (from Jira title or a one-line summary of the idea)

3. **No git branch here**
   - `/discovery` does **NOT** create a git branch. Branch creation is deferred to `/build-feature` bootstrap ([`build-feature/bootstrap.md`](../build-feature/bootstrap.md)) once the backlog item is picked up. Do not run `git switch` / `git checkout`. Do not commit unless the user explicitly asks.

4. **No OpenSpec change here**
   - `/discovery` does **NOT** run `openspec new change`. The OpenSpec change is created later in `/build-feature` bootstrap.

5. **Create the artifact directory + state file**

   Create `openspec/discovery/<slug>/` (by writing the state file into it) and write `openspec/discovery/<slug>/.discovery-state.json`:

   ```json
   {
     "phase": "discovery",
     "jiraKey": "WISLA-12345",
     "slug": "duty-shift-handover",
     "title": "Передача смены дежурного",
     "source": "jira",
     "riskLevel": null,
     "approvals": { "discovery": false },
     "backlogItem": null
   }
   ```

   For a manual idea, set `"jiraKey": null` and `"source": "manual"`.

6. Advance `phase` to `discovery` and report to the user (Russian):
   - source (Jira link or «идея»)
   - artifact path: `openspec/discovery/<slug>/`
   - next step: the discovery questions (from [references/question-bank.md](references/question-bank.md))

## Re-entry

If `.discovery-state.json` exists, read `phase` and continue from [playbook.md](playbook.md) — do not re-run intake or recreate the state file.
