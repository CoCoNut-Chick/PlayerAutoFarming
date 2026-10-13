package com.autofarm.client.gui;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/**
 * 只处理整数区间的滑块：内部把 [min, max] 映射到 SliderWidget 要求的 [0, 1]。
 *
 * <p>比原版滑块多几个能力：
 * <ul>
 *   <li>数值后面可以跟一个固定单位后缀（比如 " px"、"%"）</li>
 *   <li>或者传一个格式化器，自己决定怎么显示（扫描间隔就把 tick 换算成秒）</li>
 *   <li>{@link #setIntValue(int)} 支持从外部改值，而且<b>不会</b>回调 onChange ——
 *       这样「点一下预设色，R/G/B 三个滑块一起跳」才不会来回触发写入</li>
 * </ul>
 */
public class LabeledIntSlider extends AbstractSliderButton {

    private final String labelKey;
    private final int min;
    private final int max;
    /** 固定单位后缀，与 formatter 二选一 */
    private final String unitSuffix;
    /** 自定义显示格式，优先级高于 unitSuffix */
    private final IntFunction<String> formatter;
    private final IntConsumer onChange;

    public LabeledIntSlider(int x, int y, int width, int height, String labelKey,
                            int min, int max, int value, IntConsumer onChange) {
        this(x, y, width, height, labelKey, min, max, value, (IntFunction<String>) null, null, onChange);
    }

    public LabeledIntSlider(int x, int y, int width, int height, String labelKey,
                            int min, int max, int value, String unitSuffix, IntConsumer onChange) {
        this(x, y, width, height, labelKey, min, max, value, null, unitSuffix, onChange);
    }

    /** 用格式化器决定显示文本，比如把 tick 换算成「1.00 s」 */
    public LabeledIntSlider(int x, int y, int width, int height, String labelKey,
                            int min, int max, int value,
                            IntFunction<String> formatter, IntConsumer onChange) {
        this(x, y, width, height, labelKey, min, max, value, formatter, null, onChange);
    }

    private LabeledIntSlider(int x, int y, int width, int height, String labelKey,
                             int min, int max, int value,
                             IntFunction<String> formatter, String unitSuffix,
                             IntConsumer onChange) {
        super(x, y, width, height, Component.empty(),
                (double) (clamp(value, min, max) - min) / Math.max(1, max - min));
        this.labelKey = labelKey;
        this.min = min;
        this.max = max;
        this.formatter = formatter;
        this.unitSuffix = unitSuffix;
        this.onChange = onChange;
        updateMessage();
    }

    public int getIntValue() {
        return (int) Math.round(min + this.value * (max - min));
    }

    /** 从外部改值。刻意不触发 onChange，避免和调用方打架 */
    public void setIntValue(int newValue) {
        this.value = (double) (clamp(newValue, min, max) - min) / Math.max(1, max - min);
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        // 父类构造器会回调到这里，那时本类的字段还没赋值，
        // 必须先挡一道，否则会拿着 null 的翻译键去查语言文件。
        if (labelKey == null) {
            return;
        }
        String text;
        if (formatter != null) {
            text = formatter.apply(getIntValue());
        } else if (unitSuffix != null) {
            text = getIntValue() + unitSuffix;
        } else {
            text = Integer.toString(getIntValue());
        }
        setMessage(Component.translatable("autofarm.toggle",
                Component.translatable(labelKey), Component.literal(text)));
    }

    @Override
    protected void applyValue() {
        onChange.accept(getIntValue());
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
