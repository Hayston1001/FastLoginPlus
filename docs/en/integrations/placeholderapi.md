# PlaceholderAPI

The Bukkit/Folia expansion is registered when PlaceholderAPI is available. It is supplied by FLP; do not assume a separately downloaded eCloud expansion is required.

| Placeholder | Values / meaning |
| --- | --- |
| `%fastloginplus_status%` | `Premium`, `Cracked`, `Unknown`: FLP's cached runtime status |
| `%fastloginplus_is_premium%` | `true` or `false` from that status |
| `%fastloginplus_floodgate%` | `Java`, `Bedrock`, `Linked`, `Unknown` as available from cached platform state; missing platform state defaults to `Java` |

Example test with PlaceholderAPI installed:

```text
/papi parse me %fastloginplus_status%
```

These values are display information, not a durable database query or a permission to bypass authentication. An offline player or a connection whose status has not been populated can show a default/unknown result. Platform identity and Java premium status are different dimensions; do not merge them into one security check.

If text is not replaced, check PlaceholderAPI startup, FLP expansion registration, the exact placeholder name and the displaying plugin's PlaceholderAPI support. This expansion is not provided natively by the proxy plugins or Fabric mod.
