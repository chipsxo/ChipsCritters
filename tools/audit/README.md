# Offline route audit

Run `gradlew.bat routeLegAudit` from the project root. It writes `work/audit56-map.json`, covering all static biome/mode legs, required Cavern climbs/waterfall, the known post-Doomspiral leg and three logged clip positions. It does not render Minecraft or certify live walkability.

For the optional historical cached-terrain comparison, run `python tools/audit/audit56.py`, then `python tools/audit/reportaudit56.py` from the project root. This developer-only analysis reads the local Modrinth profile Bobby region cache through terrain48.py. The saved cache IDs are specific to Chris's test map; missing cells are reported unknown. It never ships or runs inside the mod and uploads nothing. Exact partial-block states/shapes and live chunks are not reproduced. The alternate-path fixture is an offline comparison result, not a downloaded route source.

The mod's runtime has its own loaded-chunk and collision checks. A cached-clear result is weaker evidence than a live rendered test. Read every unresolved case in the exported report and record it with `/sr record next` if it actually fails in-game.
