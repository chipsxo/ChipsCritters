# Third-party notices

## Safari walk graph

The graph in `src/main/resources/data/chipscritters/safari_graph.json` is a reduced, tag-free derivative of `constants/island_graphs/SAFARI.json` from [SkyHanni-REPO](https://github.com/SkyHanniStudios/SkyHanni-REPO). Only node positions and neighbor distances are retained; mob-finding and other graph tags are omitted.

Copyright (c) 2022 hannibal2. Distributed under the MIT License. The full license text is in `src/main/resources/data/chipscritters/licenses/SKYHANNI-REPO-MIT.txt`.

## Haunted floor-drop coordinates

The candidate coordinates in `src/main/resources/data/chipscritters/routes.json` are transcribed from the Hypixel SkyBlock Wiki's [Floor Drops](https://hypixelskyblock.minecraft.wiki/w/Floor_Drops) page, which lists known Critter Safari floor-drop locations. Those coordinate data are attributed to the wiki and are covered by its CC BY-NC-SA 3.0 license; the rest of the ChipsCritters source remains MIT-licensed. Drop availability and reward contents are random.

The ritual-cave waypoint near `48, 68, -19` is an approximate community-reported location from this [Critter Safari guide thread](https://www.reddit.com/r/HypixelSkyblock/comments/1ut8p5z/how_to_fight_the_warden_in_the_new_hunting_island/).

## Haunted Hideyho locations

The Hideyho candidate positions are based on the `HIDEYHO_LOCATION` entries in the SAFARI island graph from [SkyHanni-REPO](https://github.com/hannibal002/SkyHanni-REPO/blob/main/constants/island_graphs/SAFARI.json). The Hideonwall and Gazer bed coordinates were supplied by the user.

## Gemzie podium

The Gemzie podium waypoint at `-140, 61, 50` is based on a [Hypixel forum guide](https://hypixel.net/threads/how-to-catch-all-safari-zone-critters.6128398/) that documents where to place the Purple, Lime, and Orange Gems to spawn Gemzies.

## Gradle wrapper

The Gradle wrapper scripts and wrapper JAR are from the official Gradle distribution and are licensed under Apache-2.0. The scripts include their license header; the wrapper JAR contains its license metadata.


## Critter Mod compatibility credit

[Critter Mod](https://modrinth.com/project/critter-mod), [official source](https://github.com/MrCloudy2/critterMod), is a separate MIT-licensed project. Its full upstream MIT notice is included as a compatibility credit in `src/main/resources/data/chipscritters/licenses/CRITTER-MOD-MIT.txt`. ChipsCritters has optional companion-mode detection using the `crittermod` mod identifier; it does not bundle that mod. The reviewed ChipsCritters tree contains no imported Critter Mod code or data requiring an upstream code/data license notice. The upstream notice is retained even though no copied upstream code/data was identified. This credit does not relicense Critter Mod.

## SkyHanni code versus SkyHanni-REPO data

Only the MIT-licensed SkyHanni-REPO graph/data derivative described above is included, with its full MIT notice. The reviewed tree has no SkyHanni LGPL source/packages or dependency. Compatibility chat/command names are independently handled through Fabric. No LGPL SkyHanni code was identified in this release audit; any discovery of copied LGPL code is a publication blocker pending review.
