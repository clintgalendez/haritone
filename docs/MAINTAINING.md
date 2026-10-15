# Maintaining Haritone

This guide is for the maintainer: @clintgalendez, the release authority. Contributors do not
need it.

## Roles and permissions

- **Maintainer (repository owner):** merges pull requests, approves releases through the
  protected `release` environment, and manages settings, secrets, and rulesets.
- **Contributors:** fork the repository and open pull requests. They need no collaborator
  access, and none is granted for submitting code.
- Do not give contributors write, admin, or secret access to land changes. If triage help is
  ever needed, the GitHub **Triage** role lets someone label issues without code access.

## Repository settings

Apply these settings yourself or with `gh`. Check them with the `gh api` GET calls in the
right column.

| Setting | Value | Check |
|---|---|---|
| Ruleset `main` (branch `main`) | PRs required; 1 approval; code owner review; stale reviews dismissed; conversations resolved; required check `CI success`; linear history; no force pushes; no deletion; bypass: repository admin, **pull requests only** | `gh api repos/clintgalendez/haritone/rulesets` |
| Ruleset `release tags` (`refs/tags/v*`) | only admins may create; no update or deletion | same |
| Environment `release` | required reviewer @clintgalendez; deployment tags `v*` only | `gh api repos/clintgalendez/haritone/environments/release` |
| Actions → default `GITHUB_TOKEN` | read-only; Actions may not approve PRs | `gh api repos/clintgalendez/haritone/actions/permissions/workflow` |
| Actions → fork PR workflows | require approval for first-time contributors | repository settings UI |
| Secret scanning and push protection | enabled | `gh api repos/clintgalendez/haritone --jq .security_and_analysis` |
| Dependabot alerts and security updates | enabled | `gh api repos/clintgalendez/haritone/vulnerability-alerts` (204 means enabled) |
| Private vulnerability reporting | enabled | `gh api repos/clintgalendez/haritone/private-vulnerability-reporting` |
| Code scanning | the CodeQL workflow (advanced setup); leave "default setup" off | Security tab |

Branch protection does not stop the owner from pushing directly when bypass is set to
"always". Haritone's bypass is **pull requests only**, so even the owner's changes go through a
PR with CI. Because GitHub does not let authors approve their own PRs, the owner merges their
own PRs using the bypass.

## Labels

`bug`, `enhancement`, `documentation`, `performance`, `security`, `dependencies`
(Dependabot), `good first issue`, `help wanted`, `needs-repro`, `needs-in-game-test`,
`api-limitation` (blocked on the Hytale API), `breaking-change`, `wontfix`, `duplicate`.

## Milestones

`0.1.0` (guide mode), `0.2.0` (in-game hardening), `0.3.0` (automatic movement prototype). See
[ROADMAP.md](ROADMAP.md).

## Versioning

[Semantic Versioning](https://semver.org). Before 1.0.0, minor versions may break the extension
API, and the CHANGELOG lists every break. Release candidates are `X.Y.Z-rc.N`. The version
lives in `gradle.properties`, and the release workflow refuses a tag that does not match it.

## Releasing

1. Open a PR that sets `version=X.Y.Z` in `gradle.properties` and moves the CHANGELOG
   "Unreleased" entries under the new version. Merge it once CI is green.
2. Tag the merge commit locally and push the tag. The tag ruleset allows only admins to do
   this:
   ```sh
   git tag -s vX.Y.Z -m "Haritone X.Y.Z"   # -a if you do not sign tags
   git push origin vX.Y.Z
   ```
3. The **Release** workflow:
   - checks that the tag is semver and matches the version,
   - runs the full build and tests,
   - builds the jar twice and compares checksums,
   - writes `SHA256SUMS` and a CycloneDX SBOM,
   - creates a build-provenance attestation, then
   - **waits for your approval** on the `release` environment.
4. Approve the deployment. The workflow then creates the GitHub release with generated notes,
   the jar, `SHA256SUMS`, and `haritone-X.Y.Z.cdx.json`. Tags with a hyphen (for example
   `-rc.1`) are marked as pre-releases.
5. Users can verify a download with:
   ```sh
   sha256sum -c SHA256SUMS
   gh attestation verify haritone-X.Y.Z.jar --repo clintgalendez/haritone
   ```

## Dependencies

- Versions are pinned exactly in `gradle/libs.versions.toml` (plugins included). Dynamic
  versions are not allowed.
- `gradle/verification-metadata.xml` pins the SHA-256 of every artifact Gradle resolves. A
  Dependabot PR that bumps a dependency fails verification until the metadata is regenerated.
  Check the PR, then on its branch run:
  ```sh
  ./gradlew --write-verification-metadata sha256 build :plugin:cyclonedxDirectBom --rerun-tasks --no-configuration-cache
  ```
  Commit the result after checking that only the expected artifacts changed.
- When updating the Hytale API (`hytaleServer` in the catalog), also update `ServerVersion` in
  `plugin/src/main/resources/manifest.json`, re-run the real-server checks in
  [TESTING.md](TESTING.md), and update [TECHNICAL_RESEARCH.md](TECHNICAL_RESEARCH.md).
- GitHub Actions are pinned to full commit SHAs, and Dependabot updates them.

## Authorship and attribution

- Commits must be authored by the person who wrote them. Never rewrite contributor authorship
  and never squash away credit you do not hold. When squash-merging a contributor's PR, keep
  them as the author (GitHub does this by default).
- Do not add `Co-authored-by` trailers for tools or bots. Release notes and README credits do
  not list automated tools as contributors.
- GitHub works out its contributor list from commit authorship and activity, so a public
  project that accepts contributions **will** show other contributors. That is expected and
  correct.
