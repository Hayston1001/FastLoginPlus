# Anti-bot and Mojang networking

## Connection limits

Configure connection limits on the standalone server or, in a proxy network, on the proxy. FLP does not apply them again on the proxied backend. They do not replace network-level flood protection.

| Option | Default | Unit / purpose |
| --- | --- | --- |
| `anti-bot.enabled` | `true` | Enable FLP connection checks |
| `anti-bot.connections` | `600` | Global connections in the window |
| `anti-bot.expire` | `10` | Global window, minutes |
| `anti-bot.per-ip-connections` | `20` | Per-IP long-window connections |
| `anti-bot.per-ip-expire` | `5` | Per-IP long window, minutes |
| `anti-bot.burst-limit` | `10` | Per-IP short-window connections |
| `anti-bot.burst-window` | `10` | Burst window, seconds |
| `anti-bot.ban-duration` | `5` | Temporary IP ban, minutes |
| `anti-bot.trusted-ips` | Empty | Explicit addresses bypassing FLP connection checks |

When a limit is reached, FLP rejects the connection with the `kick-antibot` message. The old `anti-bot.action` option was removed; do not add it back to the configuration.

Invalid limits/durations are diagnosed and normalized; use positive sensible values rather than relying on fallback. Incorrect forwarded IPs can make every player share the proxy address and trigger limits. Check forwarding before raising limits or trusting the proxy's IP indiscriminately.

## Mojang requests

| Option | Default | Purpose |
| --- | --- | --- |
| `mojang-retry-count` | `3` | Total attempts, not three extra retries |
| `mojang-retry-delay` | `500` | Base delay in milliseconds; exponential backoff capped at 10 seconds |
| `mojang-request-limit` | `600` | Direct name requests per 10 minutes, clamped to `0..600` |
| `ip-addresses` | Empty | Configured local outbound addresses for requests; not arbitrary public IP impersonation |
| `proxies` | Empty | HTTP request proxies, entries such as `host:port`; not Minecraft backend routing |

On FLP's standalone path, retry settings cover name lookup and its session verification. On proxies they cover FLP name lookup, **not** the proxy software's native session handshake. Rate-limit errors are not blindly retried like transient I/O errors. `0` direct requests requires a configured HTTP proxy; without one the limit is reset to 600.

Backend retry settings do not control proxy requests. Some Paper compatibility paths can still use backend `ip-addresses`, `mojang-request-limit` and `proxies` settings.

An unreachable or rate-limited lookup is not a cracked verdict. Check DNS, TLS, outbound rules and service availability. Disabling account protection or repeatedly reconnecting can make a rate-limit incident worse.
