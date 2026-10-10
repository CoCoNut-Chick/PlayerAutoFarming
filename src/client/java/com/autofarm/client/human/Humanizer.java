package com.autofarm.client.human;

import com.autofarm.client.config.AutoFarmConfig;
import com.autofarm.client.config.ConfigManager;
import net.minecraft.util.math.BlockPos;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 「防踢模式」的大脑：把一堆「让操作更像真人」的措施集中到一处。
 *
 * <h2>它到底在防什么</h2>
 * 反作弊和服主判断「这是不是机器」，看的从来不是某一次操作，而是<b>模式</b>：
 * <ul>
 *   <li><b>视角精确得像刻上去的</b> —— 准星永远停在方块正中心，一丝不差；</li>
 *   <li><b>节奏是节拍器</b> —— 每个动作之间的间隔完全一致，几十次都不差一 tick；</li>
 *   <li><b>脚步不会停</b> —— 从早跑到晚，从不走神、从不放慢；</li>
 *   <li><b>路线是最优解</b> —— 每次都走出数学意义上的最短路线，人不会这么走。</li>
 * </ul>
 * 这个类就是逐条把这些「机器味」抹掉。它<b>不改任何游戏规则</b>：
 * 不发额外的包、不改移动速度上限、不绕过碰撞 —— 只是在规则允许的范围内，
 * 把本来恒定不变的那几个量换成有惯性的随机量。
 *
 * <h2>为什么要有档位</h2>
 * 每一项措施都在「像人」和「有效率」之间做交换：顿一下脚、犹豫几 tick，
 * 换来的是更像人的节奏，代价是慢一点。所以做成四档，让玩家自己拿捏：
 * <ol>
 *   <li>{@link #LIGHT} 轻度 —— 只打散时序（动作间隔）；</li>
 *   <li>{@link #MEDIUM} 中度 —— 再加上视角微颤、偶尔悬脚、疾跑间歇、动手前犹豫；</li>
 *   <li>{@link #HIGH} 强力 —— 再加上疲劳模型、落脚点偏移、偶尔不走最优路线。</li>
 * </ol>
 *
 * <h2>随机量为什么不能每 tick 现摇</h2>
 * 每 tick 独立摇一次随机数，得到的是一条「白噪声」：看起来跳来跳去，
 * 反而更像机械抖动。真人身上的随机量是<b>有惯性的</b> ——
 * 手会朝一个方向偏一会儿再偏回来。
 * 所以这里所有随机量都走「低频换目标 + 每 tick 朝目标平滑逼近」这套。
 */
public final class Humanizer {

    /** 关闭 */
    public static final int OFF = 0;
    /** 轻度：只打散时序 */
    public static final int LIGHT = 1;
    /** 中度：再加视角、脚步、犹豫 */
    public static final int MEDIUM = 2;
    /** 强力：再加疲劳、落点偏移、路线放松 */
    public static final int HIGH = 3;
    /** 档位总数（面板上的循环按钮用） */
    public static final int LEVEL_COUNT = 4;

    /** 疲劳攒满需要的连续干活 tick（约 5 分钟） */
    private static final int FATIGUE_MAX = 6000;

    // ---- 每 tick 平滑推进的随机量（见类注释最后一段） ----

    /** 瞄准的微颤偏移（度）：yaw / pitch 各一份 */
    private static float aimYaw;
    private static float aimPitch;
    private static float aimYawTarget;
    private static float aimPitchTarget;
    private static int aimHold;

    /** 这一段时间允不允许疾跑（真人不会连跑不停） */
    private static boolean sprintOn = true;
    private static int sprintHold;

    /** 疲劳累积：只在强力档起作用，干活涨、摸鱼落 */
    private static int fatigue;

    private Humanizer() {
    }

    // ================= 档位 =================

    /** 当前生效的档位（0~3）。开关关着时恒为 {@link #OFF} */
    public static int level() {
        AutoFarmConfig config = ConfigManager.get();
        if (config == null || !config.antiKickMode) {
            return OFF;
        }
        return Math.max(OFF, Math.min(HIGH, config.antiKickLevel));
    }

    /** 是不是至少开到了某一档 */
    public static boolean atLeast(int level) {
        return level() >= level;
    }

    /** 0~1 的疲劳程度 */
    public static float fatigue01() {
        return fatigue / (float) FATIGUE_MAX;
    }

    // ================= 每 tick =================

    /**
     * 推进一次内部随机量。由 {@code FarmTaskManager} 在每 tick 最开头调用，
     * 保证同一 tick 里不管谁读它，读到的都是同一个值。
     */
    public static void beginTick() {
        int level = level();
        if (level < LIGHT) {
            reset();
            return;
        }

        // 瞄准微颤：每 6~18 tick 换一次偏移方向，同样平滑逼近。
        // 幅度控制在 1.5 度以内 —— 交互容差是 8~12 度，抖不出界。
        if (--aimHold <= 0) {
            float amplitude = level >= MEDIUM ? 0.6f + rnd() * 0.9f : 0f;
            aimYawTarget = (rnd() * 2f - 1f) * amplitude;
            aimPitchTarget = (rnd() * 2f - 1f) * amplitude;
            aimHold = 6 + rndInt(12);
        }
        aimYaw += (aimYawTarget - aimYaw) * 0.25f;
        aimPitch += (aimPitchTarget - aimPitch) * 0.25f;

        // 疾跑节奏：一段时间开、一段时间关，模拟「跑累了走两步」
        if (--sprintHold <= 0) {
            sprintOn = rnd() < 0.78f;
            sprintHold = 60 + rndInt(140); // 3~10 秒换一次
        }
    }

    /**
     * 喂给疲劳模型：这一 tick 是不是真的在干活。
     *
     * <p>干得越久，{@link #stumble()} 和 {@link #rollHesitate()} 的触发概率越高 ——
     * 一个连续作业半小时的人和刚开始的人，动作节奏本来就该不一样。
     */
    public static void feedWorking(boolean working) {
        if (!atLeast(HIGH)) {
            fatigue = 0;
            return;
        }
        if (working) {
            fatigue = Math.min(FATIGUE_MAX, fatigue + 1);
        } else {
            fatigue = Math.max(0, fatigue - 3);
        }
    }

    /** 退出世界 / 关掉开关时归零 */
    public static void reset() {
        aimYaw = 0f;
        aimPitch = 0f;
        aimYawTarget = 0f;
        aimPitchTarget = 0f;
        aimHold = 0;
        sprintOn = true;
        sprintHold = 0;
        fatigue = 0;
    }

    // ================= 给各处调的钩子 =================

    /** 该叠加到瞄准 yaw 上的轻微晃动（度） */
    public static float aimYawJitter() {
        return atLeast(MEDIUM) ? aimYaw : 0f;
    }

    /** 该叠加到瞄准 pitch 上的轻微晃动（度） */
    public static float aimPitchJitter() {
        return atLeast(MEDIUM) ? aimPitch : 0f;
    }

    /**
     * 这一 tick 要不要「悬一下脚」——把前进键放下，让脚步不再是等间距的。
     *
     * <p>概率刻意压得很低（千分之几，还随疲劳上浮）：它要的是「偶尔慢半拍」，
     * 不是「走着走着愣住」。调用方记得把同期的卡住检测让开，否则我们自己停的
     * 这一下会被当成「走不动了」。
     */
    public static boolean stumble() {
        if (!atLeast(MEDIUM)) {
            return false;
        }
        return rnd() < 0.004 + fatigue01() * 0.010;
    }

    /** 这一段时间允不允许疾跑 */
    public static boolean allowSprint() {
        return !atLeast(MEDIUM) || sprintOn;
    }

    /** 两个动作之间的间隔，抖动一下再返回 */
    public static int gap(int base) {
        if (!atLeast(LIGHT)) {
            return base;
        }
        int spread = atLeast(HIGH) ? 2 : 1;
        return Math.max(1, base + rndInt(spread * 2 + 1) - spread);
    }

    /** 动手之前要不要先「打量一下」（概率随疲劳上升） */
    public static boolean rollHesitate() {
        if (!atLeast(MEDIUM)) {
            return false;
        }
        return rnd() < 0.22 + fatigue01() * 0.35;
    }

    /** 犹豫多久（tick） */
    public static int hesitateTicks() {
        if (!atLeast(MEDIUM)) {
            return 0;
        }
        return (atLeast(HIGH) ? 6 : 3) + rndInt(atLeast(HIGH) ? 10 : 5);
    }

    /**
     * 落脚点的细微偏移（格）。
     *
     * <p>同一个坐标每次算出来都一样（按坐标哈希），所以它是「这一格有个固定的偏差」，
     * 而不是每 tick 抖一下 —— 后者会让角色原地打摆子。幅度只有 ±0.15 格，
     * 人还是走在格子里，只是不再精确踩着正中心。
     */
    public static double spotJitter(BlockPos pos, int axis) {
        if (!atLeast(HIGH) || pos == null) {
            return 0.0;
        }
        int hash = pos.hashCode() * 31 + axis * 17;
        return (Math.floorMod(hash, 31) - 15) / 100.0; // -0.15 ~ 0.15
    }

    /** 规划路线时要不要「偷个懒」不走最优解 —— 真人不会每次都算出最短路线 */
    public static boolean skipOptimize() {
        if (!atLeast(MEDIUM)) {
            return false;
        }
        return rnd() < 0.15;
    }

    // ================= 随机 =================

    private static float rnd() {
        return ThreadLocalRandom.current().nextFloat();
    }

    private static int rndInt(int bound) {
        return bound <= 0 ? 0 : ThreadLocalRandom.current().nextInt(bound);
    }
}
