package com.autofarm.client.combat;

import com.autofarm.client.config.AutoFarmConfig;
import com.autofarm.client.interact.BlockInteractor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.BlazeEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.WitchEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 「自动打怪」的执行体 —— 附加功能里唯一一个<b>会替玩家出手</b>的东西。
 *
 * <h2>它为什么单独成类、也单独成组</h2>
 * 附加功能面板里其他每一组都在「不改变游戏规则」的框架内：拟人化、防踢、行为真实感
 * 都只是把节奏打散。这一组不一样 —— 它直接调用原版的攻击接口，等价于常见的
 * 「杀戮光环」。所以从配置到面板都做了隔离：默认关闭、面板上用警示色、
 * 打开前还要过一次风险确认屏（见 {@code DangerConfirmScreen}）。
 *
 * <h2>两种模式</h2>
 * <ul>
 *   <li><b>非暴力（默认）</b> —— 出手前先<b>限速转头</b>把准星压到目标身上，
 *       转到位（角度容差内）才挥手，两次出手之间留着随机间隔。
 *       服务端看到的是一个「会转头、会瞄准、节奏不像节拍器」的普通玩家。
 *       代价是出手慢：转视角本身要花几 tick，所以角色仍然可能被怪打死。</li>
 *   <li><b>暴力（二级开关）</b> —— 不碰视角，只要目标在攻击距离内就直接出手。
 *       服务端看到的就变成「玩家挥了下手，某个不在准星上的生物掉了血」——
 *       这正是反作弊最敏感的特征，所以它默认关闭、且必须先开非暴力才会出现。</li>
 * </ul>
 *
 * <h2>它不去做的事</h2>
 * <ul>
 *   <li><b>不打玩家</b>：目标是玩家时一律跳过。打玩家属于 PVP 作弊，
 *       和「保护自己收菜」是两件事，这条不开放；</li>
 *   <li><b>不走位、不追怪</b>：只打「自己走进攻击距离」的目标，
 *       不会为了杀一只怪跑出田外。</li>
 * </ul>
 *
 * <h2>攻击距离与暴力程度</h2>
 * 两者的出厂值都很保守：距离 {@code attackRange}（2.9 格）卡在原版实体交互距离
 * （3.0 格）之内，暴力程度默认「轻度」。它们都能在面板上调高，但都要先过一道
 * 风险确认屏 —— 超距的攻击包服务端多半拒收，「无视间隔」的出手频率则最招反作弊。
 */
public final class KillAura {

    /**
     * 非暴力模式下「算瞄准了」的角度容差（度）。
     *
     * <p>比 {@code BlockInteractor} 挖方块的 12 度小一些：攻击的判定虽然由服务端按距离做，
     * 但视角偏太多，在别的玩家眼里就是「眼睛看着左边、手却砍右边的怪」。
     */
    private static final float AIM_TOLERANCE = 10.0f;

    /** 当前锁定的目标。冷却期间保持不动，免得在几个目标之间来回抖 */
    private static LivingEntity locked;

    /** 距离下一次可以出手还有多少 tick */
    private static int cooldown;

    private KillAura() {
    }

    /**
     * 每 tick 推进一步。
     *
     * @return true 表示「这一 tick 有可打的目标、手被它占着」——
     *         调用方（{@code FarmTaskManager}）据此暂停寻路与收割，但保留路线，
     *         等目标清掉后自动接着走。
     */
    public static boolean tick(MinecraftClient client, AutoFarmConfig config) {
        if (client == null || client.player == null || client.world == null
                || client.interactionManager == null) {
            reset();
            return false;
        }
        if (config == null || !config.autoAttack) {
            reset();
            return false;
        }

        if (cooldown > 0) {
            cooldown--;
        }

        ClientPlayerEntity player = client.player;
        double reach = Math.max(AutoFarmConfig.ATTACK_RANGE_MIN, config.attackRange);
        double rangeSq = reach * reach;

        // 先看锁定的目标还值不值得打；不行就重新挑一个最近的
        if (!isTargetable(client, config, locked, rangeSq)) {
            locked = findTarget(client, config, rangeSq);
        }
        LivingEntity target = locked;
        if (target == null) {
            return false;
        }

        // 冷却期间：手占着（所以不走位、不干别的活），但<b>不去扭视角</b> ——
        // 一直死盯着目标才是「锁头」，真人挥完一刀视角是会飘走的
        if (cooldown > 0) {
            return true;
        }

        // ---- 非暴力：先把准星转过去，转到位才出手 ----
        // 转视角走 BlockInteractor 那一套（限速 + 防踢模式的微颤），
        // 所以这一步通常会花上好几 tick，服务端看到的是一个「转头 → 瞄准 → 挥刀」的人
        if (!config.autoAttackViolent) {
            BlockInteractor.aimAt(player, target.getX(), target.getEyeY(), target.getZ());
            if (!BlockInteractor.isAimedAt(player, target.getX(), target.getEyeY(),
                    target.getZ(), AIM_TOLERANCE)) {
                return true; // 还在转，这一 tick 不出手
            }
        }

        // ---- 出手 ----
        // 走的是原版交互管理器：发一条攻击包 + 挥手动画，和真人点左键完全同一条路。
        // 暴力模式下没有前面的转头那一步，于是就成了「挥手即命中」。
        client.interactionManager.attackEntity(player, target);
        player.swingHand(Hand.MAIN_HAND);
        cooldown = attackIntervalTicks(config, target);
        return true;
    }

    /** 退出世界 / 关掉开关时清空现场 */
    public static void reset() {
        locked = null;
        cooldown = 0;
    }

    /** 现在是不是锁着目标（给状态显示用） */
    public static boolean hasTarget() {
        return locked != null;
    }

    // ================= 目标筛选 =================

    /** 这个目标现在还能不能打：活着、在范围内、而且符合当前的目标范围设置 */
    private static boolean isTargetable(MinecraftClient client, AutoFarmConfig config,
                                        LivingEntity entity, double rangeSq) {
        if (!isAllowed(client, config, entity)) {
            return false;
        }
        Vec3d eye = client.player.getEyePos();
        double d = eye.squaredDistanceTo(entity.getX(), entity.getEyeY(), entity.getZ());
        return d <= rangeSq;
    }

    /**
     * 目标范围这一关。
     *
     * <p>玩家一律排除 —— 这是刻意的：打玩家就是 PVP 作弊，
     * 与「收菜时别被打死」是两回事，所以不开放这个选项。
     * 盔甲架也排除（它是 LivingEntity，但不是「怪」）。
     */
    private static boolean isAllowed(MinecraftClient client, AutoFarmConfig config, Entity entity) {
        if (entity == null || entity == client.player || !entity.isAlive()) {
            return false;
        }
        if (entity instanceof PlayerEntity || entity instanceof ArmorStandEntity) {
            return false;
        }
        if (!(entity instanceof MobEntity)) {
            return false;
        }
        // 认的是原版那套「敌对生物」标记，所以僵尸、骷髅、苦力怕、蜘蛛，
        // 以及按同样方式实现的模组怪都算数
        return !config.attackHostileOnly || entity instanceof Monster;
    }

    /** 挑最近的合格目标 */
    private static LivingEntity findTarget(MinecraftClient client, AutoFarmConfig config,
                                           double rangeSq) {
        Vec3d eye = client.player.getEyePos();
        Box box = client.player.getBoundingBox().expand(Math.max(AutoFarmConfig.ATTACK_RANGE_MIN,
                config.attackRange));
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity entity : client.world.getEntitiesByClass(LivingEntity.class, box,
                e -> isAllowed(client, config, e))) {
            double d = eye.squaredDistanceTo(entity.getX(), entity.getEyeY(), entity.getZ());
            if (d <= rangeSq && d < bestDist) {
                bestDist = d;
                best = entity;
            }
        }
        return best;
    }

    /**
     * 下一次出手的间隔（tick）。返回 0 表示「无视间隔」——下一 tick 就能再打。
     *
     * <p>平时在 {@link AutoFarmConfig#attackIntervalMs} 上下浮动约 ±20%：
     * 每一下都精确等时，是最容易被抓的「节拍器」特征。
     *
     * <p>暴力模式会按 {@link AutoFarmConfig#attackViolence} 收紧它：
     * <ul>
     *   <li><b>轻度</b>：只有 {@link #isHighThreat 高危怪}（末影人、爆炸/远程类）
     *       无视间隔 —— 它们能在你出手前先打到你，慢一拍就是拿命换；</li>
     *   <li><b>中度</b>：上面全部，外加所有目标的间隔减半；</li>
     *   <li><b>保命</b>：上面全部，外加所有目标都无视间隔（每个 tick 出手一次）。</li>
     * </ul>
     */
    private static int attackIntervalTicks(AutoFarmConfig config, LivingEntity target) {
        boolean violent = config.autoAttackViolent;
        int level = config.attackViolence;
        int base = Math.max(AutoFarmConfig.ATTACK_INTERVAL_MIN_MS, config.attackIntervalMs);

        if (violent) {
            // 保命档：不分目标，全部按最快出手
            if (level >= AutoFarmConfig.VIOLENCE_LIFESAVER) {
                return 0;
            }
            // 高危怪：只要开着暴力就无视间隔（这就是「末影人这种手长的」那条）
            if (isHighThreat(target)) {
                return 0;
            }
            // 中度档：整体间隔减半
            if (level >= AutoFarmConfig.VIOLENCE_MEDIUM) {
                base = Math.max(AutoFarmConfig.ATTACK_INTERVAL_MIN_MS, base / 2);
            }
        }

        double jitter = 0.8 + ThreadLocalRandom.current().nextDouble() * 0.4; // 0.8 ~ 1.2
        return Math.max(1, (int) Math.round(base * jitter / 50.0)); // 毫秒 → tick（1 tick = 50ms）
    }

    /**
     * 「手长」或者说高危的生物 —— 它们能赶在你把准星转过去之前先打到你。
     *
     * <p>认的是几个原版里最典型的：末影人（长手、瞬移贴脸）、苦力怕（爆炸，一刀没打死就完了）、
     * 骷髅一系（远程射箭）、烈焰人（远程火球）、女巫（远程药水）。
     * 暴力模式下它们会被优先、且无视间隔地打掉。
     */
    private static boolean isHighThreat(Entity entity) {
        return entity instanceof EndermanEntity
                || entity instanceof CreeperEntity
                || entity instanceof AbstractSkeletonEntity
                || entity instanceof BlazeEntity
                || entity instanceof WitchEntity;
    }
}
