# 注册表模板

## 通用骨架

每个注册表一个类，三段结构固定：**private static final DeferredRegister** → **public static final 注册项** → **public static void register(IEventBus)**。

```java
public class EXAttributes {
    private static final DeferredRegister<Attribute> REGISTER =
            DeferredRegister.create(Registries.ATTRIBUTE, ExampleMod.MODID);

    public static final Holder<Attribute> AERIAL_JUMP = REGISTER.register("aerial_jump", location ->
            new RangedAttribute(location.toLanguageKey("attribute", "desc"), 0, 0, Integer.MAX_VALUE));

    public static void register(IEventBus modEventBus) {
        REGISTER.register(modEventBus);
    }
}
```

要点：

- `DeferredRegister` 字段名固定 `REGISTER`（多组时用语义名，见下文），并且 **private**——外部只能拿注册项，不能往里塞东西。
- 注册用 `register(name, location -> ...)` 的 lambda 形态，从而能用 `location.toLanguageKey(...)` 自动派生翻译键，不手写字符串。
- `register(IEventBus)` 由 Mod 构造器调用，注册类本身不碰事件总线。

Mod 入口只做转发，不写业务：

```java
@Mod(ExampleMod.MODID)
public class ExampleMod {
    public static final String MODID = "examplemod";

    public static ResourceLocation id(String string) {
        return ResourceLocation.fromNamespaceAndPath(MODID, string);
    }

    public ExampleMod(IEventBus modEventBus, ModContainer modContainer) {
        IEventBus neoEventBus = NeoForge.EVENT_BUS;

        EXItems.register(modEventBus);
        EXAttributes.register(modEventBus);
        EXDataComponents.register(modEventBus);
        CompatFactory.register(neoEventBus, modEventBus);
    }
}
```

## 物品：按模型类型分组注册

物品注册类持有多个 `DeferredRegister.Items`，**分组依据是 datagen 要生成哪种物品模型**，而不是业务分类。这样模型 provider 可以整组套模板，不必逐个物品声明：

```java
public final class EXItems {
    public static final DeferredRegister.Items BASE = DeferredRegister.createItems(ExampleMod.MODID);
    public static final DeferredRegister.Items HANDHELD = DeferredRegister.createItems(ExampleMod.MODID);

    public static final DeferredItem<ExampleItem> EXAMPLE_ITEM = BASE.registerItem("example_item", ExampleItem::new);

    public static void register(IEventBus modEventBus) {
        BASE.register(modEventBus);
        HANDHELD.register(modEventBus);
    }
}
```

```java
// EXItemModelProvider
EXItems.BASE.getEntries().forEach(entry -> basicItem(entry.get()));
EXItems.HANDHELD.getEntries().forEach(entry -> handheldItem(entry.get()));
```

新增一组模型类型就加一个 `DeferredRegister.Items` + provider 里加一行。加物品本身不需要动 provider。

## 物品：默认配置用链式 builder 内联

带 DataComponent 的物品，默认值直接在注册处用链式 builder 写完，一行一个参数，读起来像配置表：

```java
public static final DeferredItem<ExampleWeaponItem> EXAMPLE_WEAPON = HANDHELD.registerItem("example_weapon",
        properties -> new ExampleWeaponItem(properties, component -> component
                .ammoSwitch(ResourceLocation.fromNamespaceAndPath("othermod", "special_arrow"))
                .infiniteSpecificalAmmo(true)
                .multiShootSpread(10)
                .minShootCharge(0.2f)
                .ammoBaseDamage(20f)
                .continuous(true)
                .prepareTick(40f)
                .baseDamage(20)
                .fullCharge(10)
                .velocity(3)
                .crit(true)
        )
);
```

配套的物品类提供三个构造器，覆盖三种来源：

```java
public ExampleWeaponItem(Properties properties)                                    // 全默认
public ExampleWeaponItem(Properties properties, WeaponComponent component)         // KubeJS builder 传入
public ExampleWeaponItem(Properties properties, UnaryOperator<WeaponComponent> b)  // 注册处链式配置
```

## DataComponent 数据类模板

数据类是**可变 + 链式**的（不是 record），因为要同时服务三种场景：注册处链式配置、KubeJS builder 修改、序列化进 ItemStack。

```java
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class WeaponComponent {
    public static final Codec<WeaponComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("multiShoot").forGetter(WeaponComponent::getMultiShoot),
            Codec.FLOAT.optionalFieldOf("prepareTick").forGetter(WeaponComponent::prepareTick),
            ResourceLocation.CODEC.optionalFieldOf("ammoSwitch").forGetter(WeaponComponent::ammoSwitch),
            Codec.BOOL.fieldOf("crit").forGetter(WeaponComponent::isCrit)
    ).apply(i, WeaponComponent::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, WeaponComponent> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public WeaponComponent() { }                     // 公开无参：默认值
    private WeaponComponent(int multiShoot, ...) { }  // 私有全参：只给 CODEC 用

    @Info("额外射出弹射物数")
    private int multiShoot = 0;

    @Nullable
    @Info("开始连射的前摇，为空时默认为拉满时间；连射为 false 时用不上")
    private Float prepareTick = null;
    ...
}
```

五条约定：

1. **默认值写在字段声明上**，无参构造器什么都不做。
2. **`@Info` 说明写在字段上**——这是字段语义的唯一文档来源，不再另写 Javadoc。
3. **getter 做防御性收敛**，调用方永远拿到合法值，不需要自己 clamp：

   ```java
   public float getFullCharge()  { return Mth.clamp(this.fullCharge, 1, 20); }
   public float getBaseDamage()  { return Math.max(0, baseDamage); }
   public int   getMultiShoot()  { return Math.max(0, multiShoot); }
   ```

4. **可空字段配两个访问器**：`getXxx()` 返回 `@Nullable`（给 Java 用），`xxx()` 返回 `Optional`（给 Codec 和 `ifPresent` 链用）。setter 是**无 `set` 前缀、返回 this** 的 `xxx(value)`。

5. **依赖实体状态的派生值另开 `getEntityXxx(LivingEntity)`**，把「基础值」和「实际值」分开，tooltip 里两者都显示：

   ```java
   public float getEntityFullCharge(@Nullable LivingEntity entity) {
       float fullCharge = Math.max(1, this.fullCharge);
       double modify = EXApi.getAtkSpeedModify(entity);
       return Math.max((float) (fullCharge / modify), 1);
   }
   ```

注册：

```java
public class EXDataComponents {
    private static final DeferredRegister.DataComponents REGISTER =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ExampleMod.MODID);

    public static final Supplier<DataComponentType<WeaponComponent>> WEAPON_COMPONENT = REGISTER.registerComponentType(
            "weapon_component", builder -> builder
                    .persistent(WeaponComponent.CODEC)
                    .networkSynchronized(WeaponComponent.STREAM_CODEC)
    );

    public static void register(IEventBus modEventBus) {
        REGISTER.register(modEventBus);
    }
}
```

物品侧取值固定两个方法，**默认实例永远返回副本防止被改**：

```java
@Info("拿到的不是当前实例而是复制体，防止被外部修改")
public WeaponComponent getDefaultWeaponComponent() {
    return weaponComponent.copy();
}

public WeaponComponent getWeaponComponentOrDefault(ItemStack stack) {
    return stack.getComponents().getOrDefault(
            EXDataComponents.WEAPON_COMPONENT.get(),
            getDefaultWeaponComponent());
}
```

`copy()` 手写字段复制即可。**不要写 `clone()`**：不实现 `Cloneable` 的类调 `super.clone()` 必抛异常。

## 网络包模板

包是 record，**领域常量和判定逻辑也放在同一个 record 里**，收发两端共用一份判断，不散落到 handler：

```java
public record ArrowVelocityPacket(int entityId, float x, float y, float z) implements CustomPacketPayload {
    public static final double VANILLA_VELOCITY_LIMIT = 3.9;
    public static final Type<ArrowVelocityPacket> TYPE = new Type<>(ExampleMod.id("arrow_velocity"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ArrowVelocityPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ArrowVelocityPacket::entityId,
            ByteBufCodecs.FLOAT, ArrowVelocityPacket::x,
            ByteBufCodecs.FLOAT, ArrowVelocityPacket::y,
            ByteBufCodecs.FLOAT, ArrowVelocityPacket::z,
            ArrowVelocityPacket::new
    );

    // 便利构造器：把领域类型拆成可序列化的原始类型
    public ArrowVelocityPacket(int entityId, Vec3 velocity) {
        this(entityId, (float) velocity.x, (float) velocity.y, (float) velocity.z);
    }

    // 发送侧判断：需不需要发
    public static boolean requiresExactSync(Vec3 velocity) { ... }
    // 接收侧判断：原版是否会截断
    public static boolean isClamped(double x, double y, double z) { ... }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ArrowVelocityPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Entity entity = context.player().level().getEntity(payload.entityId());
            if (entity instanceof AbstractArrow arrow) {
                IMixinAbstractArrow.of(arrow).ex$setExactVelocity(payload.x(), payload.y(), payload.z());
            }
        });
    }
}
```

`handle` 一律 `context.enqueueWork(...)` 回主线程，内部用 `instanceof` 绑定 + 静默忽略拿不到的实体。

注册用 `@EventBusSubscriber`，协议版本从 mod 自身版本读取（改版本自动失效旧协议）：

```java
@EventBusSubscriber(modid = ExampleMod.MODID)
public class EXPackets {
    @NotNull
    private static final String PROTOCOL_VERSION = ModList.get()
            .getModContainerById(ExampleMod.MODID)
            .map(ModContainer::getModInfo)
            .map(IModInfo::getVersion)
            .map(Object::toString)
            .orElse("unknown");

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        //client
        registrar.playToClient(ArrowVelocityPacket.TYPE, ArrowVelocityPacket.STREAM_CODEC, ArrowVelocityPacket::handle);
    }
}
```

`//client` / `//server` 这种极短注释用来分组注册语句，是容许保留的「说明性注释」形态。

## 物品实现风格

### CustomData 读写惯用法

不为简单标记单独建 DataComponent，直接用 `DataComponents.CUSTOM_DATA`，但要有纪律：**key 全部提成 `TAG_` 常量，读写各封一个方法**。

```java
public static final String TAG_TARGET = "target";
public static final String TAG_IS_SEARCHING = "isSearching";
public static final String TAG_SEARCH_RADIUS = "maxSearchRadius";

private static CompoundTag getCustomTag(ItemStack stack) {
    return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
}

public void updateCustomTag(ItemStack stack, Consumer<CompoundTag> updater) {
    CustomData.update(DataComponents.CUSTOM_DATA, stack, updater);
}

private int getSearchRadius(CompoundTag tag) {
    return tag.contains(TAG_SEARCH_RADIUS) ? tag.getInt(TAG_SEARCH_RADIUS) : 5000;
}
```

每个可选字段配一个带默认值的私有 getter，调用点不出现 `tag.contains(...) ? ... : ...`。

**跨方法传递的临时数据**（不该被玩家看见、也不该持久化的）用 `ex$` 前缀 key，避免和别的 mod 撞：

```java
itemstack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, data ->
        data.update(tag -> tag.putFloat("ex$charge", charge)));
```

### 静态工厂重载链

给 KubeJS 或其他代码造预设物品时，写**重载链**而不是一堆可选参数，最长的那个接 `Consumer<CompoundTag>`：

```java
public static ItemStack forTarget(String targetName)
public static ItemStack forTarget(String targetName, String displayName)
public static ItemStack forTarget(String targetName, String displayName, Consumer<CompoundTag> updater)
public static ItemStack forTarget(String targetName, int searchRadius, boolean skipKnown,
                                  byte zoomLevel, String decorationType, @Nullable String displayName)
```

### 耗时操作：线程池 + 回主线程 + 重入标记

```java
private static final ExecutorService EXECUTORS = Executors.newFixedThreadPool(2);

updateCustomTag(stack, t -> t.putBoolean(TAG_IS_SEARCHING, true));   // 重入标记
EXECUTORS.submit(() -> {
    try {
        BlockPos foundPos = findNearest(...);
        level.getServer().execute(() -> {                            // 回主线程
            updateCustomTag(stack, t -> t.putBoolean(TAG_IS_SEARCHING, false));
            ...
        });
    } catch (Exception ignored) {
        level.getServer().execute(() -> { /* 复位 + 报错提示 */ });
    }
});
```

标记位同时用于 tooltip 显示「正在处理…」和 `use()` 里的重入拦截。异步结果**必须**经 `level.getServer().execute(...)` 回主线程再改世界状态。

### tooltip 风格

`appendHoverText` 用 lang 常量 + `ChatFormatting` 分色分层，最后调 `super`：

```java
if(component.isInfiniteAmmo()) tooltipComponents.add(EXKeyLang.WeaponInfiniteAmmo.withStyle(ChatFormatting.GREEN));
tooltipComponents.add(EXKeyLang.WeaponBaseDamage.getNumber2f(component.getBaseDamage()).withStyle(ChatFormatting.AQUA));
component.ammoBaseDamage().ifPresent(v -> tooltipComponents.add(
        EXKeyLang.WeaponAmmoBaseDamage.getNumber2f(v).withStyle(ChatFormatting.AQUA)));
tooltipComponents.add(EXKeyLang.WeaponVelocity.getNumber2f(component.getVelocity()).withStyle(ChatFormatting.DARK_GRAY));
super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
```

配色约定：GREEN = 布尔型特性开关，AQUA = 核心数值，DARK_GRAY = 细节参数。`Optional` 字段用 `ifPresent` 决定是否显示行。数值一律经 `LazyComponent.getNumber2f` / `getNumber1f` 格式化，不用 `String.format` 拼在翻译外面。




