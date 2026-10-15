# Security policy

## Supported versions

| Version | Supported |
|---|---|
| Latest release (0.1.x) | ✅ |
| Older releases | ❌ (please upgrade) |

## Reporting a vulnerability

**Do not open a public issue.** Report privately through GitHub:
**Security → Advisories → [Report a vulnerability](https://github.com/clintgalendez/haritone/security/advisories/new)**.

Please include the affected version, the Hytale server version, steps to reproduce, and the
impact you expect, for example:

- command arguments that crash or stall a world thread,
- path traversal in player data files, or
- a way around a permission check.

## What happens next

| Step | Target time |
|---|---|
| Acknowledge the report | 3 working days |
| Confirm or dismiss it, with a severity assessment | 10 working days |
| Release a fix for confirmed high or critical issues | 30 days, or sooner for actively exploited issues |

Fixes are released as patch versions with a GitHub security advisory. Reporters are credited
unless they ask not to be. Please keep the details private until the advisory is published.

## Scope and design notes

- Haritone runs inside the Hytale server, with the server's permissions. It makes no network
  calls, and it stores only waypoints and preferences per player
  (`mods/Haritone_Haritone/players/`).
- Every command argument and config value is validated. Player data files are size-capped,
  validated entry by entry, and written atomically.
- Releases carry SHA-256 checksums, a CycloneDX SBOM, and a GitHub build-provenance
  attestation (`gh attestation verify`).
- Supply-chain controls are described in [docs/MAINTAINING.md](docs/MAINTAINING.md): pinned
  actions, dependency checksums, CodeQL, Dependabot, and OpenSSF Scorecard.
