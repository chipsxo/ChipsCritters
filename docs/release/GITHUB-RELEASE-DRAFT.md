# DRAFT — ChipsCritters 0.1.65 release candidate

Do not publish without Chris's release approval. Proposed tag: v0.1.65. Mark as pre-release while in testing. This file is prepared release text, not a live GitHub Release.

AI-generated, open source (MIT) Fabric client mod for Minecraft 26.2 / Java 25. Hypixel has not confirmed it is allowed; use at your own risk.

- Route panel now includes Money, Unique or Full Clear alongside biome, stop and distance/status, including the Move HUD preview. Long lines fit without truncating fields; saved panel positions and routing are unchanged.
- Lobby/limbo locraw replies now parse and stop bounded retries. Other-mod and automatic replies stay hidden; only /sr locraw explicitly reveals one reply. Default Fabric listeners see the JSON before the late hide phase.
- Doomspiral countdown is six seconds; earlier throws prove readiness, and the existing once-per-summon double ding remains.
- Relay defaults off with no endpoint. Signed route-data checks use the read-only GitHub endpoint, retaining bundled/last-good data when unavailable or invalid and never changing an active run.
- Hunting Box: new separate 1,000-coin per-shard difference filter (inclusive), custom settings preserved, percentage/fee/pricing rules and highlight renderer unchanged. Chris confirmed highlight visibility in the 0.1.64 live test; this patch does not change it.
- Route coordinates/order and Unique Gimmiegold insertion logic are unchanged from 0.1.63.

/sr opens settings; it is a shortcut for /chipscritters. See README for the verified PUBLIC command list. PUBLIC has no recorder commands/classes.

Attach only ChipsCritters-0.1.65.jar, source archive/source JAR and matching SHA256SUMS.txt. Never attach the DEV JAR. GitHub and Modrinth must use the exact same PUBLIC JAR/checksum.

Review the test report before publication. Automated tests do not substitute for Chris's live lobby/Safari/Doomspiral checks. Gallery images are real user captures approved after privacy masks; old-version menus are excluded pending 0.1.65 retakes. Icon omitted by approval.

## Runtime data access

Bazaar API: https://api.hypixel.net/v2/skyblock/bazaar . Signed route data: https://raw.githubusercontent.com/chipsxo/ChipsCritters/main/routes-signed.json . Optional configured relay only. No downloaded code is run, no login/session/Microsoft/launcher/Discord/password files are read, and no secret/write credential is bundled.

Source: https://github.com/chipsxo/ChipsCritters . Issues: https://github.com/chipsxo/ChipsCritters/issues . Review/redact logs before posting.

Chris reported all four 0.1.64 live checks passed: locraw visibility, Safari activation, six-second Doomspiral/double ding and Hunting Box highlights. The new mode row still needs an in-game visual check.
