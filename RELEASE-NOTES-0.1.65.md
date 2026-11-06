# ChipsCritters 0.1.65 release candidate

Adds the active Money, Unique or Full Clear mode to the main route panel and Move HUD preview. The same row keeps biome, stop counter and distance/status. It reads the current mode every render. Long rows scale to the existing padded panel width without truncating text or changing panel dimensions, saved positions, progress bar, party line, timer or routing.

RouteHud.java renders the row; RoutePanelText.java shares exact formatting and measured fitting. Patch65RegressionTests covers all three names, Waiting, the longest requested case and fitting at GUI scales 1–4 with small-window metrics. Actual Minecraft visual checks for the new row are pending.

Chris confirmed the four 0.1.64 live checks passed: locraw visibility, normal Safari routing, six-second Doomspiral prompt/double ding and Hunting Box highlights. Those implementations are retained unchanged. Source is uploaded to the public repository; GitHub/Modrinth releases remain unpublished pending approval. The approved historical gallery images do not show this new mode row; old-version menu captures still need retakes.

Counts unchanged: Cavern 40, Forest 45, Icy 27 in all modes; Haunted Money 58, Unique 66 (58 with Hideonwall off), Full Clear 73. See README and third-party notices for MIT/source disclosures and build instructions.
