# Contributing to Haritone

Thanks for helping! Anyone can contribute by forking the repository and opening a pull request.
You do not need to be added as a collaborator.

## Ground rules

- Follow the [Code of Conduct](CODE_OF_CONDUCT.md).
- Be honest about what you tested. Say whether you ran the in-game checklist, a real-server
  check, or only unit tests.
- **Never use Hytale APIs from memory.** Check them against the official Javadoc or the API jar
  (`javap`) and mention how in the PR. If something is not possible through the API, document
  the limit instead of working around it with reflection or internals.
- **Clean room:** do not copy code, documentation, or constant tables from Baritone (LGPL-3.0)
  or any other project unless its license allows it and the PR records the obligations.
- Do not commit game files, server jars, `Assets.zip`, credentials, or player data.
- Security issues go to [SECURITY.md](SECURITY.md), not public issues.

## Getting started

1. Install a JDK 25 or newer and point `JAVA_HOME` at it.
2. Fork and clone, then run `./gradlew build`. It should pass before you change anything.
3. Read [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md). Most logic lives in `core`, which needs no
   game server. Keep `plugin` thin.

Good first issues are labelled `good first issue`.

## Making a change

1. Open or comment on an issue first for anything big, so the design can be agreed early.
2. Create a branch in your fork.
3. Add or update tests. Every bug fix needs a regression test. `core` coverage must stay at or
   above 90% of lines and 75% of branches.
4. Run `./gradlew spotlessApply build` (formatting, compile with `-Werror` and Error Prone,
   tests, coverage gate, jar checks).
5. Update the docs that your change affects: README feature matrix, CHANGELOG ("Unreleased"),
   ROADMAP, and TESTING.
6. Open a PR and fill in the template.

### Adding or changing dependencies

Add exact versions in `gradle/libs.versions.toml`, then regenerate checksums:

```sh
./gradlew --write-verification-metadata sha256 build :plugin:cyclonedxDirectBom --rerun-tasks --no-configuration-cache
```

Runtime dependencies are bundled into the mod jar, so they need a strong reason and an
Apache-2.0-compatible license. CI's dependency review rejects GPL and AGPL licenses.

## Review and merging

- CI must pass. CI runs on pull requests from forks too, with read-only permissions and no
  secrets.
- The maintainer (@clintgalendez, listed in `CODEOWNERS`) reviews every PR. Changes to core
  architecture, build and dependency files, workflows, security configuration, and governance
  documents always need the maintainer's approval.
- PRs are usually squash-merged, and **you stay the commit author**.

## Authorship and credit

- Write commits under your own name and email (or your GitHub noreply address).
- Do not add `Co-authored-by` trailers for tools or AI assistants, and do not credit them as
  contributors.
- GitHub builds its contributor list from commit authorship and activity. Every contributor
  whose commits land in `main` appears there, and that is how it should be. The maintainer never
  rewrites history to remove or reassign anyone's contributions.

## License

By contributing, you agree that your contributions are licensed under the
[Apache License 2.0](LICENSE), the project's license (inbound = outbound). No CLA is required.
