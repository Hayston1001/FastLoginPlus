# 构建, 测试与贡献

## 构建项目

克隆仓库后, 在根目录使用项目自带的 Gradle wrapper. 运行 Gradle 需要 **JDK 25**; 构建会按需使用其他 Java 工具链.

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

`build` 会运行项目的测试和检查. 只处理某个模块时, 可以使用定向任务, 例如 `./gradlew :velocity:build`.

## 参与贡献

请向 [FastLoginPlus 仓库](https://github.com/Hayston1001/FastLoginPlus)提交问题或拉取请求. 描述变更的目的, 影响的平台和实际执行的验证. 不要提交服务器数据或密钥.

开始修改前, 可先阅读[架构概览](architecture.md)和[贡献指南](https://github.com/Hayston1001/FastLoginPlus/blob/main/CONTRIBUTING_zh.md). 修改用户可见行为时, 请同步相关配置, 消息及中英文文档.
