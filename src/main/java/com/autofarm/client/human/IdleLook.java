package com.autofarm.client.human;

import com.autofarm.client.config.AutoFarmConfig;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * 「空闲时环顾四周」：没事干的时候，让视角像真人一样慢慢游走。
 *
 * <h2>为什么「一动不动」反而最假</h2>
 * 作物收完了、或者正在休息，模组会把角色定在原地等下一批成熟。
 * 一个真人在田边发呆时，头其实一直在缓慢地转 —— 看看天、看看另一头、又低下来。
 * 而把视准星钉死在同一个角度、连一点小幅转头都没有的角色，静态得太规整。
 * 这个类就是补上「发呆时的那种小动作」。
 *
 * <h2>两个关键设计</h2>
 * <ol>
 *   <li><b>慢</b> —— 每 tick 最多转约 1.2 度，比走路的转向慢一个数量级。
 *       它是「扫一眼」，不是「甩头」；每 1.5~5.5 秒才换一个新的注视方向。</li>
 *   <li><b>会让开</b> —— 每次调用都会拿玩家此刻的视角和「上一次我们自己设成的值」比对。
 *       只要你动了鼠标，两者立刻对不上，它这一 tick 就什么都不做、并把自己重新同步回来。
 *       换句话说：<b>你自己在操作的时候，它绝不会跟你抢视角。</b></li>
 * </ol>
 *
 * <h2>转向同样走缓动</h2>
 * 和挖 / 种 / 撒骨粉那套（{@code BlockInteractor}）一样，这里的视角也是「按剩余角度的
 * 比例一步一步逼近」：刚换目标时一步大一点、越接近注视点越轻，最后轻轻贴合上去 ——
 * 而不是「每 tick 固定转 1.2 度」那种匀速直线。区别只在<b>系数小得多</b>：
 * 那边是「转身」，这边只是「扫一眼」。
 *
 * <p>它和 {@code Humanizer} 一样，<b>不改任何游戏规则</b>：只是调玩家的 yaw / pitch，
 * 不发包、不改速度、不绕碰撞。
 */
public final class IdleLook {

    /** 每 tick 消掉「剩余角度」的比例（yaw）。刻意压得很小 —— 这是「扫一眼」不是「甩头」 */
    private static final float YAW_EASE = 0.035f;
    /** 俯仰的缓动比例，稍稍快一点（抬头低头本来就更轻） */
    private static final float PITCH_EASE = 0.05f;
    /** 缓动起步那一下也不许超过这个速度（度/tick），保证慢节奏 */
    private static final float MAX_YAW_PER_TICK = 1.2f;
    /** 每 tick 最多抬/低头多少度（pitch） */
    private static final float MAX_PITCH_PER_TICK = 0.8f;
    /** 剩余角度小到这个值就直接贴合，省掉「永远差一点点」的尾巴 */
    private static final float SNAP = 0.15f;

    /** 玩家视角和我们上次设的值差超过这个度数，就认为「玩家自己动手了」，让开 */
    private static final float USER_MOVE_EPSILON = 1.5f;

    /** 我们上一次设成的 yaw / pitch，用来检测玩家是否自己动过鼠标 */
    private static float lastYaw;
    private static float lastPitch;
    /** 上一次调用是不是真的在环顾（第一次进入时要先同步一次，不比差） */
    private static boolean active;

    /** 当前注视方向的 yaw / pitch 目标 */
    private static float targetYaw;
    private static float targetPitch;
    /** 还有几 tick 换一个注视方向 */
    private static int hold;

    private IdleLook() {
    }

    /**
     * 每 tick 推进一次。
     *
     * @param shouldLook 这一 tick 该不该环顾。由 {@code FarmTaskManager} 判断：
     *                   自动收割开着、没在走/收/补给、也没因为危险而停机的时候才为 true。
     */
    public static void tick(Minecraft client, AutoFarmConfig config, boolean shouldLook) {
        LocalPlayer player = client == null ? null : client.player;
        if (player == null || config == null || !config.idleLookAround || !shouldLook) {
            active = false;
            return;
        }

        // 玩家自己动过鼠标？→ 让开，并把同步点挪到他当前的角度，
        // 这样下一 tick 如果他不再动，我们才可以接着环顾。
        if (active
                && (degDiff(player.getYRot(), lastYaw) > USER_MOVE_EPSILON
                || Math.abs(player.getXRot() - lastPitch) > USER_MOVE_EPSILON)) {
            active = false;
            hold = 0;
            return;
        }

        active = true;

        // 低频换一个注视方向：相对当前角度左右各摆一点，俯仰回到舒服的范围
        if (--hold <= 0) {
            targetYaw = player.getYRot() + (rnd() * 2f - 1f) * 35f;
            targetPitch = -4f + rnd() * 16f; // 略低头 ~ 略抬头
            hold = 30 + rndInt(80);          // 1.5 ~ 5.5 秒换一次
        }

        float yawStep = easeStep(Mth.wrapDegrees(targetYaw - player.getYRot()),
                YAW_EASE, MAX_YAW_PER_TICK);
        float pitchStep = easeStep(targetPitch - player.getXRot(),
                PITCH_EASE, MAX_PITCH_PER_TICK);

        player.setYRot(player.getYRot() + yawStep);
        player.setXRot(Mth.clamp(player.getXRot() + pitchStep, -90f, 90f));

        // 记下「我们设成的值」，下一 tick 用它判断玩家有没有插手
        lastYaw = player.getYRot();
        lastPitch = player.getXRot();
    }

    /** 退出世界 / 关掉开关时归零 */
    public static void reset() {
        active = false;
        hold = 0;
    }

    /**
     * 缓动一步：步长 = 剩余角度 × 系数，再压一个上限。
     *
     * <p>剩余多的时候一步跨得多、越接近注视点步长越小，最后平滑咬合 ——
     * 和 {@code BlockInteractor} 那套是同一个形状，只是系数更小、更慢。
     */
    private static float easeStep(float diff, float ease, float max) {
        if (Math.abs(diff) <= SNAP) {
            return diff;
        }
        return Mth.clamp(diff * ease, -max, max);
    }

    private static float degDiff(float a, float b) {
        return Math.abs(Mth.wrapDegrees(a - b));
    }

    private static float rnd() {
        return ThreadLocalRandom.current().nextFloat();
    }

    private static int rndInt(int bound) {
        return bound <= 0 ? 0 : ThreadLocalRandom.current().nextInt(bound);
    }
}
