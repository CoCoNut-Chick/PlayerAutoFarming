package com.autofarm.client.interact;

import com.autofarm.client.config.AutoFarmConfig;
import com.autofarm.client.config.ConfigManager;
import com.autofarm.client.human.Humanizer;
import com.autofarm.client.inventory.InventoryScanner;
import com.autofarm.client.path.BlockNavigator;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * 「和世界打交道」的公共动作层：转向、挖方块、放方块。
 *
 * <p>走路（{@code PathWalker}）和收割（{@code CropHarvester}）都要做这三件事，
 * 所以抽到一层里，两边共用同一套实现 —— 尤其是挖掘，里面有个必须共用的开关
 * （见下面的 {@link #isDriving()}）。
 *
 * <h2>为什么挖方块需要一个开关</h2>
 * 我们是用 {@code updateBlockBreakingProgress} 一格一格把方块「挖」掉的，
 * 走的是原版交互管理器，和真人按住左键完全同一条路。
 *
 * <p>但原版每 tick 都有一段逻辑：如果<b>攻击键没按着</b>，就调
 * {@code cancelBlockBreaking()} 把当前破坏进度清零。我们本来就没按攻击键
 * （按键是玩家的，我们不去动它），于是每一 tick 我们攒的进度都会被下一 tick 清掉 ——
 * 结果就是「对着方块挥了半天，方块纹丝不动」，硬度 0 的作物能碎，石头永远挖不掉。
 *
 * <p>解决办法是 {@link com.autofarm.client.mixin.MinecraftClientMixin}：
 * 当这一 tick 是<b>我们</b>在挖的时候，把原版那一段整体跳过。
 * 也就是说这个类里维护的 {@link #driving} 标记，就是「这一 tick 让不让我挖」的开关：
 * <ul>
 *   <li>{@link #beginTick()} —— 每 tick 开头先置 false；</li>
 *   <li>{@link #mineTick} —— 哪次真的动了镐子，就置成 true；</li>
 *   <li>原版读它 —— 为 true 时它既不取消我们的进度，也不去挖准星指着的那格。</li>
 * </ul>
 * 不挖的 tick 它自然是 false，原版的挖掘行为一个字都不受影响。
 */
public final class BlockInteractor {

    /**
     * 挖/放之前准星允许偏多少度。
     *
     * <p>不用要求完全对准（那样会因为浮点误差一直「差一点点」），
     * 但这个容差必须够小 —— 服务端看到的方块朝向取自准星方向，
     * 偏太多就变成「面朝着别处挖方块」了。
     */
    private static final float PLACE_AIM_TOLERANCE = 12.0f;

    /** 本 tick 我们是不是自己在挖方块（给 Mixin 看的开关） */
    private static boolean driving;

    private BlockInteractor() {
    }

    // ================= 挖掘开关 =================

    /** 每 tick 开头复位，由 {@code FarmTaskManager} 在 tick 最先调用 */
    public static void beginTick() {
        driving = false;
    }

    /** 立刻关掉（退出世界、停手时用） */
    public static void clearDriving() {
        driving = false;
    }

    /** 原版的挖掘逻辑要不要给我们让路 */
    public static boolean isDriving() {
        return driving;
    }

    // ================= 转向 / 瞄准 =================

    /**
     * 每 tick 消掉「剩余角度」的比例。
     *
     * <p>转向做成<b>缓动</b>而不是匀速：每 tick 的步长 = 当前剩余角度 × 这个系数。
     * 于是角度大时步子大（转得快）、越接近目标步子越小（转得慢），最后轻轻贴合上去，
     * 既没有「每 tick 固定 18 度」那种一卡一卡的机械感，也没有「瞬间甩到位」的突兀感。
     * 0.28 大约对应 0.3~0.5 秒转完一个大角度。
     */
    private static final float TURN_EASE = 0.28f;

    /** 剩余角度小到这个值就直接贴合 —— 省掉「指数逼近永远差一点点」的尾巴 */
    private static final float TURN_SNAP = 0.4f;

    /**
     * 缓动一步：从 {@code current} 朝 {@code desired} 走一小段，返回新的角度。
     *
     * <p>用的是「按比例逼近」（指数缓动）：差得越多、这一步走得越多，差得越少、走得越少。
     * 天然满足「角度越大初始速度越大、越靠近目标越慢」，且一定单调逼近、不会冲过头。
     */
    private static float ease(float current, float desired) {
        float diff = desired - current;
        if (Math.abs(diff) <= TURN_SNAP) {
            return desired;
        }
        return current + diff * TURN_EASE;
    }

    /** 缓动一步（yaw，按 ±180 环绕取最短方向 —— 分毫不差地走最短那条路） */
    public static float easeYaw(float current, float desired) {
        float diff = MathHelper.wrapDegrees(desired - current);
        if (Math.abs(diff) <= TURN_SNAP) {
            return desired;
        }
        return current + diff * TURN_EASE;
    }

    /** 转向到某个水平坐标 —— 缓动转过去（每 tick 按剩余角度比例走一小段） */
    public static void turnTowards(ClientPlayerEntity player, double targetX, double targetZ) {
        double dx = targetX - player.getX();
        double dz = targetZ - player.getZ();
        if (dx * dx + dz * dz < 1.0e-6) {
            return;
        }
        // Minecraft 的 yaw 0 度朝 +Z，顺时针增加，所以朝向 (dx, dz) 需要的 yaw 是 atan2(-dx, dz)
        float desiredYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        player.setYaw(easeYaw(player.getYaw(), desiredYaw));
    }

    /**
     * 把准星<b>对准</b>一个世界坐标（同时转 yaw 和 pitch）。
     *
     * <p>这是「看起来像真人」的关键一步。只转 yaw 的话角色会面朝目标，
     * 但准星还停在水平线上 —— 挖方块时服务端看到的视角是「低头看着地面却报了个方块」，
     * 很扎眼。瞄准点取目标方块的<b>几何中心</b>，也就是真人会瞄的位置。
     *
     * <p>视角是<b>缓动</b>转过去的：角度越大这一步转得越多、越接近目标越慢，
     * 平滑贴合，不匀速也不瞬移。
     */
    public static void aimAt(ClientPlayerEntity player, double x, double y, double z) {
        if (player == null) {
            return;
        }
        Vec3d eye = player.getEyePos();
        double dx = x - eye.x;
        double dy = y - eye.y;
        double dz = z - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 1.0e-6 && Math.abs(dy) < 1.0e-6) {
            return;
        }

        // 防踢模式：给「该瞄哪儿」叠一点点人手晃动。幅度控制在 1.5 度以内，
        // 远小于 {@value #PLACE_AIM_TOLERANCE} 度的交互容差 —— 视角不再精确压在
        // 方块正中心，但该命中的照样命中。
        float desiredYaw = (float) Math.toDegrees(Math.atan2(-dx, dz)) + Humanizer.aimYawJitter();
        // pitch 正值代表低头：目标在下方（dy < 0）时 -atan2 得到正值
        float desiredPitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal)) + Humanizer.aimPitchJitter();

        player.setYaw(easeYaw(player.getYaw(), desiredYaw));
        player.setPitch(MathHelper.clamp(ease(player.getPitch(), desiredPitch), -90.0f, 90.0f));
    }

    /** 把准星对准某个方块的中心 */
    public static void aimAt(ClientPlayerEntity player, BlockPos pos) {
        if (pos != null) {
            aimAt(player, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        }
    }

    /**
     * 准星已经压在目标上了吗（差角小于 {@code tolerance} 度）。
     *
     * <p>挖和放都先过这一关：视角还没转到位就先不动手。否则会出现
     * 「头还在甩，方块先被挖了」，看着就是机器人在操作。
     *
     * <p>目标离眼睛极近时（贴脸）直接算「已经对准」—— 那种情况算角度没有意义，
     * 不特判会让它原地一直转。
     */
    public static boolean isAimedAt(ClientPlayerEntity player, double x, double y, double z,
                                    float tolerance) {
        if (player == null) {
            return false;
        }
        Vec3d eye = player.getEyePos();
        double dx = x - eye.x;
        double dy = y - eye.y;
        double dz = z - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 0.35 && Math.abs(dy) < 0.35) {
            return true;
        }
        if (horizontal < 1.0e-6 && Math.abs(dy) < 1.0e-6) {
            return true;
        }
        float desiredYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float desiredPitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        return Math.abs(MathHelper.wrapDegrees(desiredYaw - player.getYaw())) <= tolerance
                && Math.abs(desiredPitch - player.getPitch()) <= tolerance;
    }

    /** 准星已经压在某个方块的中心上了吗 */
    public static boolean isAimedAt(ClientPlayerEntity player, BlockPos pos, float tolerance) {
        return pos != null && isAimedAt(player, pos.getX() + 0.5, pos.getY() + 0.5,
                pos.getZ() + 0.5, tolerance);
    }

    /** 玩家该朝向方块的哪一面（服务端要拿它校验，随便给一个会掉线） */
    public static Direction faceTowards(ClientPlayerEntity player, BlockPos pos) {
        BlockPos feet = player.getBlockPos();
        int dx = Integer.signum(pos.getX() - feet.getX());
        int dy = Integer.signum(pos.getY() - feet.getY());
        int dz = Integer.signum(pos.getZ() - feet.getZ());
        Direction dir = Direction.fromVector(-dx, -dy, -dz);
        return dir == null ? Direction.UP : dir;
    }

    // ================= 挖一格 =================

    /**
     * 挖一 tick。进度由原版累积，所以我们每 tick 调一次就行。
     *
     * <p>两件事：先看要不要换手，再把这一 tick 的破坏进度发出去。
     */
    public static void mineTick(MinecraftClient client, BlockPos pos) {
        ClientPlayerEntity player = client == null ? null : client.player;
        if (player == null || client.world == null || client.interactionManager == null) {
            return;
        }

        // 换手要抢在开挖之前：挖到一半再换，原版会把进度清零重来。
        // 所以这里切完就返回，等下一 tick 才真正动镐子。
        if (ConfigManager.get().autoSelectTool && switchToBestTool(client, player, pos)) {
            return;
        }

        client.interactionManager.updateBlockBreakingProgress(pos, faceTowards(player, pos));
        player.swingHand(Hand.MAIN_HAND);

        // 告诉 Mixin：这一 tick 的破坏是我们发起的，别让原版把它清掉
        driving = true;
    }

    /**
     * 把快捷栏切到「挖这个方块最合适」的那一格。
     *
     * <p>只改 {@code selectedSlot}：原版客户端每 tick 都会发现手换过了并自动同步给服务端，
     * 所以不用自己发包，服务端看到的就是一次普通的换手。
     *
     * @return true 表示刚刚换过手（这一 tick 先别挖）
     */
    private static boolean switchToBestTool(MinecraftClient client, ClientPlayerEntity player, BlockPos pos) {
        BlockState state = client.world.getBlockState(pos);
        int slot = InventoryScanner.findBestToolSlot(player, state);
        if (slot < 0) {
            return false;
        }
        player.getInventory().selectedSlot = slot;
        return true;
    }

    // ================= 放一块 =================

    /**
     * 用快捷栏第 {@code hotbarSlot} 格的物品，在 {@code placePos} 放下去。
     *
     * <p>三步：找一面能「贴着放」的邻居 → 把要用的东西换到主手 → 构造命中结果发出去。
     * 发出去的是 {@code interactBlock}，和真人右键完全同一条路，
     * 服务端看到的就只是一个普通玩家在放方块。
     *
     * @return 有没有真的发出这次交互
     */
    public static boolean placeWith(MinecraftClient client, BlockPos placePos, int hotbarSlot) {
        ClientPlayerEntity player = client == null ? null : client.player;
        if (player == null || client.world == null || client.interactionManager == null) {
            return false;
        }
        if (hotbarSlot < 0) {
            return false;
        }

        // 1. 方块是「贴着某个实心方块」放上去的，先找一面能贴的
        Direction attach = null;
        for (Direction dir : Direction.values()) {
            BlockPos neighbour = placePos.offset(dir);
            if (!BlockNavigator.isPassableAt(client.world, neighbour.getX(), neighbour.getY(), neighbour.getZ())) {
                attach = dir;
                break;
            }
        }
        if (attach == null) {
            return false;
        }

        // 2. 把要用的东西换到主手。走原版换位包（selectHotbarSlot），
        // 而不是直接改 selectedSlot —— 那样本地换了、服务端还以为你拿着旧东西。
        if (!InventoryScanner.selectHotbarSlot(client, player, hotbarSlot)) {
            return false;
        }

        // 3. 构造命中结果：击中的是那个邻居方块，击中的面朝向要放置的位置
        BlockPos neighbour = placePos.offset(attach);
        Direction side = attach.getOpposite();
        Vec3d hitPos = new Vec3d(
                neighbour.getX() + 0.5 + side.getOffsetX() * 0.5,
                neighbour.getY() + 0.5 + side.getOffsetY() * 0.5,
                neighbour.getZ() + 0.5 + side.getOffsetZ() * 0.5);

        // 4. 抬手之前先把准星压到那一面上。差得太远就先不发交互 ——
        // 调用方是每隔几 tick 试一次的，下一轮视角就更接近了。
        // 极效率模式例外：放置也无视准星，直接构造合法命中发出去（代价是面朝向
        // 可能与实际视角不符，服务端对放置的朝向校验比破坏宽松）。
        if (ConfigManager.get().aimMode != AutoFarmConfig.AIM_EXTREME) {
            aimAt(player, hitPos.x, hitPos.y, hitPos.z);
            if (!isAimedAt(player, hitPos.x, hitPos.y, hitPos.z, PLACE_AIM_TOLERANCE)) {
                return false;
            }
        }

        BlockHitResult hit = new BlockHitResult(hitPos, side, neighbour, false);
        client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
        player.swingHand(Hand.MAIN_HAND);
        return true;
    }

    /**
     * 右键一个方块（开箱子、拉杆这类）。
     *
     * <p>和 {@link #placeWith} 走同一个入口 {@code interactBlock}，
     * 区别是不需要挑邻居、也不需要换手 —— 直接朝着方块中心发一次交互。
     * 客户端上的 {@code onUse} 会立刻返回成功，所以就算手里拿着方块也不会误放。
     *
     * @return 有没有真的发出这次交互
     */
    public static boolean useBlock(MinecraftClient client, BlockPos pos) {
        ClientPlayerEntity player = client == null ? null : client.player;
        if (player == null || client.interactionManager == null || pos == null) {
            return false;
        }
        Vec3d center = Vec3d.ofCenter(pos);
        Direction side = faceTowards(player, pos);
        // 极效率模式：右键也无视准星，直接朝方块中心发交互。
        if (ConfigManager.get().aimMode != AutoFarmConfig.AIM_EXTREME) {
            aimAt(player, center.x, center.y, center.z);
            if (!isAimedAt(player, center.x, center.y, center.z, PLACE_AIM_TOLERANCE)) {
                return false;
            }
        }
        client.interactionManager.interactBlock(player, Hand.MAIN_HAND,
                new BlockHitResult(center, side, pos, false));
        player.swingHand(Hand.MAIN_HAND);
        return true;
    }

    /** 视线离目标超过这个距离就没必要再按交互键了（原版够得着是 4.5 格） */
    public static boolean isWithinReach(ClientPlayerEntity player, BlockPos pos, double reach) {
        return player != null && pos != null
                && player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos)) <= reach * reach;
    }
}
