# Shared source boundary

This Java 8 module has no production dependencies. Classic modules depend on its jar.
Fabric compiles the same source after package/import relocation; it does not depend on the jar.

Only model-independent configuration I/O, language loading/fallback and small message utilities belong here.
Parsers, resource ownership, logging and platform policy remain with callers. No resources are bundled here.

`verifySharedSources` rejects production dependencies, non-JDK/non-shared imports, package-name literals,
reflective class loading and resource lookup. Shared code must not use fully qualified external types,
construct package names dynamically, reflect on its own package, or load package-relative resources.
If a future helper needs a resource, its caller must resolve an absolute resource path and pass the stream.
Java 8 release compilation rejects newer JDK APIs; Fabric separately compiles relocated source on Java 25.

Configuration loaders are caller-supplied parser adapters, not authentication or platform models.
The classic adapter continues to use the same Bungee Configuration provider and its default-value semantics.
Language rendering keeps color translation, empty-message suppression and `/newline` conversion unchanged.
