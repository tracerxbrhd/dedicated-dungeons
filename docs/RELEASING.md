# Releasing Dedicated Dungeons

Publishing is tag-driven. Normal branch pushes run verification but do not publish artifacts.

## GitHub configuration

Required repository secrets:

- `MODRINTH_TOKEN` — Modrinth API token.
- `CURSEFORGE_TOKEN` — CurseForge API token when CurseForge publication is desired.

Required repository variables:

- `MODRINTH_PROJECT_ID` — Dedicated Dungeons project ID or slug.
- `U_API_MODRINTH_PROJECT_ID` — U-API project ID or slug.
- `CURSEFORGE_PROJECT_ID` — optional Dedicated Dungeons project ID.
- `U_API_CURSEFORGE_PROJECT_ID` — required when CurseForge is configured.
- `U_API_REPOSITORY` — optional source repository, defaulting to `<owner>/u-api`.
- `U_API_REF` — optional ref, defaulting to `v<u_api_version>+mc<minecraft_version>`.

If U-API is private, `U_API_REPOSITORY_TOKEN` must grant read access. IDs belong in variables and API tokens belong in secrets.

## Verification and publishing

From a clean, synchronized `master` branch:

```powershell
.\scripts\release.ps1 -DryRun
.\scripts\release.ps1
```

The release channel is inferred from `mod_version`. For `1.0.0-beta.1` and Minecraft `1.21.1`, the tag is `v1.0.0-beta.1+mc1.21.1` and the publishing channel is `beta`.

The release workflow checks out the matching U-API source, runs a Java 21 clean build, unit tests and NeoForge GameTests, selects exactly one user JAR, publishes to Modrinth, optionally publishes to CurseForge, and creates the GitHub prerelease last.

Never move or reuse a published tag. Fix the issue, increment `mod_version`, commit, and publish a new tag.
