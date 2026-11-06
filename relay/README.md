# ChipsCritters waypoint relay

This open-source Cloudflare Worker relays fixed route-stop claims between ChipsCritters clients in the same Hypixel Critter Safari lobby. The mod reads the current lobby with Hypixel's `/locraw` response, hashes the lobby server ID locally, and uses that hash as the relay room. Players do not create or share room codes, and the relay uses no party chat.

## Deploy

1. Create a Cloudflare account and install Node.js.
2. In this folder, run `npx wrangler login` and then `npx wrangler deploy`.
3. Copy the Worker HTTPS URL into `/chipscritters` → **Tools** → **Relay settings**.
4. Everyone who wants relay updates enters the same Worker HTTPS URL and enables the relay. Lobby selection and waypoint claims are automatic; no room code is needed.

For local development, run `npx wrangler dev`. The local HTTP endpoint is accepted by the mod only for `localhost` and `127.0.0.1`.

## Data and privacy

- The relay accepts a locally hashed lobby key, a random per-client session token, biome name, and rounded waypoint coordinates.
- It does not receive Minecraft usernames, UUIDs, party messages, chat, raw lobby IDs, or inventory data.
- Claims are deleted after 70 minutes by a Durable Object alarm, covering the 60-minute Safari run limit plus a reconnect margin.
- The lobby hash separates ordinary relay traffic by Hypixel lobby; it is a routing key, not identity verification. A person who can determine the lobby ID and uses the same Worker could reproduce that key.
- Cloudflare operates the HTTPS endpoint and may process standard connection metadata such as the connecting IP address. This Worker does not log request bodies or client addresses.
- You deploy and control your own relay endpoint; the mod has no hard-coded third-party endpoint.

Clients poll every two seconds while connected to a Critter Safari lobby. When one client reaches a route stop, it sends the biome and coordinate. Other clients in that lobby skip that matching stop and re-optimize their remaining stops from their current position. The biome gate remains waypoint #1 until that client reaches it. Small remaining routes use an exact shortest-route solver over the bundled walking graph; larger routes use a bounded multi-start heuristic.
