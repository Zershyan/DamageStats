# 自定义事件 API 与 Helper 门面

## 分层

```
Mixin（mixin/…）                  在底层开洞，post 事件、读回结果
      ↓
api/event/XxxEvent                事件定义：原值 + 可改值
      ↓
handler/…Handler                  桥接原版事件 → 自定义事件，或直接实现内置行为
      ↓
kubejs/event/XxxKubeEvent         镜像给 JS（见 kubejs.md）
      ↓
api/helper/XxxHelper + EXApi      语义化门面，给 Java 和 JS 共用
```

原则：**Mixin 里不写玩法逻辑**，只负责 post 事件并把事件结果写回。玩法逻辑要么在 handler 里，要么在 KubeJS 脚本里。

## 事件类模板

### 普通事件：原值 + 可改值双字段

```java
public class ResourceCostEvent extends Event implements ICancellableEvent {
    private final ServerPlayer player;
    private final float originalCost;   // 永不变，供监听者判断
    private float cost;                 // 监听者改这个

    public ResourceCostEvent(ServerPlayer player, float originalCost) {
        this.player = player;
        this.originalCost = originalCost;
        this.cost = originalCost;       // 初值 = 原值
    }

    public float getCost() { return cost; }
    public float getOriginalCost() { return originalCost; }
    public ServerPlayer getPlayer() { return player; }
    public void setCost(float cost) { this.cost = cost; }
}
```

**「可被修改的量」一律配 `originalXxx` / `getOriginalXxx()`**，让多个监听者能判断「别人改过了吗」。

可取消就 `implements ICancellableEvent`（`isCanceled()` / `setCanceled()` 由接口默认实现提供）。

### 上下文事件：全 final + 少量可写

```java
public class ProjectileShootEvent extends Event implements ICancellableEvent {
    private final LivingEntity shooter;
    private final @Nullable LivingEntity target;
    private final int index;            // 不可变的上下文
    private Projectile projectile;      // 可替换
    private float velocity;
    private float inaccuracy;
    private float angle;

    public ProjectileShootEvent(
            LivingEntity shooter,
            Projectile projectile,
            int index,
            float velocity,
            float inaccuracy,
            float angle,
            @Nullable LivingEntity target
    ) { ... }
}
```

构造器参数多就每个参数独立成行。可空字段写成 `private final @Nullable LivingEntity target;`（注解贴在类型前）。

### 抽象基类 + 子类：共享上下文

两种拆法，按语义选。

**A. 抽象基类 + 平行子类**（不同语义、共享上下文）：

```java
public abstract class ModifyEnchantmentEvent extends Event {
    private final LivingEntity living;
    private final ItemStack tool;
    // getLiving() / getTool()
}

public class ModifyEnchantmentLevelEvent extends ModifyEnchantmentEvent { /* originLevel + level */ }
public class ApplySilkTouchEvent extends ModifyEnchantmentEvent { /* applied */ }
```

**B. 抽象基类 + 嵌套时机子类**（同一件事的不同时机）：

```java
public abstract class AttachDamageEvent extends Event {
    // 公共能力全部放在父类，子类只提供各自包装的原版事件
    public abstract LivingEntity getEntity();

    public DamageSource defaultAttachDamage(DamageSource damageSource) { ... }
    public void causeAttachDamage(DamageSource damageSource, float amount) { ... }

    public static class Early extends AttachDamageEvent {
        private final LivingIncomingDamageEvent event;
        public LivingIncomingDamageEvent getOriginalEvent() { return event; }
        @Override public LivingEntity getEntity() { return event.getEntity(); }
    }

    public static class Late extends AttachDamageEvent {
        private final LivingDamageEvent.Post event;
        public LivingDamageEvent.Post getOriginalEvent() { return event; }
        @Override public LivingEntity getEntity() { return event.getEntity(); }
    }
}
```

嵌套子类命名用时机词：`Early` / `Late`（对应 KubeJS 事件名 `attachDamage$early` / `$late`）。**公共方法必须提到抽象父类**，子类之间不允许出现复制粘贴的同名方法。

## 触发方式一：Mixin 里 post 并回写

`NeoForge.EVENT_BUS.post(...)` 返回的就是事件对象本身，直接接着读：

```java
// 改单个数值
@WrapOperation(method = "run", at = @At(value = "INVOKE", target = "...getItemEnchantmentLevel(...)I"))
public int modifyEnchantment(Holder<Enchantment> enchantment, ItemStack stack, Operation<Integer> original,
                             @Local(argsOnly = true) LootContext context) {
    Entity orNull = context.getParamOrNull(LootContextParams.THIS_ENTITY);
    int originLevel = original.call(enchantment, stack);
    if (!(orNull instanceof LivingEntity living)) return originLevel;
    ModifyEnchantmentLevelEvent event = NeoForge.EVENT_BUS.post(
            new ModifyEnchantmentLevelEvent(living, stack, enchantment, originLevel));
    return event.getLevel();
}
```

```java
// 改局部变量（配 LocalFloatRef）
@Inject(method = "extractResource", at = @At(value = "INVOKE", target = "...applyAutoRefill(...)Z"))
public void onResourceExtract(ServerPlayer serverPlayer, CallbackInfoReturnable<Boolean> cir,
                              @Local(name = "extract") LocalFloatRef extract) {
    ResourceCostEvent event = NeoForge.EVENT_BUS.post(new ResourceCostEvent(serverPlayer, extract.get()));
    if(event.isCanceled()) extract.set(0);
    else extract.set(event.getCost());
}
```

**取消语义要落到具体行为上**，不是简单 `return`——这里「取消」= 消耗 0 资源。

## 触发方式二：Handler 桥接原版事件

```java
@EventBusSubscriber(modid = ExampleMod.MODID)
public class AttachDamageHandler {
    @SubscribeEvent
    public static void incomingDamage(LivingIncomingDamageEvent event) {
        if(EXApi.damageSource(event.getSource()).isAttachDamage()) return;   // 防递归
        NeoForge.EVENT_BUS.post(new AttachDamageEvent.Early(event));
    }

    @SubscribeEvent
    public static void damagePost(LivingDamageEvent.Post event) {
        if(EXApi.damageSource(event.getSource()).isAttachDamage()) return;
        NeoForge.EVENT_BUS.post(new AttachDamageEvent.Late(event));
    }
}
```

「事件里又造成伤害」这类会自我触发的场景，用**对象上的标记位**当哨兵（这里是伤害源上通过 Mixin 挂的 `ex$attachDamage`），不用 ThreadLocal 或计数器。

## Handler 写法约定

```java
@EventBusSubscriber(modid = ExampleMod.MODID)                     // 双端
@EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT) // 仅客户端
```

- 类放 `handler/common/` 或 `handler/client/`，命名 `<功能>Handler`。
- 全部 `public static void` + `@SubscribeEvent`，类本身不需要实例，写成 `final class` 就够了。**不要为了「防止实例化」补一个空的 private 构造器**，那是纯噪音。
- 一个 Handler 只管一件事。最小的 Handler 可以只有三行：

  ```java
  @SubscribeEvent
  public static void onArmorHurt(ArmorHurtEvent event) {
      event.setCanceled(true);
  }
  ```

- 需要按 mod 是否加载条件注册的，**不用** `@EventBusSubscriber`，改到 `XxxCompat.addCommonListener` 里手动注册（见 `compat.md`）。
- 仅开发期用的调试监听在 Mod 构造器里手动 `NeoForge.EVENT_BUS.register(Xxx.class)`，与正式功能区分开。

## Helper 门面模式

Mixin 加的字段通过 `IMixinXxx` 暴露，但那是「机械」的接口。再包一层 Helper 给出**领域语义 + `@Info` 说明**，Java 和 KubeJS 共用同一套：

```java
public class DamageSourceHelper {
    private final DamageSource damageSource;
    private final IMixinDamageSource mixinSource;

    public DamageSourceHelper(DamageSource damageSource) {
        this.damageSource = damageSource;
        this.mixinSource = IMixinDamageSource.of(damageSource);   // 构造器里一次性做完强转
    }

    @Info("判断是否是附加攻击类型")
    public boolean isAttachDamage() { return mixinSource.ex$isAttachDamage(); }

    @Info("设置为附加攻击类型")
    public void setAttachDamage(boolean attachDamage) { mixinSource.ex$setAttachDamage(attachDamage); }

    @Info("判断是否为静默伤害；静默伤害不触发 LivingDamageEvent")
    public boolean isSilent() { return mixinSource.ex$isSilent(); }

    @Info("判断是否含有 tag")
    public boolean tagMatch(TagKey<DamageType> tagKey) { return damageSource.is(tagKey); }

    public DamageSource getDamageSource() { return damageSource; }
}
```

结构固定：**构造器里一次性做完强转**、原始对象和桥接接口各存一个字段、每个公开方法带 `@Info`、末尾提供 `getXxx()` 拿回原始对象。

## Builder 门面

构造复杂对象时配 `XxxHelperBuilder`：**方法名 = 字段名不带 `set`、返回 this、`create()` 缓存结果**：

```java
public class DamageSourceHelperBuilder {
    private final Holder<DamageType> type;
    @Nullable private Entity causingEntity;
    @Nullable private Entity directEntity;
    @Nullable private Vec3 damageSourcePosition;
    @Nullable private DamageSource source;              // 缓存

    public DamageSourceHelperBuilder causingEntity(@Nullable Entity causingEntity) {
        this.causingEntity = causingEntity;
        return this;
    }

    // 组合快捷方法：一次设好一组相关字段
    public DamageSourceHelperBuilder causeBy(@Nullable Entity source) {
        return causingEntity(source).directEntity(source)
                .damageSourcePosition(source == null ? null : source.position());
    }

    public DamageSource create() {
        if(source != null) return source;
        return source = new DamageSource(type, causingEntity, directEntity, damageSourcePosition);
    }

    public DamageSourceHelper createToHelper() { return new DamageSourceHelper(create()); }
}
```

常用组合提成快捷方法（`causeBy`），并提供 `createToHelper()` 直接进入 Helper 链路。

## EXApi：唯一静态入口

```java
public class EXApi {
    @Info("获取伤害源 Helper")
    public static DamageSourceHelper damageSource(DamageSource source) {
        return new DamageSourceHelper(source);
    }

    @Info("辅助创建 damage source")
    public static DamageSourceHelperBuilder buildSource(Holder<DamageType> type) {
        return new DamageSourceHelperBuilder(type);
    }

    @Info("获取实体攻击速度 modifier 的修正倍率，不含实体自身 base；传入 null 返回 1.0f")
    public static double getAtkSpeedModify(@Nullable LivingEntity attacker) {
        if(attacker == null) return 1.0f;
        AttributeInstance attribute = attacker.getAttribute(Attributes.ATTACK_SPEED);
        if(attribute == null) return 1.0f;
        double baseValue = 1.0f, multiBase = 1.0f, multiTotal = 1.0f;
        for (AttributeModifier modifier : attribute.getModifiers()) {
            switch (modifier.operation()) {
                case ADD_VALUE -> baseValue += modifier.amount();
                case ADD_MULTIPLIED_BASE -> multiBase += modifier.amount();
                case ADD_MULTIPLIED_TOTAL -> multiTotal *= (1 + modifier.amount());
            }
        }
        return baseValue * multiBase * multiTotal;
    }
}
```

- 全静态，方法名短（`damageSource` / `buildSource` / `structure`），返回 Helper 或计算结果。
- **每个方法必须有 `@Info`**，因为它整体绑定给 JS。
- 跨 mod 的能力经 `CompatFactory` 转发，`@Nullable` 语义写进 `@Info` 文本里。
- 通用计算放这里，Java 侧和 JS 侧共用同一份，避免两处各算一遍。

## 新增事件检查清单

1. 事件属于原版/自家范畴 → `api/event/`；属于某个第三方 mod → `compat/<modid>/api/event/`。
2. 可修改的量配 `originalXxx` 只读字段。
3. 可取消就 `implements ICancellableEvent`，并明确「取消」对应的具体行为。
4. Mixin 里 post 后**记得把所有可写字段读回去**（漏一个就是静默失效）。
5. 会自我触发的加标记位哨兵。
6. 抽象基类的子类之间不留重复方法。
7. 需要给 JS 用 → 建镜像事件 + 事件常量（见 `kubejs.md`）。
8. 常用操作抽成 Helper 方法并加 `@Info`。


