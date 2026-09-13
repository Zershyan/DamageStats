package io.zershyan.damagestats.datagen.provider;

import com.mojang.logging.LogUtils;
import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.datagen.init.DSLang;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.data.LanguageProvider;

/** 一个 provider 类跑多个语言。加语言 = 加一个常量 + 一个 case + 一个 runXxx 工厂 */
public class DSLangProvider extends LanguageProvider {
    private static final String enUs = "en_us";
    private static final String zhCn = "zh_cn";

    private final String locale;

    public DSLangProvider(PackOutput output, String locale) {
        super(output, DamageStats.MODID, locale);
        this.locale = locale;
    }

    @Override
    protected void addTranslations() {
        switch (locale) {
            case enUs -> DSLang.getAllLang().forEach(entry -> addTranslation(entry.key(), entry.lang().enDesc()));
            case zhCn -> DSLang.getAllLang().forEach(entry -> addTranslation(entry.key(), entry.lang().zhDesc()));
            default -> LogUtils.getLogger().error("Unsupported locale: {}", locale);
        }
    }

    /** 新增可翻译类型时在这里加 case，default 只记日志再降级，不让 datagen 中断 */
    private <T> void addTranslation(T key, String value) {
        if(key instanceof Item object) {
            add(object, value);
        } else if(key instanceof Block object) {
            add(object, value);
        } else if(key instanceof String object) {
            add(object, value);
        } else if(key instanceof ItemStack object) {
            add(object.getItem(), value);
        } else if(key instanceof MobEffect object) {
            add(object, value);
        } else if(key instanceof EntityType<?> object) {
            add(object, value);
        } else if(key instanceof TagKey<?> object) {
            add("tag." + object.location().toLanguageKey(), value);
        } else {
            LogUtils.getLogger().error("Unknown translation key type: {}", key.getClass());
            add(key.toString(), value);
        }
    }
    public static DSLangProvider runEnUs(PackOutput output) {
        return new DSLangProvider(output, enUs);
    }

    public static DSLangProvider runZhCn(PackOutput output) {
        return new DSLangProvider(output, zhCn);
    }
}
