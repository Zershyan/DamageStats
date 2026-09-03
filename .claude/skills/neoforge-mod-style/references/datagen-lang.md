# Datagen 与多语言 Lang 体系

**不手写任何 lang json**。翻译全部在 Java 里声明，且声明的同时就拿到能用的 `Component`。`src/generated/resources` 是产物，永远不要手改。

## 体系全貌

```
EXKeyLang           声明式翻译常量（key → Component），"写一行 = 登记多语言 + 拿到 Component"
EXRegistryLang      给注册项（物品/属性/效果…）配翻译
EXConfigLang        给配置项配翻译，同时提供 toml 键名（见 config.md）
        ↓ 都继承
     EXLang          聚合器：getAllLang() 收集所有条目
        ↓
  EXLangProvider     一个类跑多个 locale，靠 switch 挑对应语言的文本
        ↓
DataGeneratorHandler  GatherDataEvent 里 createProvider(EXLangProvider::runEnUs / ::runZhCn)
```

## 聚合基类

```java
public abstract class EXLang {
    public interface Entry { FinalEntry<?> toLang(); }
    public record Lang(String enDesc, String zhDesc) {}
    public record FinalEntry<T>(T key, Lang lang) implements Entry {
        public FinalEntry(T key, String enDesc, String zhDesc) { this(key, new Lang(enDesc, zhDesc)); }
        @Override public FinalEntry<?> toLang() { return this; }
    }

    protected final List<Entry> entries = new ArrayList<>();

    protected void addEntry(Entry entry) { this.entries.add(entry); }
    protected <T> void addFinalEntry(T key, String enUs, String zhCn) {
        this.entries.add(new FinalEntry<>(key, enUs, zhCn));
    }

    /** 有返回值初始化：子类返回自己的 static 列表，用于 static 常量场景 */
    protected List<Entry> init(List<Entry> entries) { return entries; }
    /** 无返回初始化：子类在方法里调 addEntry / addFinalEntry */
    protected void init() {}

    private List<Entry> initLang() {
        init();
        return init(entries);
    }

    public static List<FinalEntry<?>> getAllLang() {
        List<FinalEntry<?>> entryList = new ArrayList<>();
        Stream.of( // 添加 lang 类
                new EXKeyLang(),
                new EXRegistryLang()
        ).map(EXLang::initLang)
         .map(list -> list.stream().map(Entry::toLang).toList())
         .forEach(entryList::addAll);
        return entryList;
    }
}
```

`FinalEntry` 的 key 是泛型 `T`，可以是 `String`、`Item`、`Block`、`MobEffect`……由 provider 端分派。

`Lang` record 的字段数 = 支持的语言数。加语言就加字段，编译器会把所有需要改的地方标出来。

**两个 init 钩子的分工**（刻意设计，Javadoc 里要写清）：

- `init()` —— 实例风格，子类在里面调 `addFinalEntry(...)`。适合「遍历注册项配翻译」。
- `init(List)` —— 返回值风格，子类把自己的 `static` 列表塞进去返回。适合「翻译需要作为 `public static final` 常量被别处引用」的场景，因为常量必须在类初始化时就建好。

新增一个 lang 类时，除了继承基类，**必须把它加进 `getAllLang()` 的 `Stream.of(...)`**，否则不会生成。

## 声明式翻译常量（最常用）

核心是三个私有工厂方法，把「登记」和「取用」合成一步：

```java
public class EXKeyLang extends EXLang {
    private static final List<FinalEntry<String>> TranslatableLang = new ArrayList<>();
    private static final String Modid = ExampleMod.MODID;
    private static final String ItemDescPrefix = "item." + Modid + ".";
    private static final String MessagePrefix = "message." + Modid + ".";

    private static String entryString(String key, String enUs, String zhCn) {
        TranslatableLang.add(new FinalEntry<>(key, enUs, zhCn));
        return key;
    }
    private static MutableComponent entry(String key, String enUs, String zhCn) {
        return Component.translatable(entryString(key, enUs, zhCn));
    }
    private static LazyComponent entryLazy(String key, String enUs, String zhCn) {
        return new LazyComponent(entryString(key, enUs, zhCn));
    }

    @Override
    public List<Entry> init(List<Entry> entries) {
        entries.addAll(TranslatableLang);
        return entries;
    }
}
```

声明一条翻译 = 写一个 `public static final`，用分组注释隔开：

```java
// 提示消息
public static final MutableComponent TargetNotSet = entry(MessagePrefix + "target.not_set",
        "No target set", "未设置目标");
public static final MutableComponent Searching = entry(MessagePrefix + "target.searching",
        "Searching...", "正在搜索...");

// 武器 tooltip
public static final LazyComponent WeaponBaseDamage = entryLazy(ItemDescPrefix + "weapon.basedamage",
        "Base Damage: %s", "基础伤害：%s");
```

约定：

- **无参翻译用 `entry` 得 `MutableComponent`**，直接 `.withStyle(...)` 就能塞进 tooltip。
- **带 `%s` 的用 `entryLazy` 得 `LazyComponent`**，调用时 `get(args)` / `getNumber2f(args)`。因为 `Component.translatable` 的参数必须在调用时才知道。
- **key 前缀提成常量**（`ItemDescPrefix` / `MessagePrefix` / …），新增类别就加一个前缀常量。
- 常量名 PascalCase，见 `naming-and-layout.md`。

## LazyComponent：带参翻译 + 数字格式化

```java
public record LazyComponent(String key) {
    public MutableComponent get(Object... args) {
        return Component.translatable(this.key, args);
    }

    public MutableComponent getNumber2f(Object... args) {
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof Float || args[i] instanceof Double) {
                args[i] = formatOptimized("#.##", ((Number) args[i]).doubleValue());
            }
        }
        return get(args);
    }

    public MutableComponent getNumber1f(Object... args) { /* 同上，格式为 #.# */ }

    private static String formatOptimized(String format, double value) {
        DecimalFormat df = new DecimalFormat(format, DecimalFormatSymbols.getInstance(Locale.US));
        df.setRoundingMode(RoundingMode.HALF_UP);
        return df.format(value);
    }
}
```

`Locale.US` 固定小数点符号，避免跟随系统语言变成逗号。**tooltip 里的数字一律走这里**，不要 `String.format` 之后再拼进翻译。

## 给注册项配翻译

用 `init()`（无返回钩子），每种注册类型配一个 `entryXxx` 方法，把「怎么从注册项拿 key」这件事封起来：

```java
public class EXRegistryLang extends EXLang {
    public void entryItem(DeferredItem<?> item, String enUs, String zhCn) {
        addFinalEntry(item.asItem(), enUs, zhCn);
    }

    public void entryAttribute(Holder<Attribute> attribute, String enUs, String zhCn) {
        addFinalEntry(attribute.value().getDescriptionId(), enUs, zhCn);
    }

    @Override
    protected void init() {
        entryItem(EXItems.EXAMPLE_ITEM, "Example Item", "示例物品");
        entryItem(EXItems.EXAMPLE_WEAPON, "Example Weapon", "示例武器");
        entryAttribute(EXAttributes.AERIAL_JUMP, "Aerial Jump Count", "空中跳跃次数");
    }
}
```

`entryItem` 传的是 `Item` 对象（provider 端 `add(Item, String)` 会自己取 descriptionId）；`entryAttribute` 传的是字符串 key（属性没有对应的 `add` 重载）。新增注册类型时按这个原则选：**provider 有重载就传对象，没有就传 `getDescriptionId()`**。

## Provider：一个类跑多个 locale

```java
public class EXLangProvider extends LanguageProvider {
    private final String locale;
    private static final String enUs = "en_us";
    private static final String zhCn = "zh_cn";

    public EXLangProvider(PackOutput output, String locale) {
        super(output, ExampleMod.MODID, locale);
        this.locale = locale;
    }

    @Override
    protected void addTranslations() {
        switch (locale){
            case enUs -> EXLang.getAllLang().forEach(e -> addTranslation(e.key(), e.lang().enDesc()));
            case zhCn -> EXLang.getAllLang().forEach(e -> addTranslation(e.key(), e.lang().zhDesc()));
        }
    }

    private <T> void addTranslation(T o, String string) {
        switch (o) {
            case Item object -> add(object, string);
            case Block object -> add(object, string);
            case String object -> add(object, string);
            case ItemStack object -> add(object.getItem(), string);
            case MobEffect object -> add(object, string);
            case EntityType<?> object -> add(object, string);
            case TagKey<?> object -> add(object, string);
            default -> {
                LogUtils.getLogger().error("Unknown object type: {}", o.getClass());
                add(o.toString(), string);
            }
        }
    }

    public static EXLangProvider runZhCn(PackOutput output) { return new EXLangProvider(output, zhCn); }
    public static EXLangProvider runEnUs(PackOutput output) { return new EXLangProvider(output, enUs); }
}
```

要点：

- **一个 provider 类 + `runXxx` 静态工厂**，而不是每个语言一个子类。加语言 = 加一个常量 + 一个 `case` + 一个 `runXxx`（并在 `Lang` record 里加字段）。
- `addTranslation` 用 **Java 21 switch 模式匹配**做类型分派，`default` 分支记错误日志后降级 `toString()`——不抛异常，datagen 不中断。
- 新增可翻译类型（比如 `Enchantment`）就在 switch 里加一个 `case`。

## DataGeneratorHandler：入口

```java
@EventBusSubscriber(modid = ExampleMod.MODID)
public class DataGeneratorHandler {
    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        ExistingFileHelper helper = event.getExistingFileHelper();
        PackOutput packOutput = event.getGenerator().getPackOutput();

        event.createProvider(PackMetadataProvider::new);
        event.createProvider(EXLangProvider::runEnUs);
        event.createProvider(EXLangProvider::runZhCn);
        event.addProvider(new EXItemModelProvider(packOutput, helper));
    }
}
```

- 只需要 `PackOutput` 的 provider 用 `event.createProvider(工厂方法引用)`。
- 还需要 `ExistingFileHelper` 等额外参数的用 `event.addProvider(new ...)`。
- 顺序无关，但按 metadata → lang → model 排列。

跑 datagen：`gradlew runData`（run 配置见 `naming-and-layout.md`）。

## 模型 provider：整组套模板

```java
public class EXItemModelProvider extends ItemModelProvider {
    public EXItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, ExampleMod.MODID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        EXItems.BASE.getEntries().forEach(entry -> basicItem(entry.get()));
        EXItems.HANDHELD.getEntries().forEach(entry -> handheldItem(entry.get()));
    }
}
```

物品按模型类型分了多个 `DeferredRegister`（见 `registry.md`），所以这里永远只有几行，**加物品不需要动 provider**。

## PackMetadataProvider：连 pack 描述也走 lang

```java
public class PackMetadataProvider extends PackMetadataGenerator {
    public PackMetadataProvider(PackOutput pOutput) {
        super(pOutput);
        add(PackMetadataSection.TYPE, new PackMetadataSection(
                EXKeyLang.Resource,
                DetectedVersion.BUILT_IN.getPackVersion(PackType.SERVER_DATA)
        ));
    }
}
```

`EXKeyLang.Resource` 是一个普通的翻译常量。**凡是要显示给玩家的字符串，一律来自 lang 声明类，没有例外。**

## 加一条翻译的完整步骤

1. 想清楚是哪类：
   - 界面文字 / tooltip / 提示消息 → `EXKeyLang`
   - 注册项名称 → `EXRegistryLang`
   - 配置项显示名 → `EXConfigLang`，并在配置里用 `.translation(...)` 引用（见 `config.md`）
2. `EXKeyLang` 里挑或加前缀常量（`ItemDescPrefix` / `MessagePrefix` / …）。
3. 加常量：无参用 `entry(...)`，带 `%s` 用 `entryLazy(...)`。命名 PascalCase，放在对应的分组注释下。
4. 在需要的地方引用常量，数值经 `getNumber2f` / `getNumber1f`。
5. 跑 `gradlew runData`，检查 `src/generated/resources/assets/<modid>/lang/*.json` 每个语言文件都有新键。
6. 提交时把 `src/generated` 的改动一起提交。



