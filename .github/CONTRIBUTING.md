# Branching

Three kinds of branch. Nothing else.

```
main ────────────●───────────────────────●──────  releases only
                 ↑                       ↑
                 │  merge when done      │
dev  ──●────●────●────●────●────●────●───●──────  integration
        ↖    ↖         ↖         ↖
         feat/x  fix/y   feat/z   spike/w         one branch per piece of work
```

## `main`

The stable line. It only moves when something is **done** — a release, or a
slice finished end to end and verified. Never commit to it directly, and never
merge a half-finished feature into it.

## `dev`

Where work integrates. Every feature branch starts here and comes back here.
`dev` is allowed to be ahead of `main` for a long time; that is the point.

## Feature branches

Branch off `dev`, merge back into `dev`, then delete.

```bash
git checkout dev
git pull
git checkout -b feat/my-thing

# ... work, commit ...

git push -u origin feat/my-thing
# open a PR against dev
```

Prefixes in use: `feat/`, `fix/`, `chore/`, `refactor/`, `spike/`.
A `spike/` branch is an evidence branch — it may never merge, and that is fine.

## Releasing

When `dev` holds something worth calling done:

```bash
git checkout main
git pull
git merge --no-ff dev
git push
```

`--no-ff` keeps the release visible as a single merge in `main`'s history.

## Rules that are enforced on GitHub, not here

- `main` takes no direct pushes. Changes arrive by PR from `dev`.
- CI must be green before a PR merges.

Both live in the repository's branch protection settings.

## The gate

CI (`.github/workflows/ci.yml`) runs four things, in order, and all four must
pass:

1. `./gradlew test`
2. `./gradlew runData` — the data generation gate
3. `./gradlew runGameTestServer` — the in-game test gate
4. `./gradlew build`

Do not run `runData` locally to check something. It regenerates placeholder
textures directly into `src/main/resources` and will overwrite real committed
art with solid-colour stubs. Let CI run it, on its own throwaway checkout.
