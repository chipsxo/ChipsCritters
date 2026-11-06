# Data access and security reporting

ChipsCritters is AI-generated and open source (MIT), with third-party data licenses listed separately. Hypixel has not confirmed it is allowed; players use it at their own risk.

Runtime HTTPS destinations are the public Hypixel Bazaar API, the configured read-only signed route-data URL, and an optional user-configured relay. Relay is off by default with no endpoint. The mod does not download/run code, read login/session/Microsoft/launcher/Discord/password data or bundle write credentials. Route updates are data only, signature/schema/version/bounds checked, staged between runs, with last-good/bundled fallback.

Do not put passwords, tokens, private keys, account/launcher files or unreviewed logs in issues or commits. Review and redact latest.log before posting. Public bug reports: https://github.com/chipsxo/ChipsCritters/issues . Do not post a live credential or private signing key to demonstrate a problem.

The standalone RouteSigner tool is an offline maintainer utility, separate from the Minecraft mod. Only Chris runs it with his explicitly chosen private-key path; the mod never reads that key. Keep private.pk8 outside the repository and all artifacts, with an encrypted offline backup.
