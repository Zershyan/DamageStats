# KubeJS 集成

整合包作者要改的一切都通过 KubeJS 暴露。三个组成部分：**Plugin**（注册入口）、**Events 接口**（事件常量表）、**KubeEvent 镜像类**（把 Java 事件转成 JS 事件）。

声明文件：`src/main/resources/kubejs.plugins.txt`，一行一个 plugin 全限定名。

## Plugin

```java
public class EXKubePlugin implements KubeJSPlugin {
    @Override
    public void registerBindings(BindingRegistry bindings) {
        bindings.add("EX$Api", EXApi.class);
    }

    @Override
    public void registerEvents(EventGroupRegistry registry) {
        registry.register(EXKubeEvents.EXEvents);
    }

    @Override
    public void registerBuilderTypes(BuilderTypeRegistry registry) {
        registry.of(Registries.ITEM, itemCallback -> itemCallback.add(
                ExampleMod.id("example_weapon"),
                ExampleWeaponItemBuilder.class,
                ExampleWeaponItemBuilder::new
        ));
    }
}
```

绑定名用 `EX$Api` 这种带 `$` 的形式，避免和整合包里其他全局名冲突。

## 事件常量表

用 `interface`（字段自动 `public static final`）：

```java
public interface EXKubeEvents {
    EventGroup EXEvents = EventGroup.of("EXEvents");

    TargetedEventHandler<String> ProjectileShoot = EXEvents.server("projectileShoot", () -> ProjectileShootKubeEvent.class).supportsTarget(EventTargetType.STRING);
    TargetedEventHandler<String> ResourceCost = EXEvents.server("resourceCost", () -> ResourceCostKubeEvent.class).supportsTarget(EventTargetType.STRING);
    TargetedEventHandler<String> ModifyEnchantment$level = EXEvents.server("modifyEnchantment$level", () -> ModifyEnchantmentKubeEvent.Level.class).supportsTarget(EventTargetType.STRING);
    TargetedEventHandler<String> ModifyEnchantment$silkTouch = EXEvents.server("modifyEnchantment$silkTouch", () -> ModifyEnchantmentKubeEvent.SilkTouch.class).supportsTarget(EventTargetType.STRING);
    TargetedEventHandler<String> ResourceOverlay = EXEvents.client("resourceOverlay", () -> ResourceBarRegisterKubeEvent.class).supportsTarget(EventTargetType.STRING);
}
```

约定：

- 事件组名 = `<EX>Events`（JS 端写 `EXEvents.projectileShoot(event => {...})`）。
- 常量名 PascalCase，JS 事件名 camelCase。
- **嵌套事件类用 `$` 拼名**：Java 常量 `ModifyEnchantment$silkTouch`、JS 名 `modifyEnchantment$silkTouch` 对应 `ModifyEnchantmentKubeEvent.SilkTouch`。
- 客户端事件 `EXEvents.client(...)`，服务端/双端 `EXEvents.server(...)`。
- 类引用一律 `() -> Xxx.class` 的 supplier 形式（延迟加载，避免类过早链接）。
- 统一 `.supportsTarget(EventTargetType.STRING)`，让 JS 能按字符串过滤（如物品 id）。

## KubeEvent 镜像类（最核心的模式）

镜像类**继承自己的 API 事件**并实现 `KubeEvent`，靠一个静态监听器完成「Java 事件 → JS 事件 → 写回 Java 事件」的往返：

```java
@EventBusSubscriber(modid = ExampleMod.MODID)
public class ProjectileShootKubeEvent extends ProjectileShootEvent implements KubeEvent {

    @HideFromJS                                     // JS 不该看到构造过程
    public ProjectileShootKubeEvent(ProjectileShootEvent event) {
        super(                                      // 从原事件拷出所有状态
                event.getShooter(),
                event.getProjectile(),
                event.getIndex(),
                event.getVelocity(),
                event.getInaccuracy(),
                event.getAngle(),
                event.getTarget()
        );
    }

    @SubscribeEvent
    @HideFromJS
    public static void projectileShoot(ProjectileShootEvent event) {
        ProjectileShootKubeEvent kubeEvent = new ProjectileShootKubeEvent(event);
        EXKubeEvents.ProjectileShoot.post(kubeEvent);           // 交给 JS
        if(kubeEvent.isCanceled()) event.setCanceled(true);     // 逐项写回
        event.setAngle(kubeEvent.getAngle());
        event.setInaccuracy(kubeEvent.getInaccuracy());
        event.setProjectile(kubeEvent.getProjectile());
        event.setVelocity(kubeEvent.getVelocity());
    }
}
```

四条铁律：

1. **`extends 原事件 implements KubeEvent`** —— 复用 getter/setter，JS 端拿到的 API 和 Java 端完全一致，不用写第二套。
2. **构造器 + `@SubscribeEvent` 静态方法都要 `@HideFromJS`**，否则 JS 里会出现无意义的成员。
3. **post 之后必须逐项写回**。漏写一项，JS 改了也没效果，而且不报错——这是这个模式最容易出的 bug，改事件字段时一定要同步这里。
4. `@EventBusSubscriber` 放在**镜像类**上；嵌套子类的话每个子类各放一个。

### 嵌套事件的镜像

外层类只是命名空间，不加注解：

```java
public class ModifyEnchantmentKubeEvent {

    @EventBusSubscriber(modid = ExampleMod.MODID)
    public static class Level extends ModifyEnchantmentLevelEvent implements KubeEvent {
        @HideFromJS
        public Level(ModifyEnchantmentLevelEvent event) {
            super(event.getLiving(), event.getTool(), event.getEnchantment(), event.getLevel());
        }

        @SubscribeEvent
        @HideFromJS
        public static void level(ModifyEnchantmentLevelEvent event) {
            Level kubeEvent = new Level(event);
            EXKubeEvents.ModifyEnchantment$level.post(kubeEvent);
            event.setLevel(kubeEvent.getLevel());
        }
    }

    @EventBusSubscriber(modid = ExampleMod.MODID)
    public static class SilkTouch extends ApplySilkTouchEvent implements KubeEvent {
        @HideFromJS
        public SilkTouch(ApplySilkTouchEvent event) { super(event.getLiving(), event.getTool()); }

        @SubscribeEvent
        @HideFromJS
        public static void silkTouch(ApplySilkTouchEvent event) {
            SilkTouch kubeEvent = new SilkTouch(event);
            EXKubeEvents.ModifyEnchantment$silkTouch.post(kubeEvent);
            event.setApplied(kubeEvent.isApplied());
        }
    }
}
```

### 只收集、不回写的镜像（注册型事件）

有些事件是「让 JS 往里注册东西」，写回方式是遍历 JS 那侧收集到的列表：

```java
public class BarRegisterKubeEvent extends BarRegisterEvent implements ClientKubeEvent {
    @SubscribeEvent
    @HideFromJS
    public static void load(BarRegisterEvent event) {
        BarRegisterKubeEvent kubeEvent = new BarRegisterKubeEvent();
        EXKubeEvents.ResourceOverlay.post(kubeEvent);
        for (BarData data : kubeEvent.getAtFirst())   event.registerAtFirst(data.position(), data.overlay());
        for (BarData data : kubeEvent.getRecommends()) event.registerOverlay(data.position(), data.overlay());
        for (BarData data : kubeEvent.getAtLast())    event.registerAtLast(data.position(), data.overlay());
    }
}
```

事件类内部用 `List<XxxData>` 收集 + `getXxx()` 返回 `List.copyOf(...)`，`XxxData` 用 record 承载「位置 + 内容」。客户端事件实现 `ClientKubeEvent` 而不是 `KubeEvent`。

### 需要按 mod 加载条件注册的镜像

compat 子包里的镜像事件**不加 `@EventBusSubscriber`**，改由对应 Compat 手动注册：

```java
@Override
public void addClientListener(IEventBus forgeBus, IEventBus modBus) {
    safelyRun(() -> {
        forgeBus.register(BarRegisterKubeEvent.class);
        forgeBus.post(new BarRegisterEvent());
    });
}
```

## @Info 规则

`@Info`（`dev.latvian.mods.kubejs.typings.Info`）是给 ProbeJS 生成 `.d.ts` 的说明来源，等于「JS 侧的文档」。

- **凡是 JS 能看到的方法/字段都要写**：Api 门面全部方法、Helper 全部公开方法、DataComponent 全部字段、Builder 全部方法。
- 内容说清「这是什么」和「边界情况」，包含反直觉的地方：

  ```java
  @Info("获取实体攻击速度 modifier 的修正倍率，不含实体自身 base；传入 null 返回 1.0f")
  @Info("crit 是暴击标记，即使暴击也不会加伤害（已被 mixin 去掉），只有粒子效果")
  @Info("不要试图修改这个对象，你应该使用 DataComponent")
  @Info("拿到的不是当前实例而是复制体，防止被外部修改")
  ```

- 语义写在 `@Info` 里就不要再写 Javadoc，两处会不一致。
- 不希望 JS 看到的用 `@HideFromJS`（`dev.latvian.mods.rhino.util.HideFromJS`）。

## ItemBuilder 扩展

给 KubeJS 加自定义物品类型：继承 `ItemBuilder`，用**函数式接口**接收 JS 的配置 lambda：

```java
@FunctionalInterface
public interface WeaponComponentBuilder {
    WeaponComponent apply(WeaponComponent component);
}
```

```java
public class ExampleWeaponItemBuilder extends ItemBuilder {
    private WeaponComponent component = new WeaponComponent();

    public ExampleWeaponItemBuilder(ResourceLocation id) { super(id); }

    public ExampleWeaponItemBuilder modifyWeapon(WeaponComponentBuilder modifier) {
        this.component = modifier.apply(this.component);
        return this;
    }

    // 便利方法：套用原版弓的拉弓模型，JS 端一行搞定
    public ExampleWeaponItemBuilder copyBowItemModel(ResourceLocation id) {
        return copyBowItemModel(id, "item/", "_pulling_");
    }

    public ExampleWeaponItemBuilder copyBowItemModel(ResourceLocation id, String prefix, String suffix) {
        ResourceLocation location = id.withPrefix(prefix);
        modelGenerator(gen -> {
            gen.parent(location);
            ResourceLocation withedSuffix = location.withSuffix(suffix);
            gen.override(withedSuffix.withSuffix("0"), override ->
                    override.predicate(ResourceLocation.withDefaultNamespace("pulling"), 1));
            gen.override(withedSuffix.withSuffix("1"), override -> {
                override.predicate(ResourceLocation.withDefaultNamespace("pulling"), 1);
                override.predicate(ResourceLocation.withDefaultNamespace("pull"), 0.65f);
            });
            ...
        });
        return this;
    }

    @Override
    public ExampleWeaponItem createObject() {
        return new ExampleWeaponItem(createItemProperties(), component);
    }
}
```

要点：**别让 JS 端逐个手写模型 override**，把整套样板封成 `copyXxxModel(id)` 便利方法，并留一个带 prefix/suffix 参数的完整版重载。

## 让 JS 重写 Java 类行为：callSuper 开关模式

要把一个 Java 类（尤其是别人 mod 的抽象类）整体开放给 JS 重写时，用「**每个方法一个函数式接口 + 一个 `callSuper` 布尔**」的模式。

三段结构：

```java
@SuppressWarnings({"deprecation", "unused"})
public class OverridableBar extends SomeAbstractBar {
    // 1. 每个可重写方法一个函数式接口（参数签名与目标方法一致）
    @FunctionalInterface
    public interface DrawInter {
        void accept(GuiGraphics guiGraphics, int left, int top, int right, int bottom, Parameters parameters, boolean flip);
    }

    // 2. 可空字段 + 同名 CallSuper 布尔
    @Nullable private DrawInter draw;
    private boolean drawCallSuper;

    // 3a. 链式配置方法，带 @Info 说明第二个参数含义
    @Info("第二个参数为 true 时会在你的代码前先调用 super")
    public OverridableBar draw(@Nullable DrawInter draw, boolean callSuper) {
        this.draw = draw;
        this.drawCallSuper = callSuper;
        return this;
    }

    // 3b. override：三态语义
    @Override
    public void draw(GuiGraphics guiGraphics, int left, int top, int right, int bottom, Parameters parameters, boolean flip) {
        if (drawCallSuper || draw == null) super.draw(guiGraphics, left, top, right, bottom, parameters, flip);
        if (draw != null) draw.accept(guiGraphics, left, top, right, bottom, parameters, flip);
    }
}
```

三态语义（这是模式的关键）：

| JS 传入 | 行为 |
|---|---|
| 不设置（`null`） | 只跑 `super` —— 保持原行为 |
| 设置 + `callSuper = false` | 只跑 JS —— **替换**原行为 |
| 设置 + `callSuper = true` | 先 `super` 再跑 JS —— **追加**行为 |

返回值型方法更简单，可空字段直接三元：

```java
@Override
public boolean showFadeEffect() {
    return showFadeEffect != null ? showFadeEffect : super.showFadeEffect();
}
```

配套约定：

- 构造器私有，用静态工厂暴露，把「必填项」放工厂参数里，并顺手替 JS 把要填的对象 new 好：

  ```java
  @FunctionalInterface
  public interface NewBarFunction {
      Parameters getParameters(Player player, Parameters parameters);
  }

  public static OverridableBar bar(NewBarFunction getParameters, Function<Player, Boolean> shouldRender) {
      return new OverridableBar(player -> getParameters.getParameters(player, new Parameters()), shouldRender);
  }
  ```

- 常见配置提供预设方法（如 `drawStringDefault()`），JS 端不用重复写。
- 类上 `@SuppressWarnings({"deprecation", "unused"})` —— 这类给 JS 用的方法在 Java 侧确实「未使用」。

这个模式很啰嗦，但换来的是 JS 端能只重写关心的方法。**适用条件：目标类方法多、JS 需求分散。** 只需要开放一两个钩子时不要上这套，直接传 lambda 就好。

## 新增 KubeJS 能力检查清单

**加事件**：
1. 先有 `api/event/XxxEvent`（见 `event-api.md`）。
2. 建 `kubejs/event/XxxKubeEvent`（第三方 mod 相关的放 `compat/<modid>/api/kubeEvent/`）。
3. `extends 原事件 implements KubeEvent`（客户端用 `ClientKubeEvent`）。
4. 构造器 + 静态监听方法都加 `@HideFromJS`。
5. post 之后**逐项写回**所有可写字段。
6. 事件常量表加一项，嵌套类用 `$` 拼名。
7. 需要条件注册的挪到 Compat 里手动 `forgeBus.register(...)`。

**加 API 方法**：
1. 加到 Api 门面或对应 Helper 上。
2. 写 `@Info` 说明，包含 null / 边界行为。
3. 跨 mod 的走 `CompatFactory`。

**加物品类型**：
1. 建 `XxxItemBuilder extends ItemBuilder`，`createObject()` 返回物品实例。
2. 配置入口用 `@FunctionalInterface`。
3. 样板（模型 override 之类）封成便利方法。
4. `registerBuilderTypes` 里注册，id 用 `ExampleMod.id("...")`。



