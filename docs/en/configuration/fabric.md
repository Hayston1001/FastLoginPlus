# Fabric configuration

File: `config/fastloginplus-fabric.yml` on the Fabric backend. Stop the server to edit it, then restart. This file controls local settings only; configure Java and Bedrock login policies in [Velocity configuration](velocity.md).

| Option | Default | Purpose |
| --- | --- | --- |
| `language` | `en` | Backend message language: `en` or `zh`; other nonempty values warn and use English |
| `debug` | `false` | Enable diagnostic logs |
| `check-update` | `true` | Check for new releases in the background; does not download or install them |
| `update-check-interval` | `24` | Update check interval in hours; values below 1 use 24 |
| `allowed-proxy-ids` | Empty | Trusted Velocity proxy UUIDs, comma-separated; obtain them from the proxy's `plugins/fastloginplus/proxyId.txt` |

`allowed-proxy-ids` is not the forwarding secret. Leaving it empty affects Bedrock login and premium binding that require a trusted proxy identity. The proxy's `forwarding.secret` belongs in FabricProxy-Lite's configuration. See the [Fabric installation guide](../installation/fabric.md) for setup and EasyAuth settings.
