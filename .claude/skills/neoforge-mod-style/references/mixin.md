# Mixin 组织与写法

## 目录即加载条件

配一个 `IMixinConfigPlugin`，把**包名当成加载条件**，这样放对包 = 写好了条件：

```java
public class EXMixinPlugin implements IMixinConfigPlugin {
    /**
     * mixin 包下的类严格按 "modid/modid/.../MixinXXX" 命名。<br>
     * 程序会逐段遍历包名，检查是否加载了同名 mod；有一段没装就整包不生效。<br>
     * 包名段为 client/server/common 时无条件通过。
     */
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        List<String> modList = LoadingModList.get().getMods().stream().map(ModInfo::getModId).toList();
        String modIds = mixinClassName.replace(this.getClass().getPackageName() + ".", "")
                                      .replaceAll("^(.*)(\\.).*$", "$1");
        for (String string : modIds.split("\\.")) {
            if("client".equals(string) || "server".equals(string) || "common".equals(string)) return true;
            else if (!modList.contains(string)) return false;
        }
        return true;
    }

    // 其余接口方法留空 / 返回 null
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String t, ClassNode c, String m, IMixinInfo i) {}
    @Override public void postApply(String t, ClassNode c, String m, IMixinInfo i) {}
}
```

规则：

- `mixin/othermod/MixinSomeClass` → 只在装了 `othermod` 时应用。
- `mixin/othermod/hudmod/MixinMixinSomething` → **othermod 和 hudmod 都装了**才应用（逐段检查）。
- `mixin/examplemod/MixinLivingEntity` → 自己的 modid 必然存在，等于「无条件」，用于改原版。
- 包名段是 `client` / `server` / `common` 时无条件通过。

**新建 Mixin 前先想清楚它依赖哪些 mod，然后按依赖链建包**。这样不需要 `@Restriction`、不需要在 mixins.json 里拆多个配置。

## 类命名

| 情形 | 命名 |
|---|---|
| 普通 mixin | `Mixin<目标类简名>`，如 `MixinLivingEntity` |
| 目标类本身是别人的 Mixin 类 | 保留对方名字，如 `MixinItemRendererMixin` |
| MixinSquared 二次 mixin | `MixinMixin<目标类简名>`，如 `MixinMixinFoodData` |
| 桥接接口 | `IMixin<目标类简名>`，放 `util/mixin/` |

## mixins.json 约定

```json
{
  "required": true,
  "minVersion": "0.8",
  "package": "com.example.examplemod.mixin",
  "compatibilityLevel": "JAVA_21",
  "refmap": "examplemod.refmap.json",
  "plugin": "com.example.examplemod.mixin.EXMixinPlugin",
  "mixins":  [ "examplemod.MixinLivingEntity", "othermod.MixinSomeClass" ],
  "client":  [ "hudmod.MixinOverlays" ],
  "injectors": { "defaultRequire": 1 },
  "overwrites": { "requireAnnotations": true }
}
```

- **只碰客户端类的 mixin 必须放 `client` 段**，否则专用服务器会崩。
- `mixins` / `client` 数组按包名字母序排列。
- `defaultRequire: 1` —— 注入点找不到就报错，不静默跳过。
- `requireAnnotations: true` —— `@Overwrite` 必须写 `@author` + `@reason`。

## 注入器选择顺序

1. **`@WrapOperation`（MixinExtras）—— 首选**。要改「某次调用」的参数、返回值，或者干脆不让它执行。
2. **`@ModifyReturnValue`** —— 只想改方法返回值。
3. **`@ModifyVariable(argsOnly = true)`** —— 想统一改某个入参。
4. **`@Inject`** —— 只是想在某处插一段，或 `cir.setReturnValue` 提前返回。
5. **`@Redirect`** —— 只在 `@WrapOperation` 表达不出来时用（典型场景：重定向静态字段访问）。
6. **`@Overwrite`** —— 最后手段，必须带 `@author` / `@reason`。

局部变量一律用 MixinExtras sugar，不靠 `@Inject` 尾参数硬数索引：

```java
@Local(argsOnly = true) LocalFloatRef amount                     // 按类型 + argsOnly 抓参数
@Local(name = "knockback", index = 4, argsOnly = true) LocalFloatRef knockbackRef
@Local(name = "charge") LocalFloatRef charge                     // 按变量名抓局部变量
@Local(argsOnly = true) LootContext context                      // 只读则不用 Ref
```

## 各注入器模板

### @WrapOperation：改参数后放行

```java
@WrapOperation(
        method = "shoot",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;processProjectileSpread(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/Entity;F)F")
)
public float modifySpread(ServerLevel level, ItemStack tool, Entity entity, float projectileSpread, Operation<Float> original) {
    if(tool.getItem() instanceof ExampleWeaponItem item) {
        float spread = item.getWeaponComponentOrDefault(tool).getMultiShootSpread();
        if(spread != 0) return spread;
    }
    return original.call(level, tool, entity, projectileSpread);
}
```

签名规则：**目标调用的所有参数（实例方法还要在最前面加实例）+ `Operation<返回类型> original` + 想额外拿的外层参数 / `@Local`**。

### @WrapOperation：短路掉整个计算

想「让某个数值恒定」时，不调 `original` 直接返回。这是**削平别人数值体系最干净的手法：不删对方逻辑，只把它依赖的取值钉死**：

```java
// 让某个属性加成失效
@WrapOperation(method = "applyRangedDamage",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/attributes/AttributeInstance;getValue()D"))
private static double cancelRangedDamage(AttributeInstance instance, Operation<Double> original) {
    return 1.0f;
}

// 让「按玩家人数倍增」恒为 1
@WrapOperation(method = "multiplePlayerEnhance", at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I"))
private static int modifyMultiple(int a, int b, Operation<Integer> original) {
    return 1;
}
```

### @ModifyReturnValue

```java
@ModifyReturnValue(method = "calculateValue", at = @At("RETURN"))
public double getValueModify(double original) {
    if(this.attribute == SomeAttributes.CRIT_CHANCE) return 0.0f;
    else return original;
}
```

### @ModifyVariable：统一改入参

同一个改法要作用于多个重载/多个方法时，写多个 `@ModifyVariable` 指向同一个 `@Unique` 静态方法：

```java
@ModifyVariable(method = "addOrUpdateTransientModifier", at = @At("HEAD"), argsOnly = true)
public AttributeModifier modifyTransientModifier(AttributeModifier original) {
    return ex$rewriteModifier(original);
}
// ...其余几个方法同样指向它

@Unique
private static AttributeModifier ex$rewriteModifier(AttributeModifier original) { ... }
```

### @Inject：插入 + 提前返回

```java
@Inject(
        method = "getAttributePercent",
        at = @At("HEAD"),
        cancellable = true
)
private static void modifyPercent(Holder<Attribute> attribute, LivingEntity entity, CallbackInfoReturnable<Float> cir) {
    if(attribute == SomeAttributes.SUMMON_DAMAGE) cir.setReturnValue(1.0f);
}
```

`@At` 支持精确定位，`ordinal` 用来区分同一方法里的多个相同调用点：

```java
at = @At(value = "RETURN", ordinal = 5)
at = @At(value = "INVOKE", target = "...actuallyHurt(...)V", ordinal = 0)
at = @At(value = "INVOKE", target = "...setSpeed(F)V", shift = At.Shift.AFTER)
```

## 给目标类加字段：IMixinXxx 桥接接口

给别的类挂状态或开放私有成员时，**一律走「桥接接口 + `of()` 静态转换」**，调用方不出现强转。

接口放 `util/mixin/`：

```java
public interface IMixinAbstractArrow {
    void ex$setExactVelocity(double x, double y, double z);
    void ex$setFakeSpread(float fakeSpread);
    void ex$setPickupItemStackOrigin(ItemStack itemstack);

    static IMixinAbstractArrow of(AbstractArrow arrow) {
        return (IMixinAbstractArrow) arrow;
    }
}
```

Mixin 实现它：

```java
@Mixin(AbstractArrow.class)
public abstract class MixinAbstractArrow extends Projectile implements IMixinAbstractArrow {
    @Shadow private ItemStack pickupItemStack;
    @Shadow @Nullable private ItemStack firedFromWeapon;
    @Shadow protected abstract void setPierceLevel(byte pierceLevel);

    protected MixinAbstractArrow(EntityType<? extends Projectile> entityType, Level level) {
        super(entityType, level);
    }

    @Unique private float ex$fakeSpread = 0;
    @Unique private boolean ex$hasExactVelocity;

    @Override
    public void ex$setExactVelocity(double x, double y, double z) {
        this.setDeltaMovement(x, y, z);
        this.ex$hasExactVelocity = true;
    }
}
```

调用方：

```java
IMixinAbstractArrow.of(arrow).ex$setExactVelocity(payload.x(), payload.y(), payload.z());
```

规则：

- **`@Unique` 只标注新增的私有字段/私有方法**。实现接口的 `@Override` 方法不需要 `@Unique`。
- 桥接接口方法名带 `ex$` 前缀。
- 只需要开放私有成员、不需要新增行为时，也用这个模式（接口里可以只有两个 setter）。
- 接口上方写一行 Javadoc 说明它桥接的是什么：`/** Access bridge for XXX 的 YYY 字段。 */`

## 继承目标类的父类

需要访问目标类的 `protected` 成员，或者需要调 `super.xxx()` 时，让 mixin **继承目标类的父类**：

```java
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity extends Entity implements ILivingEntityExtension {
    public MixinLivingEntity(EntityType<?> entityType, Level level) { super(entityType, level); }
    // 现在可以直接用 invulnerableTime 这类 protected 字段
}
```

```java
@Mixin(SomeArrowEntity.class)
public abstract class MixinSomeArrowEntity extends AbstractArrow {
    @WrapOperation(method = "onHitEntity", at = @At(value = "INVOKE", target = "...Entity;hurt(...)Z"))
    public boolean refineHurt(Entity instance, DamageSource source, float amount, Operation<Boolean> original) {
        float baseDamage = (float) super.getBaseDamage();   // 绕过子类覆写，取父类实现
        return original.call(instance, source, baseDamage);
    }
}
```

mixin 类要声明为 `abstract`，并补一个转发到 `super` 的构造器（不会被真正调用，只为编译通过）。

## @Shadow 用法

```java
@Shadow protected Stack<DamageContainer> damageContainers;              // 字段
@Shadow protected abstract float getDamageAfterMagicAbsorb(...);        // 方法：abstract + 无实现
@Shadow @Final @Mutable protected float damage;                         // 解锁 final 字段以便修改
@Shadow private static boolean initialized;                             // 静态字段
```

`@Final @Mutable` 组合用于「对方把配置存成 final 字段、我们要在运行时改」的场景，配合 `IMixinXxx` 暴露 setter。

## 拿 this 当目标类型

用 `Target.class.cast(this)`，不写 `(Target)(Object)this`：

```java
SomeEntity entity = SomeEntity.class.cast(this);
NeoForge.EVENT_BUS.post(new SomeEntityEvent(entity));
```

## MixinSquared：改别人 mixin 里的 handler

当要改的逻辑本身是别的 mod 用 mixin 注入的（不在原类里），用 MixinSquared 二次注入。命名 `MixinMixin<目标类>`，`priority` 必须**高于**对方（默认 1000，取 1500）：

```java
@Mixin(value = FoodData.class, priority = 1500)
public class MixinMixinFoodData {
    @TargetHandler(
            mixin = "com.othermod.mixin.MixinFoodData",   // 对方的 mixin 类全限定名
            name = "modifyHeal0"                          // 对方的 handler 方法名
    )
    @WrapOperation(
            method = "@MixinSquared:Handler",             // 固定字面量
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;heal(F)V")
    )
    private void wrapOtherModHeal(Player instance, float v, Operation<Void> original) {
        original.call(instance, v + instance.getMaxHealth() * 0.02f);
    }
}
```

```java
@Mixin(value = WorldOptions.class, priority = 1500)
public class MixinMixinWorldOptions {
    @TargetHandler(mixin = "com.othermod.mixin.WorldOptionsMixin", name = "othermod$getSecretFlag")
    @ModifyReturnValue(method = "@MixinSquared:Handler", at = @At("RETURN"))
    public long modify(long original) {
        return original | 256;
    }
}
```

三个固定点：`@Mixin` 指向**最终目标类**（不是对方的 mixin 类）、`method = "@MixinSquared:Handler"`、`@TargetHandler` 提供对方 mixin 类名 + handler 名。包名要放在**两个 mod 都在场**的路径下。

依赖声明：

```gradle
compileOnly(annotationProcessor("com.github.bawnorton.mixinsquared:mixinsquared-common:<version>"))
implementation(jarJar("com.github.bawnorton.mixinsquared:mixinsquared-neoforge:<version>"))
```

## 其他惯用法

### 消除刷屏日志

```java
@Redirect(
        method = "loadBlockStateDefinitions",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/resources/model/BlockStateModelLoader;LOGGER:Lorg/slf4j/Logger;", opcode = 178),
        require = 2
)
private static Logger ex$redirectLogger0() {
    return NOPLogger.NOP_LOGGER;
}
```

`opcode = 178` 是 `GETSTATIC`；`require = N` 声明预期命中次数，少了就报错。lambda 里的访问要单独写一条（方法名形如 `lambda$loadBlockStateDefinitions$10`）。

### 整个方法作废

```java
@Inject(method = "registerSomething", at = @At("HEAD"), cancellable = true)
private static void cancel(CallbackInfo ci) { ci.cancel(); }
```

### @Overwrite（必须带注解）

```java
/**
 * @author <作者>
 * @reason <为什么必须整体重写>
 */
@Overwrite
private static String processLine(String line) { ... }
```

### 从 Mixin 发自定义事件

Mixin 只负责「开洞」，具体逻辑交给事件（详见 `event-api.md`）：

```java
@WrapOperation(method = "shoot", at = @At(value = "INVOKE", target = "...shootProjectile(...)V"))
private void modifyShootProjectile(..., Operation<Void> original) {
    ProjectileShootEvent event = NeoForge.EVENT_BUS.post(
            new ProjectileShootEvent(shooter, projectile, i, velocity, inaccuracy, angle, target));
    if(event.isCanceled()) return;
    original.call(instance, event.getShooter(), event.getProjectile(), event.getIndex(),
            event.getVelocity(), event.getInaccuracy(), event.getAngle(), event.getTarget());
}
```

## 新增 Mixin 检查清单

1. 目标类属于哪个 mod？→ 决定包名（多 mod 依赖就嵌套包）。
2. 只在客户端存在的类？→ 登记到 mixins.json 的 `client` 段。
3. 能用 `@WrapOperation` / `@ModifyReturnValue` 解决吗？→ 优先它们，别上 `@Redirect` / `@Overwrite`。
4. 要加字段或开放私有成员？→ 建 `util/mixin/IMixin<目标>` 桥接接口 + `of()`。
5. 新成员是否都带 `ex$` 前缀？
6. 要改的逻辑本身是别人 mixin 注进去的？→ MixinSquared，`priority = 1500`。
7. 登记进 `<modid>.mixins.json`（按字母序插入）。
8. 逻辑是否应该抽成自定义事件让脚本能改？



