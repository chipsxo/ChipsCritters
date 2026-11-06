# Signed route data — 0.1.64

The read-only endpoint is https://raw.githubusercontent.com/chipsxo/ChipsCritters/main/routes-signed.json . The file may be absent at launch: PUBLIC then keeps bundled routes (or a previously verified cache). No unsigned placeholder is hosted at that path.

All fresh installs begin with identical bundled waypoint data. A signed update must pass Ed25519 verification with the pinned public key, schema 1, increasing dataVersion, supported minModVersion, fixed coordinate/biome bounds and data-only field validation. It cannot contain code, scripts, JARs, commands or URLs. Route data never overwrites personal config, keybinds, HUD positions, mode, learned stops, PBs or history. Missing/bad/offline responses do not erase last-good/bundled data. Downloads are background/time-limited and cooldown bounded; no chat spam. Activation happens only at a new-run boundary.

## Chris: export locally using DEV

1. Install only the local DEV JAR, not PUBLIC and DEV together.
2. Finish any recordings with the existing recorder commands.
3. Run `/sr record export 1` for the first data release, increasing the number on every later release. It writes `routes-export.json` in your game's `config/chipscritters-routes` folder.
4. Review the data before signing. Do not publish personal configs/logs or account files.

## Sign on your own PC

Keep `private.pk8` outside the mod/project/repository. Never upload, paste or share it. Keep an encrypted offline backup. The mod contains only the pinned PUBLIC key. Do not generate a replacement key for an existing client unless intentionally shipping a new trust key in a mod update.

With Java 25 JDK on PATH, from the cloned source folder in PowerShell:

```powershell
$ccRoutes = "$env:APPDATA\ModrinthApp\profiles\SkyBlock Enhanced\config\chipscritters-routes"
java .\tools\RouteSigner.java sign "$env:USERPROFILE\.chipscritters-signing\private.pk8" "$ccRoutes\routes-export.json" "$ccRoutes\routes-signed.json"
```

Change `$ccRoutes` only if your actual launcher profile differs. This is a command for Chris to run himself; Codex does not need to read the key. A public source build does not require a private key.

The exact hosted filename is **routes-signed.json**, a UTF-8 envelope:

```json
{"payload":"BASE64_OF_EXACT_UTF8_EXPORT_BYTES","signature":"BASE64_ED25519_SIGNATURE"}
```

Do not edit the payload after signing. Publish only the signed envelope as the endpoint.

## Publish routes yourself, after approval

1. Open https://github.com/chipsxo/ChipsCritters .
2. Add/replace **routes-signed.json** at the root of **main**, using GitHub Add file → Upload files or your own Git checkout.
3. Upload only that signed data file; never the private key or its directory.
4. Commit the data update. Its Raw URL is the endpoint above; the mod already knows it.
5. On later changes increase dataVersion, export, review, sign and replace the same file.

A received update is staged for a new run, never forced into a running route. Disable checks in Tools → Route data if desired. Relay is separate, still off by default, and has no default endpoint.
