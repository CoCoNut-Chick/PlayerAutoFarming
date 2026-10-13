package com.autofarm.client.human;

import com.autofarm.client.compat.CameraCompat;
import com.autofarm.client.config.AutoFarmConfig;
import com.autofarm.client.interact.BlockInteractor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

/**
 * 「环境自救」：把角色从两种跟收割无关、但会要命的环境里捞出来。
 *
 * <h2>它管哪两件事</h2>
 * <ol>
 *   <li><b>溺水</b> —— 角色潜到水下、氧气开始见底时，自动上浮到水面换气。
 *       收割任务本身不会主动潜水，但田边可能靠着河、或者被水冲进坑里；
 *       人一旦在水下没了氧气就会持续掉血，而那时候模组往往还在低头干活。</li>
 *   <li><b>着火</b> —— 身上着火、附近又正好有水的时候，朝最近的水跑过去。
 *       着火期间会持续掉血，扑进水里是最快的自救方式。</li>
 * </ol>
 *
 * <h2>怎么动角色</h2>
 * 和 {@link com.autofarm.client.path.PathWalker} 走<b>同一条通道</b>
 * （见 {@code ClientPlayerInputMixin}）：在玩家移动意图那一层写入，
 * 玩家的真实按键和自由视角模组的摄像机操作一个字都不改。
 * 两者不会打架 —— 触发自救时任务大脑会先把寻路停掉（见 {@code FarmTaskManager}），
 * 所以同一时刻只有一边在接管。
 *
 * <p>它<b>不改任何游戏规则</b>：不发额外的包、不改速度上限、不绕碰撞，
 * 只是替玩家按住「跳跃」或者朝某个方向按住「前进」而已。
 */
public final class RescueHelper {

    /** 找水源的最大水平半径（格）。太远的不去 —— 跑过去的时间里人早烧没了 */
    private static final int WATER_SEARCH_RADIUS = 8;

    /** 氧气低于最大值的这个比例就认为「快憋不住了」，开始上浮 */
    private static final float AIR_LOW_RATIO = 0.75f;

    /** 当前的自救类型 */
    public enum Mode {
        /** 没在自救 */
        NONE,
        /** 正在上浮换气 */
        SURFACING,
        /** 正在跑向水源灭火 */
        FIRE
    }

    private static Mode mode = Mode.NONE;
    private static String notice;
    /** 本 tick 想要的移动意图，由 {@link #tick} 算好、由 {@link #applyInputOverride} 消费 */
    private static boolean wantForward;
    private static boolean wantJump;

    private RescueHelper() {
    }

    /**
     * 每 tick 推进一次。
     *
     * @return true 表示「这一 tick 正在自救、要接管移动」——
     *         调用方据此把手上的收割/寻路全部放下。
     */
    public static boolean tick(Minecraft client, AutoFarmConfig config) {
        mode = Mode.NONE;
        notice = null;
        wantForward = false;
        wantJump = false;

        LocalPlayer player = client == null ? null : client.player;
        if (player == null || client.level == null || config == null) {
            return false;
        }
        // 「自动收割」或「纯拾取模式」任一开着都算在工作 —— 保命的事两种模式下都要做
        boolean working = config.enabled || config.pickupOnlyMode;
        if (!working || !config.selfRescueEnabled || player.isSpectator()) {
            return false;
        }
        // 自由视角 + 开了「自由视角时停机」时不接管 —— 否则屏幕上身体不动，
        // 却在那边自己上浮 / 朝水源跑，很扎眼。返回 false 并保持「没在自救」，
        // 控制权不落在我们手上。（关着那一项时照常自救，见 CameraCompat。）
        if (CameraCompat.pauseForFreecam(config)) {
            return false;
        }

        // ---- 1. 溺水：潜在水里、氧气见底 → 上浮 ----
        int maxAir = player.getMaxAirSupply();
        if (player.isUnderWater() || player.getAirSupply() < maxAir * AIR_LOW_RATIO) {
            mode = Mode.SURFACING;
            notice = "autofarm.notice.rescue_drowning";
            wantJump = true; // 水里按住跳跃键 = 上浮
            return true;
        }

        // ---- 2. 着火：身上着火、附近有水 → 朝水跑 ----
        if (player.isOnFire() && !player.isInWater()) {
            BlockPos water = findNearestWater(client, player.blockPosition());
            if (water != null) {
                mode = Mode.FIRE;
                notice = "autofarm.notice.rescue_fire";
                BlockInteractor.turnTowards(player, water.getX() + 0.5, water.getZ() + 0.5);
                wantForward = true;
                // 水比脚高一点就顺带跳一下，免得卡在岸边
                wantJump = water.getY() > player.getY() + 0.4;
                return true;
            }
        }

        return false;
    }

    /**
     * 由 {@code KeyboardInputMixin} 调用（排在 PathWalker 之后）。
     * 没有在自救时一个字段都不碰。
     */
    /**
     * 把自救的动作写进玩家的移动意图，返回有没有真的接管。
     *
     * @see com.autofarm.client.path.PathWalker#applyInputOverride 同一套通道，原因见那里
     */
    public static boolean applyInputOverride(LocalPlayer player) {
        if (player == null || mode == Mode.NONE) {
            return false;
        }
        player.xxa = 0.0f;
        player.zza = wantForward ? 1.0f : 0.0f;
        player.setJumping(wantJump);
        return true;
    }

    /** 自救时该播报的那句话（翻译键）；不在自救时返回 null */
    public static String notice() {
        return notice;
    }

    /** 退出世界 / 关掉开关时归零 */
    public static void reset() {
        mode = Mode.NONE;
        notice = null;
        wantForward = false;
        wantJump = false;
    }

    // ================= 内部 =================

    /**
     * 在附近找一格水。
     *
     * <p>从脚下往上一层开始、按「一层一层往外扩」的顺序找：先看脚边、再看远一圈，
     * 找到最近的 <b>水</b>（只认静态水，流动的也算水，但不认岩浆）。
     */
    private static BlockPos findNearestWater(Minecraft client, BlockPos origin) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dy = -1; dy <= 2; dy++) {
            for (int dx = -WATER_SEARCH_RADIUS; dx <= WATER_SEARCH_RADIUS; dx++) {
                for (int dz = -WATER_SEARCH_RADIUS; dz <= WATER_SEARCH_RADIUS; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (!isWater(client.level.getBlockState(pos))) {
                        continue;
                    }
                    double d = origin.distSqr(pos);
                    if (d < bestDist) {
                        bestDist = d;
                        best = pos.immutable();
                    }
                }
            }
        }
        return best;
    }

    /** 这一格是不是水（认水，不认岩浆） */
    private static boolean isWater(BlockState state) {
        return state.getFluidState().getType() == Fluids.WATER;
    }
}
