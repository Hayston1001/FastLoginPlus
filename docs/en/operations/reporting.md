# Report a problem

Open an issue in [FastLoginPlus](https://github.com/Hayston1001/FastLoginPlus/issues), not accidentally in the upstream FastLogin project. Read existing reports first.

Include:

1. FLP version **and commit/build source**, especially for unreleased 1.0.0 development artifacts.
2. Server/proxy type and exact version, Java runtime, auth plugin version, and relevant ProtocolLib/Floodgate/Fabric mods.
3. Deployment: direct or proxy, forwarding mode, which side contains each plugin, and storage type. Do not reveal secrets.
4. Account situation: new or existing, password or bound, Java or Bedrock/linked, expected UUID policy and recent commands/upgrades.
5. Numbered reproduction steps, expected result and actual player-visible result. Say whether reconnect/restart changes it.
6. Sanitized startup and one attempt's debug logs from both proxy and backend, plus relevant config. Include timestamps so the attempt can be correlated.
7. What you have already tried and the result of each attempt.

Remove passwords, generated credentials, proxy IDs, forwarding secrets, private IPs if needed and personal data. Never attach the full authentication database. If the issue is a vulnerability or account takeover, use GitHub's private vulnerability reporting where available rather than publishing exploit details or live secrets in a public issue.

For documentation problems, include the page link and the incorrect text. For login problems, include the message the player actually saw; a skin alone does not establish whether verification succeeded.
