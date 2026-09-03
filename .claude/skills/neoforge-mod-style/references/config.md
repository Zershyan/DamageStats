# 配置规范

## ModConfigSpec：显示名走 translation

配置项用 `.translation(key)` 给出翻译键，翻译本身走 datagen lang 体系（见 `datagen-lang.md`）。`.comment(...)` 只放单位、取值含义这类技术说明，内容不和 translation 重复——前者写进 toml 给改文件的人看，后者是配置界面上的显示名。

配置的翻译单独一个 `EXConfigLang extends EXLang` 持有，用 `init(List)` 返回值风格：配置常量必须在 `EXConfig` 的静态初始化阶段就能引用。每条配置同时给出 toml 键名和翻译键，两者从同一处取，改名只动一个地方：

```java
public record ConfigEntry(String name, Lang lang) {
    public String getKey() { return ExampleMod.MODID + ".configuration." + name; }
}

public static final ConfigEntry SomeOption = entry("someOption", "Some Option", "某个选项");
```

```java
public static final ModConfigSpec.IntValue SomeOption = BUILDER
        .comment("单位 tick，20 tick = 1 秒")
        .translation(EXConfigLang.SomeOption.getKey())
        .defineInRange(EXConfigLang.SomeOption.name(), 100, 20, 12000);
```

翻译键格式固定 `<modid>.configuration.<配置名>`。配置项的常量名用 PascalCase 而不是全大写（见 `naming-and-layout.md`）——它们会被业务代码反复读取。

## 自定义配置文件

需要玩家频繁编辑、或者结构比 toml 更适合嵌套的，用独立 JSON 放 config 目录，并配一条 reload 指令。

- **标识一律英文，显示时才翻译。** 文件里写 `"Magic"`，界面上用 `Component.translatableWithFallback(key, 标识)` 翻成「魔法伤害」。玩家自己新增的标识没有对应翻译时会回落显示原文，而不是露出翻译键。翻译键由一个公开方法统一派生，登记和查询共用同一份逻辑。
- **一对多的映射写成「一 → 多的列表」**：`{"Magic": ["a", "b"]}` 而不是 `{"a": "Magic", "b": "Magic"}`。前者贴合玩家的心智模型，也少写一半 key。代码里按成员反查时自己建反向索引，正向结构保持和文件一致便于对照排查。同一成员被配到多个组时记一条 warn，别静默覆盖。
- **漏配必须能回落。** 不能因为某个 ID 没出现在配置里就从结果里消失——第三方 mod 随时新增内容，配置永远追不齐。
- **解析失败整体降级。** 记明确的错误日志，连带说明「删掉这个文件会在下次启动时重新生成默认内容」，不要让一处配置问题拖崩加载。
- 首次启动写出一份默认文件，内容只覆盖最常见的几项，作用是给玩家一份能照着改的样板，不追求穷举。
