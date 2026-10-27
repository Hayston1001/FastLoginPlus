# Build, test and contribute

## Build the project

Clone the repository and use its Gradle wrapper from the root directory. Gradle requires **JDK 25**; the build uses other Java toolchains as needed.

{% tabs %}
{% tab title="Windows" %}

```powershell
.\gradlew.bat build --console=plain
```

{% endtab %}
{% tab title="macOS / Linux" %}

```sh
./gradlew build --console=plain
```
{% endtab %}
{% endtabs %}

`build` runs the project's tests and checks. For work on one module, use a targeted task such as `./gradlew :velocity:build`.

## Contribute

Open issues or pull requests against the [FastLoginPlus repository](https://github.com/Hayston1001/FastLoginPlus). Describe the purpose of the change, affected platforms and checks you actually ran. Do not commit server data or secrets.

Before making changes, see the [architecture overview](architecture.md) and [contribution guide](https://github.com/Hayston1001/FastLoginPlus/blob/main/CONTRIBUTING.md). When changing user-facing behavior, update the relevant configuration, messages and English and Chinese documentation.
