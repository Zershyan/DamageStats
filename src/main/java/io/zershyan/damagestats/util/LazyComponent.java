package io.zershyan.damagestats.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** 带参翻译的句柄。数字统一在这里格式化，不在调用点拼 String.format */
public record LazyComponent(String key) {
    public MutableComponent get(Object... args) {
        return Component.translatable(this.key, args);
    }

    public MutableComponent getNumber2f(Object... args) {
        return get(formatNumbers(args, "#.##"));
    }

    public MutableComponent getNumber1f(Object... args) {
        return get(formatNumbers(args, "#.#"));
    }

    private static Object[] formatNumbers(Object[] args, String format) {
        for (int i = 0; i < args.length; i++) {
            if(args[i] instanceof Float || args[i] instanceof Double) {
                args[i] = formatOptimized(format, ((Number) args[i]).doubleValue());
            }
        }
        return args;
    }

    /** 固定 Locale.US，否则小数点会跟随系统语言变成逗号 */
    private static String formatOptimized(String format, double value) {
        DecimalFormat df = new DecimalFormat(format, DecimalFormatSymbols.getInstance(Locale.US));
        df.setRoundingMode(RoundingMode.HALF_UP);
        return df.format(value);
    }
}
