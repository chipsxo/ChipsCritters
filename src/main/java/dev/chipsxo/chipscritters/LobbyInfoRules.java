package dev.chipsxo.chipscritters;

import java.util.LinkedHashMap;
import java.util.Map;

/** Strict parser and predicates for Hypixel's one-line locraw JSON response. */
public final class LobbyInfoRules {
	public record Info(String server, String gametype, String mode, String map, String lobbyname) { }

	private LobbyInfoRules() { }

	public static Info parse(String input) {
		if (input == null) return null;
		Parser parser = new Parser(input);
		Map<String, String> values = parser.parseObject();
		if (values == null || parser.hasRemaining()) return null;
		String server = values.get("server");
		String gametype = values.get("gametype");
		String mode = values.get("mode");
		String map = values.get("map");
		if (server == null || server.isBlank() || values.keySet().stream().anyMatch(k -> !java.util.Set.of("server","gametype","mode","map","lobbyname").contains(k))) return null;
		return new Info(server, gametype == null ? "" : gametype, mode == null ? "" : mode, map == null ? "" : map, values.getOrDefault("lobbyname", ""));
	}

	public static boolean isCritterSafari(Info info) {
		return info != null && "SKYBLOCK".equalsIgnoreCase(info.gametype().trim())
				&& "safari".equalsIgnoreCase(info.mode().trim());
	}

	private static final class Parser {
		private final String text;
		private int index;

		private Parser(String text) { this.text = text; }

		private Map<String, String> parseObject() {
			skipWhitespace();
			if (!consume('{')) return null;
			Map<String, String> values = new LinkedHashMap<>();
			skipWhitespace();
			if (consume('}')) return values;
			while (index < text.length()) {
				skipWhitespace();
				String key = readString();
				if (key == null) return null;
				skipWhitespace();
				if (!consume(':')) return null;
				skipWhitespace();
				String value = readString();
				if (value == null) return null;
				if(values.putIfAbsent(key, value)!=null)return null;
				skipWhitespace();
				if (consume('}')) return values;
				if (!consume(',')) return null;
			}
			return null;
		}

		private String readString() {
			if (!consume('"')) return null;
			StringBuilder value = new StringBuilder();
			while (index < text.length()) {
				char character = text.charAt(index++);
				if (character == '"') return value.toString();
				if (character < 0x20) return null;
				if (character != '\\') {
					value.append(character);
					continue;
				}
				if (index >= text.length()) return null;
				char escaped = text.charAt(index++);
				switch (escaped) {
					case '"', '\\', '/' -> value.append(escaped);
					case 'b' -> value.append('\b');
					case 'f' -> value.append('\f');
					case 'n' -> value.append('\n');
					case 'r' -> value.append('\r');
					case 't' -> value.append('\t');
					case 'u' -> {
						if (index + 4 > text.length()) return null;
						try { value.append((char) Integer.parseInt(text.substring(index, index + 4), 16)); }
						catch (NumberFormatException exception) { return null; }
						index += 4;
					}
					default -> { return null; }
				}
			}
			return null;
		}

		private boolean hasRemaining() {
			skipWhitespace();
			return index != text.length();
		}

		private void skipWhitespace() {
			while (index < text.length()) {
				char value = text.charAt(index);
				if (value != ' ' && value != '\t' && value != '\r' && value != '\n') return;
				index++;
			}
		}

		private boolean consume(char expected) {
			if (index >= text.length() || text.charAt(index) != expected) return false;
			index++;
			return true;
		}
	}
}
