# DRAFT — ChipsCritters

Status: listing text only; not published. Icon omitted by Chris's approval. Use only approved edited gameplay images, with the captions below. No invented screenshots or live-test claims.

Summary: Fixed-coordinate Critter Safari routes, waypoints, run clocks and personal bests for Hypixel SkyBlock.

ChipsCritters is **AI-generated and open source (MIT)**. Third-party data keeps its documented license; see the source notices. **Hypixel has not confirmed this mod is allowed. Players use it at their own risk.**

Client-side Fabric, Minecraft 26.2, Java 25. Requires Fabric API. Modes: Money, Unique and Full Clear (solo). Compact route/arrow panels, waypoint ribbons/cubes, saved HUD positioning, an airship tip banner, fixed-spot Hideyho search and a six-second Doomspiral readiness cue with a double ding.

## Commands (PUBLIC)

/sr opens settings and is a shortcut for /chipscritters.

- /sr help, /sr controls
- /sr pause, /sr resume
- /sr skip [number], /sr back, /sr undo
- /sr refresh, /sr refresh claims (confirmation required)
- /sr rejoin, /sr newrun, /sr status
- /sr find hideyho, /sr find stop
- /sr stop, /sr restart, /sr report
- /sr history, /sr stats, /sr waypoints
- /sr locraw (one explicitly visible lobby reply)

Every listed command is checked against the built PUBLIC command tree. This is not a claim of live command execution testing. Do not list or upload DEV-only recording tools.

## Privacy, networking and updates

Only the Hypixel Bazaar API, read-only signed route-data URL and optional configured relay are used. Relay is off with a blank endpoint by default. All fresh users start with bundled routes. Downloaded route data must pass the pinned signature and safety checks, falls back to last-good/bundled data when missing/invalid/offline, and activates only between runs. No scripts/JARs/commands are downloaded or executed. The mod does not read session tokens, Microsoft login data, launcher/account files, Discord or passwords. No secrets or write access are bundled.

The Hunting Box compares exact Safari shards using sell-order less fee versus rarity Essence / 2,500 × Morning Dew sell-order value. A separate 1,000-coin per-shard difference filter is added, inclusive; custom values, existing percentage and fee settings are retained. Highlight rendering was not changed and is not confirmed working in-game.

## Project links

Source/homepage: https://github.com/chipsxo/ChipsCritters
Issues: https://github.com/chipsxo/ChipsCritters/issues
GitHub downloads: https://github.com/chipsxo/ChipsCritters/releases

Review and redact screenshots/logs before posting. Never post passwords, tokens or private keys.

## Gallery captions

1. Cavern route panel, ribbon and ladder instruction.
2. Haunted route panel and Doomspiral countdown.
3. Full-width airship tip banner during the Safari airship scene.

These approved real captures predate 0.1.64 and are not evidence of live RC testing. Retake both 0.1.63 menu screenshots before using them as current-version gallery images. Do not use NO SAFE PATH — FOLLOW ARROW screenshots as feature images.

## Upload checklist — only after publication approval

Create/select the actual Modrinth project and link it in the README once its official URL is verified. Use ChipsCritters-0.1.64.jar, exactly the same bytes and SHA-256 as the GitHub release. Link the open source archive. Never upload the DEV JAR. Keep the listing as a draft until Chris authorizes publication.
