const LOBBY_KEY = /^[A-Za-z0-9_-]{12,64}$/;
const ORIGIN = /^[0-9a-f-]{36}$/i;
const BIOMES = new Set(["ICY", "HAUNTED", "FOREST", "CAVERN"]);
const MAX_BODY_BYTES = 4096;
// Keep one maximum-length 60-minute Safari run plus a small reconnect margin.
const CLAIM_RETENTION_MS = 70 * 60 * 1000;
const MAX_EVENTS_PER_SYNC = 200;

function json(data, status = 200) {
	return new Response(JSON.stringify(data), {
		status,
		headers: {
			"Content-Type": "application/json; charset=utf-8",
			"Cache-Control": "no-store, max-age=0",
			"X-Content-Type-Options": "nosniff"
		}
	});
}

async function readBody(request) {
	const text = await request.text();
	if (new TextEncoder().encode(text).byteLength > MAX_BODY_BYTES) return null;
	try { return JSON.parse(text); } catch { return null; }
}

function validCommon(payload) {
	return payload && typeof payload.room === "string" && LOBBY_KEY.test(payload.room)
		&& typeof payload.origin === "string" && ORIGIN.test(payload.origin);
}

export default {
	async fetch(request, env) {
		const url = new URL(request.url);
		if (request.method !== "POST" || !["/v1/claim", "/v1/sync"].includes(url.pathname)) return json({ error: "not found" }, 404);
		const payload = await readBody(request);
		if (!validCommon(payload)) return json({ error: "invalid request" }, 400);
		if (url.pathname === "/v1/claim") {
			if (!BIOMES.has(payload.biome) || ![payload.x, payload.y, payload.z].every(Number.isFinite)
					|| Math.abs(payload.x) > 30_000_000 || Math.abs(payload.z) > 30_000_000
					|| payload.y < -2048 || payload.y > 2048) return json({ error: "invalid waypoint" }, 400);
		}
		if (url.pathname === "/v1/sync" && (!Number.isSafeInteger(payload.since) || payload.since < 0)) return json({ error: "invalid cursor" }, 400);

		const room = env.ROOMS.get(env.ROOMS.idFromName(payload.room));
		const internal = new Request(`https://room.internal${url.pathname}`, {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify(payload)
		});
		return room.fetch(internal);
	}
};

export class RoomState {
	constructor(state) {
		this.state = state;
		this.sql = state.storage.sql;
		this.rate = new Map();
		this.ready = state.blockConcurrencyWhile(async () => {
			this.sql.exec("CREATE TABLE IF NOT EXISTS relay_meta (id INTEGER PRIMARY KEY CHECK (id = 1), sequence INTEGER NOT NULL)");
			this.sql.exec("INSERT OR IGNORE INTO relay_meta (id, sequence) VALUES (1, 0)");
			this.sql.exec("CREATE TABLE IF NOT EXISTS waypoint_claims (sequence INTEGER PRIMARY KEY, origin TEXT NOT NULL, biome TEXT NOT NULL, x10 INTEGER NOT NULL, y10 INTEGER NOT NULL, z10 INTEGER NOT NULL, created_at INTEGER NOT NULL, UNIQUE(biome, x10, y10, z10))");
			await this.scheduleCleanup();
		});
	}

	async fetch(request) {
		await this.ready;
		const now = Date.now();
		this.sql.exec("DELETE FROM waypoint_claims WHERE created_at < ?", now - CLAIM_RETENTION_MS);
		const payload = await readBody(request);
		if (!validCommon(payload)) return json({ error: "invalid request" }, 400);
		let response;
		if (request.url.endsWith("/claim")) response = this.claim(payload, now);
		else if (request.url.endsWith("/sync")) response = this.sync(payload, now);
		else response = json({ error: "not found" }, 404);
		await this.scheduleCleanup();
		return response;
	}

	async alarm() {
		await this.ready;
		this.sql.exec("DELETE FROM waypoint_claims WHERE created_at < ?", Date.now() - CLAIM_RETENTION_MS);
		await this.scheduleCleanup();
	}

	async scheduleCleanup() {
		const row = this.sql.exec("SELECT MIN(created_at) AS oldest FROM waypoint_claims").toArray()[0];
		if (!row || row.oldest == null) {
			await this.state.storage.deleteAlarm();
			return;
		}
		await this.state.storage.setAlarm(Number(row.oldest) + CLAIM_RETENTION_MS);
	}

	claim(payload, now) {
		if (!BIOMES.has(payload.biome) || ![payload.x, payload.y, payload.z].every(Number.isFinite)
				|| Math.abs(payload.x) > 30_000_000 || Math.abs(payload.z) > 30_000_000
				|| payload.y < -2048 || payload.y > 2048) return json({ error: "invalid waypoint" }, 400);
		const previous = this.rate.get(payload.origin) ?? [];
		const recent = previous.filter(time => time > now - 60_000);
		if (recent.length >= 30) return json({ error: "rate limited" }, 429);
		recent.push(now);
		this.rate.set(payload.origin, recent);
		const x10 = Math.round(payload.x * 10), y10 = Math.round(payload.y * 10), z10 = Math.round(payload.z * 10);
		const duplicate = this.sql.exec("SELECT sequence FROM waypoint_claims WHERE biome = ? AND x10 = ? AND y10 = ? AND z10 = ?", payload.biome, x10, y10, z10).toArray()[0];
		if (duplicate) return json({ ok: true, duplicate: true, sequence: duplicate.sequence });
		const current = this.sql.exec("SELECT sequence FROM relay_meta WHERE id = 1").toArray()[0].sequence;
		const sequence = current + 1;
		this.sql.exec("UPDATE relay_meta SET sequence = ? WHERE id = 1", sequence);
		this.sql.exec("INSERT INTO waypoint_claims (sequence, origin, biome, x10, y10, z10, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
			sequence, payload.origin, payload.biome, x10, y10, z10, now);
		return json({ ok: true, duplicate: false, sequence }, 201);
	}

	sync(payload, now) {
		const rows = this.sql.exec("SELECT sequence, origin, biome, x10, y10, z10 FROM waypoint_claims WHERE sequence > ? AND created_at >= ? ORDER BY sequence LIMIT ?",
			payload.since, now - CLAIM_RETENTION_MS, MAX_EVENTS_PER_SYNC).toArray();
		const highWater = this.sql.exec("SELECT sequence FROM relay_meta WHERE id = 1").toArray()[0].sequence;
		return json({
			latestSequence: rows.length === MAX_EVENTS_PER_SYNC ? rows[rows.length - 1].sequence : highWater,
			events: rows.map(row => ({
				sequence: row.sequence,
				origin: row.origin,
				biome: row.biome,
				x: row.x10 / 10,
				y: row.y10 / 10,
				z: row.z10 / 10
			}))
		});
	}
}
