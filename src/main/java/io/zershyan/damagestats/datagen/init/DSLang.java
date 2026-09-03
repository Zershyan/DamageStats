package io.zershyan.damagestats.datagen.init;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 翻译条目的聚合基类。{@link Lang} 的字段数 = 支持的语言数，加语言就加字段，
 * 编译器会把所有需要跟着改的地方标出来。
 */
public abstract class DSLang {
    public interface Entry {
        FinalEntry<?> toLang();
    }

    public record Lang(String enDesc, String zhDesc) {}

    public record FinalEntry<T>(T key, Lang lang) implements Entry {
        public FinalEntry(T key, String enDesc, String zhDesc) {
            this(key, new Lang(enDesc, zhDesc));
        }

        @Override
        public FinalEntry<?> toLang() {
            return this;
        }
    }

    protected final List<Entry> entries = new ArrayList<>();

    protected void addEntry(Entry entry) {
        this.entries.add(entry);
    }

    protected <T> void addFinalEntry(T key, String enUs, String zhCn) {
        this.entries.add(new FinalEntry<>(key, enUs, zhCn));
    }

    /** 返回值风格：子类把自己的 static 列表塞进来。翻译需要作为 static 常量被别处引用时用这个 */
    protected List<Entry> init(List<Entry> entries) {
        return entries;
    }

    /** 实例风格：子类在方法体里调 addFinalEntry。遍历注册项配翻译时用这个 */
    protected void init() {
    }

    private List<Entry> initLang() {
        init();
        return init(entries);
    }

    /** 新增 lang 类必须加进这里的 Stream，否则不会生成 */
    public static List<FinalEntry<?>> getAllLang() {
        List<FinalEntry<?>> entryList = new ArrayList<>();
        Stream.of(new DSKeyLang(), new DSConfigLang())
                .map(DSLang::initLang)
                .map(list -> list.stream().map(Entry::toLang).toList())
                .forEach(entryList::addAll);
        return entryList;
    }
}
