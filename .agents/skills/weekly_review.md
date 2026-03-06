# Skill: weekly_review

## Purpose
Generate a friendly, human-readable weekly update describing the **features added this week** (primarily user-facing changes), based on the git history in the current repository.

## How to use
Run an agent with this skill as the base prompt, and pass any extra instructions as the task prompt.

Example task prompt ideas:
- "Write this for customers (marketing tone)"
- "Write this for internal engineering (more technical)"
- "Only include changes in src/ and docs/"

## Instructions
You are an assistant operating in a git repository.

1. Determine the time window for "this week":
   - Default to the last 7 days.
   - If the user specifies a different range (e.g. "since last Monday"), use that.

2. Collect relevant changes:
   - Use `git log` (and if needed `git show`) to gather commits in the time window.
   - Prefer merge commits / PR merges if the repo uses them.
   - Ignore purely mechanical changes when possible (formatting, lint-only, dependency bumps) unless they clearly affected user experience.

   Suggested commands (adjust as needed):
   - `git log --since="7 days ago" --no-merges --oneline`
   - `git log --since="7 days ago" --merges --oneline`
   - `git log --since="7 days ago" --name-only --pretty=format:"%H %s"`

3. Identify **features** vs. fixes/chore:
   - A “feature” is a net-new capability, a visible enhancement, or a meaningful workflow improvement.
   - If uncertain, include it but label it as an improvement rather than a new feature.

4. Group items into a small set of categories (choose what fits):
   - New Features
   - Improvements
   - Developer Experience
   - Performance / Reliability
   - UX / UI
   - Documentation

5. Write the weekly update:
   - Tone: friendly, concise, non-salesy by default.
   - Use short bullets or short paragraphs.
   - Expand acronyms the first time if the audience might be non-technical.
   - If the commit messages are unclear, infer intent by looking at changed files or diffs.

## Output format
Return:
- Title: "Weekly Review" + date range
- 5–15 bullets total (combine similar items)
- Optional: a short closing line like "Thanks to everyone who contributed!"

## Guardrails
- Do not invent features that aren’t supported by commit history/diffs.
- If the week has very few changes, say so and keep it short.
- If you can’t access git history for some reason, ask the user how they define "this week" and request the relevant commit range or PR links.
