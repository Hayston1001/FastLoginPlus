# Geyser and Floodgate

Geyser translates Bedrock connections to Java protocol. Floodgate supplies a separately authenticated Bedrock identity and optional Java linking. FLP does not replace either product or infer their authentication from a prefix.

## Choose the authentication mode

Do not assume "Geyser without Floodgate" means "no authentication". Geyser's selected Java authentication mode determines whether a Java login is performed. FLP's plain-Geyser online path can follow the Java decision tree; a non-online path must not gain premium login merely because a name matches a paid Java account.

Install Geyser/Floodgate according to their own [setup guides](https://geysermc.org/wiki/geyser/setup) and [Floodgate documentation](https://geysermc.org/wiki/floodgate/setup). Their current protocol/Java requirements are independent of FLP's bytecode minimum.

## Classic deployments

Place Geyser/Floodgate where that deployment's official guide requires. On proxy networks FLP takes identity and policy from the proxy and can forward signed Bedrock facts to the backend. AuthMe's supported name-based pre-login API can then prepare/register/login before its gate. This also covers the appropriate Spigot pre-login path, not only Paper configure events.

An optional backend Floodgate API path exists for compatibility. It must not silently override a proxy declaration with backend-local policy or duplicate the proxy's identity decision. Keep names/prefixes stable, preserve trust files, and test linked/unlinked cases separately.

Automatic registration and login are controlled by [Bedrock policies](../configuration/bedrock.md). Failed registration/login cleanup can require operator repair; do not reset accounts or log generated credentials to force a successful result.

## Fabric's narrower boundary

All of these are required for the current Bedrock auto-login path:

1. A fresh signed proxy claim bound to the actual connection's name and Floodgate UUID, from an allowed proxy.
2. An **unlinked** identity and permitting proxy auto-login policy.
3. An existing completed EasyAuth account with exactly the same name/UUID, a nonempty stored credential, an appropriate non-premium account kind and no forced UUID override.
4. Successful EasyAuth policy checks, readable storage and a successful account update before authentication completes.

Missing/invalid/expired/replayed claims are rejected; unknown/unreadable account state never grants login. Replay tracking is process-local, not a promise of persistence across restart. The stored credential establishes completed registration, not authentication of the current connection; that comes from the trusted Floodgate claim.

This path does **not** create new Bedrock accounts, accept linked accounts, use EasyAuth native Floodgate bypass or convert them into Java premium accounts. A Bedrock player denied here needs a separately supported provisioning/recovery procedure, not fabricated Java UUIDs or disabled checks.
