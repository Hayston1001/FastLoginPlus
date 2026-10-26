# Languages, logs and update checks

## Select a language

Classic platforms use `language` in their own `config.yml`, loading `messages_<language>.yml` from that process's plugin folder. Built-in languages are `en` and `zh`. Custom codes such as `ja` are allowed with a matching UTF-8 file; invalid codes fall back to English. A proxy and backend can use different languages because each owns its own messages.

Fabric uses `language` in `config/fastloginplus-fabric.yml` and optional overrides in `config/fastloginplus-fabric/messages_en.yml` or `messages_zh.yml`. It supports `en`/`zh`; other nonempty selections warn and use English. Restart to apply changes.

## Missing and outdated messages

Language files carry `_messages-version`. Startup appends missing keys using the bundled selected language where available and English as fallback. Versioned migration can refresh recognizable old defaults or leaked English defaults without replacing genuinely customized values. Extra keys are retained and diagnosed, not silently deleted. Do not edit the version marker to suppress a migration.

Broken YAML is reported and bundled messages are used instead of disabling the whole plugin. This does not mean your broken custom file was repaired. Fix the syntax and restart. Configuration template refresh and language-file upgrades are different mechanisms; comments/order in language overrides are preserved rather than wholly regenerated.

Keep message keys and replacement tokens exactly as bundled. Classic rendering uses its color/multiline conventions; Fabric has its own string-to-component handling and flat YAML restrictions. Do not assume every platform treats an empty value, multiline escape or token identically. Copy examples from that platform's generated/bundled file.

## Diagnostic logging

Set `debug: true` on the process being investigated and restart. Diagnostic messages appear at the plugin's normal log level; changing the logging framework's DEBUG threshold is not required. For proxy failures, collect both sides' logs over the same connection attempt.

Turn diagnostics off after reproduction. Remove database passwords, forwarding secrets, proxy IDs and personal data before publishing logs/configs. Never publish generated auth credentials or an entire authentication database.

## Update checks

`check-update: true` enables background GitHub release checks. `update-check-interval: 24` is in **hours**. This does not download/install JARs or restart servers. Classic backends can notify operators; Fabric reports updates to its log. Network failure in the checker is not evidence that player authentication failed.

A development build still carrying an older version label may not compare like the future 1.0.0 release. Record the actual commit when reporting it. See [reporting](reporting.md).
