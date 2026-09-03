# 包结构、命名与格式

## 包结构与职责

```
com.example.examplemod
├── ExampleMod                   @Mod 入口，MODID 常量 + id(String) 工具
├── api/                         对外能力层（其他 mod / KubeJS 可用）
│   ├── EXApi                    唯一静态门面，绑定给 JS
│   ├── event/                   自定义 NeoForge 事件
│   └── helper/                  语义化门面：XxxHelper + XxxHelperBuilder
├── compat/                      跨 mod 适配层，全部跨 mod 调用的唯一出口
│   ├── ICompatUtils              软兼容工具接口（默认方法）
│   ├── CompatFactory             兼容单例注册表
│   └── <modid>/                  每个被兼容的 mod 一个子包
│       ├── OtherModCompat        实现 ICompatUtils
│       ├── api/event/            该 mod 相关的自定义事件
│       ├── api/kubeEvent/        对应的 KubeJS 镜像事件
│       ├── api/XxxHelper         该 mod 对象的门面
│       ├── handler/              该 mod 相关的事件监听
│       └── registry/             需要向该 mod 注册的东西
├── datagen/                     数据生成
│   ├── DataGeneratorHandler     GatherDataEvent 汇总
│   ├── init/                    EXLang / EXKeyLang / EXRegistryLang
│   └── provider/                各 Provider
├── handler/                     普通事件监听
│   ├── client/                  仅客户端
│   └── common/                  双端
├── kubejs/                      KubeJS 集成
│   ├── EXKubePlugin             KubeJSPlugin 实现
│   ├── EXKubeEvents             EventGroup + 事件常量
│   ├── event/                   KubeEvent 镜像类
│   └── builder/                 ItemBuilder 等扩展
├── mixin/                       Mixin，按目标 modid 分包
│   ├── EXMixinPlugin            按包名路由的 IMixinConfigPlugin
│   └── <modid>/MixinXxx
├── registry/                    注册表
│   ├── EXItems / EXAttributes / EXDataComponents / EXPackets
│   ├── component/               DataComponent 数据类
│   ├── item/                    物品实现
│   └── packet/                  网络包 record
└── util/                        通用工具
    ├── LazyComponent / PlayerUtil
    └── mixin/                   IMixinXxx 桥接接口
```

核心约束：**`api/`、`registry/`、`handler/`、`util/` 里的代码不得直接 import 第三方 mod 的类**。需要的话通过 `CompatFactory` 拿。唯一容许的例外是「返回类型必须是对方相关的门面类」，且该门面类本身住在 compat 子包内。

## 类命名

| 类型 | 规则 | 例子 |
|---|---|---|
| Mod 入口 | `<驼峰全名>Mod` | `ExampleMod` |
| 注册表 | `EX<复数名>` | `EXItems` `EXAttributes` `EXPackets` |
| datagen 声明 | `EX<用途>Lang` | `EXKeyLang` `EXRegistryLang` |
| datagen provider | `EX<用途>Provider` | `EXLangProvider` `EXItemModelProvider` |
| 兼容实现 | `<Mod名>Compat` | `OtherModCompat` |
| 事件监听 | `<功能>Handler` | `AttachDamageHandler` `ArmorDurabilityHandler` |
| 自定义事件 | `<动作>Event` | `ProjectileShootEvent` `ResourceCostEvent` |
| KubeJS 镜像事件 | `<事件名>KubeEvent` | `ProjectileShootKubeEvent` |
| 门面 | `<对象>Helper` / `<对象>HelperBuilder` | `DamageSourceHelper` |
| DataComponent 数据 | `<物件>Component` | `WeaponComponent` |
| 网络包 | `<内容>Packet` | `ArrowVelocityPacket` |
| Mixin | `Mixin<目标类简名>` | `MixinLivingEntity` `MixinAbstractArrow` |
| 二次 Mixin（MixinSquared） | `MixinMixin<目标类简名>` | `MixinMixinFoodData` |
| Mixin 桥接接口 | `IMixin<目标类简名>` | `IMixinAbstractArrow` |

界线只有一条：**基础设施类加前缀，业务类不加**。目标类本身就是别人的 Mixin 类时，保留对方名字（`MixinItemRendererMixin`）。

## 成员命名

两套约定并存，按「这个常量是配置还是值」区分。

**UPPER_SNAKE_CASE** —— 注册项、协议常量、真常量、静态资源：

```java
public static final DeferredRegister.Items BASE = ...;
public static final DeferredItem<ExampleWeaponItem> EXAMPLE_WEAPON = ...;
public static final Codec<WeaponComponent> CODEC = ...;
public static final Type<ArrowVelocityPacket> TYPE = ...;
public static final String TAG_TARGET = "target";
public static final double VANILLA_VELOCITY_LIMIT = 3.9;
private static final ExecutorService EXECUTORS = Executors.newFixedThreadPool(2);
```

**PascalCase** —— 「当值用」的静态常量：翻译 Component、事件句柄、兼容单例、配置项。它们在调用点读起来像普通对象引用，而且会被反复阅读，所以刻意不写成全大写：

```java
public static final MutableComponent WeaponTooltip = entry(...);
public static final LazyComponent WeaponBaseDamage = entryLazy(...);
public static final OtherModCompat OtherMod = addCompat(new OtherModCompat());
public static final ModConfigSpec.IntValue SomeOption = BUILDER...;
EventGroup EXEvents = EventGroup.of("EXEvents");
TargetedEventHandler<String> ProjectileShoot = EXEvents.server(...);
```

配置项归这一类，是因为业务代码里到处在读它们（`EXConfig.SomeOption.get()`），全大写加下划线会打断阅读节奏。同一个配置类里的 `SPEC` / `BUILDER` 只在注册时用一次，仍然全大写。

嵌套事件在 KubeJS 事件名里用 `$` 表示层级：`modifyEnchantment$silkTouch`、`attachDamage$early`。

Mixin 内新增成员统一 `ex$` 前缀：`ex$lastInvulnerableTime`、`ex$setExactVelocity`。写进 `CustomData` 的临时 key 同样加前缀：`ex$charge`、`ex$multiArrow`。

## 格式细节

- **单行 if 不带花括号**，条件括号前不空格，是主流写法：

  ```java
  if(component.getKnockback() != 0) knockbackRef.set(component.getKnockback());
  if(location == null) return modifiers;
  if(helper.isIgnoreInvulnerableTime()) {
      ex$lastInvulnerableTime = invulnerableTime;
      invulnerableTime = 0;
  } else ex$lastInvulnerableTime = -1;
  ```

- **早返回优先**，深层嵌套用「反向 else return」压平：

  ```java
  if (0 <= using && using < 1 && level.isClientSide) {
      playFullChargeSound(level, livingEntity);
  } else if (usedTick < prepareTick) return;
  ```

- **Optional 链式**取代 null 判断堆叠：

  ```java
  ModList.get().getModContainerById(MODID)
          .map(ModContainer::getModInfo)
          .map(IModInfo::getVersion)
          .map(Object::toString)
          .orElse("unknown");
  ```

- **registry 查询**统一 `getOptional(...).orElse(null)` 再配 `instanceof` 绑定，不用 `containsKey` + `get`。
- **类型分派**用 Java 21 switch 模式匹配，`default` 分支记日志再降级处理，不抛异常。
- **cast 到 Mixin 目标类**用 `Target.class.cast(this)`，不写 `(Target)(Object)this`。

## 构建与依赖约定

`gradle.properties` 承载所有版本号与 mod 元信息，`src/main/templates/META-INF/neoforge.mods.toml` 用 `${...}` 占位，由 `generateModMetadata` 任务展开。改 mod 名/版本只动 `gradle.properties`。

```gradle
sourceSets.main.resources {
    srcDir('src/generated/resources')
    exclude("**/*.bbmodel")            // 建模工程文件不打包
    exclude("src/generated/**/.cache")  // datagen 缓存不打包
}
```

依赖按「运行时是否必须」分档，**每条依赖上方写注释标明 mod 名**：

```gradle
// 某内容 mod（开发期需要实际跑）
implementation "curse.maven:othermod-123456:7890123"

// 某属性 mod（纯软兼容）
compileOnly "curse.maven:attributemod-234567:8901234"

// Mixin^2
compileOnly(annotationProcessor("com.github.bawnorton.mixinsquared:mixinsquared-common:0.3.7-beta.1"))
implementation(jarJar("com.github.bawnorton.mixinsquared:mixinsquared-neoforge:0.3.7-beta.1"))
```

- `compileOnly` —— 软兼容，只为编译期类型，运行时不存在也不影响启动。**绝大多数兼容 mod 都应该是这一档**。
- `implementation` —— 开发环境需要实际跑起来联调的。
- `jarJar` —— 需要打进成品 jar 的库。
- `compileOnly(annotationProcessor(...))` —— 注解处理器。

run 配置建议开两个客户端，便于测联机同步：

```gradle
runs {
    client1 { client(); gameDirectory = project.file('./run/client1'); programArguments.addAll("--username", "player1") }
    client2 { client(); gameDirectory = project.file('./run/client2'); programArguments.addAll("--username", "player2") }
    server  { server(); programArgument '--nogui' }
    data {
        data()
        programArguments.addAll '--mod', project.mod_id, '--all',
                '--output', file('src/generated/resources/').getAbsolutePath(),
                '--existing', file('src/main/resources/').getAbsolutePath()
    }
}
```

`src/generated/resources` 纳入版本控制，datagen 产物随代码一起提交。

## 反模式

这几条是长期维护中最容易漏掉的，写完对照一遍：

| 反模式 | 应该 |
|---|---|
| 前缀出现第二种写法（`EX` 之外又冒出 `EXM`、`ex$$`） | 全项目一种前缀，加成员时复制现有的看一眼 |
| 监听器类命名成 `XxxEvent` | 监听器是 `XxxHandler`，只有事件定义才叫 `XxxEvent` |
| 给实现接口的 `@Override` 方法标 `@Unique` | `@Unique` 只给新增的私有字段/私有方法 |
| 写 `clone()` 却没 `implements Cloneable` | `super.clone()` 必抛异常。要么实现接口，要么直接写 `copy()` 手动复制 |
| 抽象基类的多个子类里复制粘贴同一段方法 | 公共方法提到抽象父类，子类只留差异部分 |
| 混用 `javax.annotation.Nullable` 和 jetbrains 的 | 统一 jetbrains |
| 事件加了字段，但 Mixin / 镜像事件里忘记写回 | 改事件字段时同步检查所有 post 点的写回列表 |
| 业务代码里写 `ModList.get().isLoaded("othermod")` | 判断只属于 compat 层 |
| 定义了没人调用的方法留着「以后可能用」 | 直接删。不留兼容壳、不留 `_unused` 重命名 |
| 给纯静态的工具类 / Handler 补一个空的 private 构造器「防止实例化」 | `final class` 就够了，空构造器是纯噪音 |
| 为实现函数式接口专门造一个 `INSTANCE` 单例 | 直接传静态方法引用（`XxxOverlay::render`），不需要实例就不要造 |
| tab 缩进 / 行尾空格 | 4 空格 |

## 开发期数据导出

盘点内容、校对数值平衡时，把一次性统计脚本写成 Java 类是个划算的做法：

```java
// 仅客户端手动注册，不用 @EventBusSubscriber —— 它不是正式功能
if (FMLEnvironment.dist.isClient()) {
    neoEventBus.register(DebugDumpEvent.class);
}
```

```java
@SubscribeEvent
public static void dumpAll(PlayerEvent.PlayerLoggedInEvent event) {
    try {
        Path file = Paths.get("all_items.txt");
        if (!Files.exists(file)) Files.createFile(file);
        List<String> result = new ArrayList<>();
        List<Item> list = new ArrayList<>(BuiltInRegistries.ITEM.stream().toList());
        list.sort(Comparator.comparing(item -> item.getDescription().getString()));
        list.forEach(item -> { /* 单条失败各自 ignored */ });
        Files.write(file, result, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
    } catch (Exception ignored) {
    }
}
```

约定：用 `PlayerLoggedInEvent` 当触发点、输出 txt 到游戏运行目录、整体和单条都 `catch (Exception ignored)`——盘点脚本要的是尽量多的结果而不是正确性。取不到公开 API 的数值时允许沿继承链反射兜底，**这是唯一容许反射兜底的场景**。



