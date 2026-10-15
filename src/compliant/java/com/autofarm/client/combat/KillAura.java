package com.autofarm.client.combat;

import com.autofarm.client.config.AutoFarmConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

/**
 * 「自动打怪」在<b>合规版</b>里的空实现。
 *
 * <p>Modrinth《Content Rules》第 3 条禁止客户端模组具备
 * 「自动或辅助 PvP 战斗」与「自动瞄准 / 辅助瞄准」能力。完整版这一组功能
 * 虽然默认关闭、且明确不打玩家，但它会替玩家转向并出手攻击生物，
 * 属于该条款覆盖的范围 —— 因此合规版把真正的 {@code KillAura}
 * 整个排除掉，用这份什么都不做的实现顶上。
 *
 * <p>保留同样的静态方法签名，是为了让 {@code FarmTaskManager} 的调用点
 * 一行都不用改：{@link #tick} 恒返回 false（不接管战斗），
 * {@link #focus} 与 {@link #reset} 都是空操作。
 */
public final class KillAura {
    private KillAura() {
    }

    /** 合规版不接管战斗，恒表示「本 tick 没有出手」。 */
    public static boolean tick(Minecraft client, AutoFarmConfig config) {
        return false;
    }

    /** 合规版没有「优先还手」这回事。 */
    public static void focus(LivingEntity target) {
        // 空实现
    }

    public static void reset() {
        // 空实现
    }
}
