---
name: neoforge-mod-style
description: NeoForge 1.21.x Minecraft 模组的代码风格、命名规范与模块模板。新建或修改 NeoForge + Mixin + KubeJS 模组代码时使用，覆盖包结构与命名、注册表、datagen 多语言体系、配置项与自定义配置文件、软兼容 compat 层、Mixin 组织与前缀约定、自定义事件 API 分层、KubeJS 事件镜像与 @Info 标注。
---

# NeoForge 模组代码规范

适用于 NeoForge 1.21.x / Java 21 / Parchment 映射的模组工程。

## 占位约定

全文示例统一使用一套虚构标识，落地到具体项目时整体替换：

| 占位 | 含义 | 取值规则 |
|---|---|---|
| `com.example.examplemod` | 根包 | `<group>.<modid>` |
| `ExampleMod` | `@Mod` 主类 | 驼峰全名 + `Mod` 后缀 |
| `examplemod` | modid | 全小写无分隔 |
| `EX` | 基础设施类前缀 | modid 缩写，大写 |
| `ex$` | Mixin 新增成员前缀 | modid 缩写，小写 + `$` |
| `othermod` / `OtherMod` | 被兼容的第三方 mod | 对方的 modid |

前缀取 modid 首字母缩写，两到四个字母，全项目统一，**不允许出现第二种写法**。

## 架构主线

功能沿一条固定路径下沉，每一层职责单一：

```
Mixin 改底层行为
   ↓ 把改动点抽成事件
api/event 自定义 Event
   ↓ handler 桥接原版事件 / api/helper 提供门面
kubejs 镜像事件 + <EX>Api 绑定
   ↓
整合包作者用 JS 脚本控制数值与逻辑
```

含义是：**Java 只负责开洞和提供能力，具体数值和玩法交给脚本**。写新功能先问一句「这个东西整合包作者需要改吗」，需要就配套镜像事件或 DataComponent，而不是把常量写死在 Java 里。

跨 mod 调用另有一条硬规则：只允许经过 `compat` 层。

## 十条铁律

1. **前缀只给基础设施类**：注册、datagen、api 入口、kubejs 入口，如 `EXItems` / `EXLangProvider` / `EXKubePlugin` / `EXApi`。业务类（物品、Handler、Helper、Component、Event）不加前缀。
2. **ResourceLocation 一律走 `ExampleMod.id("path")`**。只有指向别的 mod 命名空间时才写 `ResourceLocation.fromNamespaceAndPath("othermod", ...)`。
3. **Mixin 包名 = 目标 mod 的 modid**：`mixin/<modid>/MixinXxx.java`。改原版或自家类放 `mixin/examplemod/`。配套的 `IMixinConfigPlugin` 按包名逐段检查 mod 是否加载，因此**放对包就等于写好了加载条件**。
4. **Mixin 新增的一切成员加 `ex$` 前缀**：`@Unique` 字段、`@Unique` 方法、`IMixinXxx` 桥接接口方法、以及写进 `CustomData` 的临时 tag key。
5. **注入器优先用 MixinExtras**：`@WrapOperation` > `@Redirect`；`@ModifyReturnValue` > `@Inject(at = RETURN)`；取局部变量用 `@Local` + `LocalFloatRef` / `LocalRef`，不靠 `@Inject` 尾参数硬数索引。
6. **跨 mod 类不允许在普通代码里直接引用**：必须写成 `CompatFactory.OtherMod` 上的方法，方法体用 `testLoadedAndRun` / `testLoadedAndCall` 包住。调用方永远只看到 `CompatFactory.OtherMod.doSomething(...)` 这种形态。
7. **翻译在 Java 里声明，不手写 lang json**：`EXKeyLang.entry(key, en, zh)` 一行同时完成「登记多语言」和「拿到可直接用的 `MutableComponent`」。带参数的用 `entryLazy` 拿 `LazyComponent`。
8. **暴露给 KubeJS 的一切都要 `@Info("说明")`**，ProbeJS 靠它生成 `.d.ts`。不希望 JS 看见的构造器和 `@SubscribeEvent` 方法要 `@HideFromJS`。
9. **注释只解释「为什么」**，字段语义写在 `@Info` 里而不是 Javadoc 里。commit 一句话说清做了什么。
10. **不为不可能的情况加防御**：早返回 + 单行 if 是默认写法，`if(x) return;` 不带花括号。只在跨 mod、反射、异步这三类边界上 try-catch，并且是有意识地静默降级。

## 模块索引

| 要做的事 | 读哪个文件 |
|---|---|
| 加物品 / 属性 / DataComponent / 网络包 | `references/registry.md` |
| 加翻译、加 datagen provider | `references/datagen-lang.md` |
| 加配置项、写自定义配置文件 | `references/config.md` |
| 对接别的 mod（软依赖、可选功能） | `references/compat.md` |
| 写或改 Mixin | `references/mixin.md` |
| 定义可被外部监听 / 修改的事件 | `references/event-api.md` |
| 把功能开放给 KubeJS 脚本 | `references/kubejs.md` |
| 包结构、格式细节、反模式 | `references/naming-and-layout.md` |

## 格式速查

- 缩进 4 空格，UTF-8，在 `build.gradle` 里固定 `options.encoding = 'UTF-8'`。
- `if(cond) return;` —— 条件括号前不空格、单行、无花括号。多分支才用花括号。
- 注解参数超过一个就每个参数独立成行：

  ```java
  @Inject(
          method = "hurt",
          at = @At(value = "INVOKE", target = "...", shift = At.Shift.AFTER)
  )
  ```

- 方法参数超过三四个就每个参数独立成行（override 原版方法、Mixin handler 基本都这样）。
- `@Nullable` / `@NotNull` 统一用 `org.jetbrains.annotations.*`，不混用 `javax.annotation`。override 原版方法时补齐参数上的 `@NotNull`。
- Builder 方法名 = 字段名，**不带 `set` 前缀**，返回 `this`：`component.velocity(3).crit(true)`。
- 多重失败要退到同一个兜底分支时，用带标签的块而不是嵌套 if：

  ```java
  applyRun : {
      if(!event.isApplied()) break applyRun;
      ...
      original.call(instance, modifiedArg);
      return;
  }
  original.call(instance, originalArg);
  ```

- 善用 Java 21：`switch` 表达式 + 模式匹配、record、`instanceof` 绑定、`List.getFirst()`。

## 提交前自检

- 新翻译是否走了 lang 声明类，而不是手改 `src/generated`？
- 新 Mixin 是否放进了对应 modid 的包，并登记进 `<modid>.mixins.json`（客户端的放 `client` 段）？
- 新增的跨 mod 引用是否只出现在 `compat/` 或 `mixin/<modid>/` 里？
- 暴露给 JS 的新方法是否带 `@Info`？
- 数值是否可以由 DataComponent 或事件覆盖，而不是硬编码？
