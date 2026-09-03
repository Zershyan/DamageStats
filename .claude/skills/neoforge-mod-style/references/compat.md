# Compat：软兼容层

## 为什么必须有这一层

Java 在**首次执行到引用了某个类的代码**时才链接该类。如果一个方法体里出现了 `com.othermod.Foo`，只要这个方法不被调用，缺少 othermod 也不会炸；但只要方法被调用一次，就是 `NoClassDefFoundError`。

所以规则是：**跨 mod 的类只能出现在 compat 层（或 `mixin/<modid>/` 包内），并且调用点必须被 `isModLoaded()` 守卫。** 业务代码只看得到 `CompatFactory.OtherMod.doSomething(...)` 这种形态。

## 三件套

### ICompatUtils：能力接口（全默认方法）

```java
public interface ICompatUtils {
    boolean isModLoaded();                                  // 唯一抽象方法

    default boolean testLoadedAndRun(Runnable runnable) {
        if(isModLoaded()) runnable.run();
        else return false;
        return true;
    }

    default <T> T testLoadedAndCall(Callable<T> callable, T errorResult) {
        try {
            if(isModLoaded()) return callable.call();
        } catch (Exception ignored) {}
        return errorResult;
    }

    default <T> T testLoadedAndCall(Callable<T> callable, Callable<T> elseCall, T errorResult) {
        try {
            if(isModLoaded()) return callable.call();
            else return elseCall.call();
        } catch(Exception e) { return errorResult; }
    }

    default void safelyRun(Runnable runnable) {
        try { runnable.run(); }
        catch (Exception ignored) {}
    }

    default void addCommonListener(IEventBus forgeBus, IEventBus modBus){}
    default void addClientListener(IEventBus forgeBus, IEventBus modBus){}

    default void addListener(IEventBus forgeBus, IEventBus modBus) {
        addCommonListener(forgeBus, modBus);
        if(FMLLoader.getDist() == Dist.CLIENT){
            addClientListener(forgeBus, modBus);
        }
    }

    default void init(IEventBus forgeBus, IEventBus modBus) { addListener(forgeBus, modBus); }

    default void initial(IEventBus forgeBus, IEventBus modBus) {
        try { testLoadedAndRun(() -> init(forgeBus, modBus)); }
        catch (Exception e) { LogUtils.getLogger().error(e.getMessage()); }
    }
}
```

四个工具方法的选择标准：

| 场景 | 用 | 失败行为 |
|---|---|---|
| 无返回值的跨 mod 操作 | `testLoadedAndRun` | mod 未装则返回 false，异常向上抛 |
| 有返回值，未装时给兜底值 | `testLoadedAndCall(callable, fallback)` | 静默吞异常，返回 fallback |
| 有返回值，未装时走另一套实现 | `testLoadedAndCall(callable, elseCall, fallback)` | 静默吞异常 |
| 注册监听等「失败也不影响主流程」的动作 | `safelyRun` | 静默吞异常 |

静默吞异常是**这一层刻意的选择**：兼容失败应该表现为「该功能没了」，而不是「游戏崩了」。除了这一层，别处不要写空 catch。

### CompatFactory：单例注册表 + 访问点

```java
public class CompatFactory {
    private static final Set<ICompatUtils> compatUtils = new HashSet<>();

    public static final OtherModCompat OtherMod = addCompat(new OtherModCompat());
    public static final HudModCompat HudMod = addCompat(new HudModCompat());

    private static <T extends ICompatUtils> T addCompat(T utils) {
        if (compatUtils.add(utils)) return utils;
        else throw new IllegalStateException("CompatUtils already added.");
    }

    public static void register(IEventBus forgeEventBus, IEventBus modEventBus) {
        compatUtils.forEach(compatUtils -> compatUtils.initial(forgeEventBus, modEventBus));
    }
}
```

字段本身就是访问点，**PascalCase 命名**（`CompatFactory.OtherMod` 读起来像命名空间）。`addCompat` 在静态初始化时顺带登记进集合，重复添加直接抛异常。

### XxxCompat：单个 mod 的适配实现

```java
public class OtherModCompat implements ICompatUtils {
    public static final String MODID = "othermod";
    public static final String SUB_MODID = "othermod_addon";
    public static final String HUD_MODID = "hudmod";

    @Override
    public boolean isModLoaded() {
        return ModList.get().isLoaded(MODID) && ModList.get().isLoaded(SUB_MODID);
    }

    public boolean isHudModLoaded() {
        return ModList.get().isLoaded(HUD_MODID);
    }

    // 有返回值：未装或参数为 null 都返回 null
    @Nullable
    public NpcHelper npcHelper(@Nullable LivingEntity entity) {
        return testLoadedAndCall(() -> NpcHelper.of(entity), null);
    }

    // 无返回值：整段跨 mod 逻辑塞进 lambda
    public void playFullChargeSound(Level level, LivingEntity entity) {
        testLoadedAndRun(() -> {
            if (level.isClientSide) {
                entity.playSound(OtherModSounds.CHARGE_READY.get());
            }
        });
    }

    @Override
    public void addCommonListener(IEventBus forgeBus, IEventBus modBus) {
        safelyRun(() -> {
            forgeBus.register(SomeKubeEvent.class);
        });
    }

    @Override
    public void addClientListener(IEventBus forgeBus, IEventBus modBus) {
        safelyRun(() -> {
            if(isHudModLoaded()) {
                forgeBus.register(OverlayRegisterHandler.class);
            }
        });
    }
}
```

约定：

- **每个 Compat 都有 `public static final String MODID`**，`isModLoaded()` 只用这些常量。需要多个 mod 同时在场时（本体 + 子模块）就 `&&`。
- 「可选的可选项」（装了 A 才有意义，但也依赖 B）另开 `isXxxLoaded()`，在监听注册时判断。
- **注册监听不用 `@EventBusSubscriber`**，因为要按 mod 是否加载条件注册，只能在 `addCommonListener` / `addClientListener` 里手动 `forgeBus.register(Xxx.class)`。
- 客户端/双端由 `addClientListener` / `addCommonListener` 区分，不要自己判 `Dist`——基类 `addListener` 已经做了。
- **一个方法只做一件事**，方法体第一层就是 `testLoadedAndRun` / `testLoadedAndCall`。

## 调用方写法

业务代码里就是一句普通调用，看不出兼容的存在：

```java
// 装了 othermod 就播它的音效，没装就什么也不发生
if(0 <= using && using < 1 && level.isClientSide) {
    CompatFactory.OtherMod.playFullChargeSound(level, livingEntity);
}
```

```java
// 暴露给 KubeJS 的门面同样只经过 CompatFactory
@Nullable
@Info("获取 othermod 的 NPC Helper；othermod 没安装或实体不是 NPC 时返回 null")
public static NpcHelper npc(@Nullable LivingEntity npc) {
    return CompatFactory.OtherMod.npcHelper(npc);
}
```

**不要**在业务代码里写 `if (ModList.get().isLoaded("othermod"))`。判断只属于 compat 层。

## compat 子包结构

一个 mod 的适配内容按用途分：

```
compat/othermod/
├── OtherModCompat              入口
├── api/
│   ├── NpcHelper               对该 mod 对象的门面（包私有构造 + 静态工厂）
│   ├── event/                  基于该 mod 触发的自定义事件
│   └── kubeEvent/              上面这些事件的 KubeJS 镜像
├── handler/                    监听器
└── registry/                   需要向该 mod 注册的东西
```

对应的 Mixin 放在 `mixin/<modid>/`，不放 compat 包里——两者分工是：**Mixin 负责在别人的代码里开洞并 post 自定义事件，compat 负责调用别人的 API**。

### Helper 门面：包装别人的对象

```java
public class NpcHelper {
    private final OtherModNpc npc;

    NpcHelper(OtherModNpc npc) { this.npc = npc; }        // 包私有

    @Nullable
    public static NpcHelper of(@Nullable LivingEntity entity) {
        if(entity instanceof OtherModNpc npc) return new NpcHelper(npc);
        else return null;
    }

    public void addTrade(ITrade trade) {
        if(npc.level().isClientSide()) return;             // 每个方法自己守卫端
        manager().ex$addTrade(trade);
    }

    private IMixinTradeManager manager() {                 // 桥接接口收在私有方法里
        return IMixinTradeManager.of(npc.getTradeManager());
    }
}
```

要点：构造器包私有、静态工厂返回 `@Nullable`、每个公开方法自己判 `isClientSide` 提前返回、`IMixinXxx` 的强转收在一个私有方法里不外泄。

## gradle 依赖如何选

| 兼容性质 | 依赖方式 |
|---|---|
| 纯软兼容（装了才生效） | `compileOnly` —— **默认选这个** |
| 开发期需要实际跑起来联调 | `implementation` |
| 需要打进成品 jar 的库 | `implementation(jarJar(...))` |
| 注解处理器 | `compileOnly(annotationProcessor(...))` |

每条依赖上方写注释标 mod 名。

如果是**硬依赖**（没它就不能工作），还要在 `src/main/templates/META-INF/neoforge.mods.toml` 里加 `[[dependencies]]`。软兼容 mod **不要**写进 mods.toml。

## 新增一个 mod 兼容的完整步骤

1. `build.gradle` 加依赖，优先 `compileOnly`，上方写注释标 mod 名。
2. 新建 `compat/<modid>/XxxCompat implements ICompatUtils`：
   - `public static final String MODID = "<modid>"`
   - `isModLoaded()` 返回 `ModList.get().isLoaded(MODID)`
3. `CompatFactory` 里加 `public static final XxxCompat Xxx = addCompat(new XxxCompat());`
4. 需要调用对方 API 的能力，一个方法一件事，方法体用 `testLoadedAndRun` / `testLoadedAndCall` 包住。
5. 需要监听事件的，重写 `addCommonListener` / `addClientListener`，`safelyRun` 里 `forgeBus.register(...)`。
6. 需要改对方行为的，去 `mixin/<modid>/` 建 Mixin（包名放对就自动带加载条件），并登记进 `<modid>.mixins.json`。
7. 需要暴露给 KubeJS 的，在 `compat/<modid>/api/kubeEvent/` 建镜像事件，并在事件常量表里加一项。
8. 验证：**在不装该 mod 的环境下启动一次**，确认没有 `NoClassDefFoundError`。这是这一层唯一有意义的测试。



