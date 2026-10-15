## What and why

<!-- What does this change, and which issue does it address? -->

Closes #

## How it was tested

- [ ] `./gradlew spotlessApply build` passes locally
- [ ] New or changed behaviour has tests (bug fixes include a regression test)
- [ ] Real-server check (docs/TESTING.md), if `plugin/` changed: <!-- server version and result -->
- [ ] In-game check, if player-facing behaviour changed: <!-- game version and result, or "not run" -->

## Hytale API usage

<!-- For any new Hytale API call: how you checked it exists (Javadoc link or javap). Write "none" if there is no new API use. -->

## Checklist

- [ ] Docs updated where relevant (README feature matrix, CHANGELOG "Unreleased", ROADMAP, TESTING)
- [ ] No code or text copied from Baritone or other projects without a compatible license
- [ ] No game files, credentials, or player data committed
- [ ] New dependencies are pinned and `gradle/verification-metadata.xml` is regenerated
