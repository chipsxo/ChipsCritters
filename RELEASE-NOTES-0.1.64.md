# ChipsCritters 0.1.64 release candidate

AI-generated, open source (MIT). PUBLIC and DEV are built separately; only PUBLIC/source are candidates for public release. Nothing is published as a GitHub/Modrinth release until Chris approves it. Hypixel has not confirmed the mod is allowed; use at your own risk.

- Strict whole-message locraw now requires a server string and accepts optional gametype/mode/map/lobbyname strings, including lobby and limbo responses. Any valid response ends automatic retries. Malformed/prefixed/unknown-shape chat stays visible. Only /sr locraw explicitly opts into a visible reply; other mods cannot be mistaken for a human SEND_COMMAND. Early observation returns true and a late phase hides after normal default listeners. Stale reply entries are consumed/cleared.
- Doomspiral's countdown is 6,000 ms. Its double ding, earlier-throw readiness, ritual requirements and own-capture completion are unchanged.
- Relay defaults OFF, endpoint blank. Read-only signed route data defaults to the repo's routes-signed.json URL, with existing signature/schema/version/bounds/data-only checks, fallback and new-run-only activation. The private key remains offline and is never read by the mod or this release preparation.
- New minimumShardAdvantageCoins defaults to 1,000 per shard and is separate from the existing 0% minimumShardAdvantagePercent. Exactly 1,000 passes. Existing custom coin and percentage values are preserved; reset returns to 1,000. It filters the sell/salvage decision border/corner marker and decision-dependent tooltip line, not item actions or bag/stack profit. Pricing and fee rules are unchanged; highlight rendering is unchanged and not confirmed live.
- Route coordinates/order and Gimmiegold placement logic are unchanged from 0.1.63. Fresh counts: Cavern40, Forest45, Icy27; Haunted Money58, Unique66, Full73; Unique paintings-off58.

See docs/release for unpublished GitHub/Modrinth listing text, docs/ROUTE-UPDATES.md for exact local signing/publishing commands, THIRD_PARTY_NOTICES.md and LICENSE for licensing, and the local release report for test/audit evidence.

Three cleaned real gameplay images are approved for README/gallery use. Originals and the two 0.1.63 menu shots are not committed. Images use crops/solid masks only, with metadata stripped. They do not prove the RC passed live tests. No LGPL SkyHanni code was found in the reviewed tree; the derivative graph/data is from MIT SkyHanni-REPO with its MIT notice. Critter Mod is detected by mod ID for companion compatibility; no copied Critter Mod code/data was identified.

Release gates still require live lobby/Safari/debug locraw and Doomspiral checks, plus current-version menu retakes. No live testing is claimed by automated checks.
