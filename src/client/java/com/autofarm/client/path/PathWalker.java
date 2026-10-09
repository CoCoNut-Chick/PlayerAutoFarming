package com.autofarm.client.path;

import com.autofarm.client.config.AutoFarmConfig;
import com.autofarm.client.config.ConfigManager;
import com.autofarm.client.interact.BlockInteractor;
import com.autofarm.client.inventory.AutoEater;
import com.autofarm.client.inventory.InventoryScanner;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 「按路线把角色走过去」的执行器。
 *
 * <h2>它是怎么走的</h2>
 * {@link PathPlanner} 决定<b>先去哪个作物</b>，{@link PathFinder} 决定
 * <b>从当前位置怎么过去</b>（包括要不要挖、要不要垫），本类夹在中间负责执行，
 * 一次处理一段：
 *
 * <ol>
 *   <li>手上没有可走的路了 → 朝当前目标要一条 A* 路径；</li>
 *   <li>每 tick 看路段里的下一个落脚点：如果需要先挖开或先垫一块，就停下手做；</li>
 *   <li>路通了就朝它转视角、往前走；</li>
 *   <li>路段走完 = 目标到达 → <b>停在原地</b>，等任务大脑把这一棵收掉；
 *       它处理完调 {@link #releaseTarget()}，才继续下一段。</li>
 * </ol>
 *
 * <h2>怎么让角色「自己走」</h2>
 * Minecraft 的客户端玩家每 tick 会先读一次输入（{@code KeyboardInput.tick}
 * 把按键状态抄进 {@code Input} 的字段），然后按这份输入移动。
 * 所以想让角色自动走，就必须在「读输入」之后动手。
 *
 * <p>这里不用「模拟按键」那种写法（直接去按 W 键的绑定），原因是它会和真人打架：
 * 绑定是一个全局状态，我们把它设成按下，玩家自己按的那个 W 就被覆盖了。
 *
 * <p>改用 {@code KeyboardInput.tick} 末尾的一次注入（见 {@code KeyboardInputMixin}）：
 * 原版抄完按键状态后，如果我们正在接管，就把抄出来的结果替换成我们想要的。
 * 这样玩家手上的键位状态<b>一个字都没被改</b>，我们一停，控制权立刻还回去。
 *
 * <h2>挖掘和放置</h2>
 * 两者都走原版的交互管理器，和真人操作完全同一条路：
 * 挖是 {@code updateBlockBreakingProgress}（每 tick 调一次，进度由原版累积），
 * 放是 {@code interactBlock}（要先把手里的东西换成方块，再构造一个命中结果）。
 * 服务端看到的就只是一个普通玩家在挖矿和放方块，不会因为「不是人操作」被拦。
 */
public final class PathWalker {

    /** 到达一个落脚点的判定半径（格）。落脚点间距就 1 格左右，太大太小都会出问题 */
    private static final double WAYPOINT_RADIUS = 0.85;

    /** 到达落脚点的竖直容差（格） */
    private static final double WAYPOINT_HEIGHT = 1.5;

    /** 每 tick 最多转多少度 */
    private static final float MAX_TURN_PER_TICK = 18.0f;

    /** 朝向和目标方向差小于这个角度，才敢往前走 */
    private static final float FORWARD_ANGLE = 50.0f;

    /**
     * 朝向和目标方向差小于这个角度，才敢开疾跑。
     *
     * <p>比 {@link #FORWARD_ANGLE} 严得多：疾跑带着惯性，大角度转弯时跑着会甩出去，
     * 稳一点反而更快到。
     */
    private static final float SPRINT_ALIGN_ANGLE = 30.0f;

    /** 位置变化小于这个值就认为「这一 tick 没动」（格） */
    private static final double STUCK_EPSILON = 0.004;

    /** 挖/垫之前准星允许偏目标中心多少度（比内部放置的容差更严，避免互相等） */
    private static final float WORK_AIM_TOLERANCE = 8.0f;

    /** 卡住这么多 tick 之后开始尝试跳跃 */
    private static final int STUCK_JUMP_TICKS = 10;

    /** 卡住这么多 tick 就放弃当前目标，免得整条路线堵在一个点上 */
    private static final int STUCK_GIVE_UP_TICKS = 70;

    /** 一次挖/垫最多持续多少 tick，超了就放弃这个目标（别卡在挖不动的方块上） */
    private static final int ACTION_TIMEOUT_TICKS = 240;

    /** 放方块的重试间隔：每几 tick 试一次，失败也不至于每帧都发一次交互包 */
    private static final int PLACE_RETRY_INTERVAL = 4;

    /** 已经「处理完」的点（走到了，或者判定过不去放弃了） */
    private static final ArrayDeque<BlockPos> resolved = new ArrayDeque<>();

    /**
     * 「要走到它自己那一格」的目标。
     *
     * <p>平时走到一个目标跟前，是站在<b>旁边</b>看着它干活（作物得抬头挖、对着种），
     * 所以 {@link BlockNavigator#findViewSpot} 会挑一个能看见它的落脚点。
     * 但有一类目标不是「要看的东西」，而是<b>要去碰到的东西</b> ——
     * 掉在地上的掉落物就是这样：人得站到它那一格上，原版才会把它吸进背包。
     * 农场大脑每次规划路线前把这类目标丢进来，挑落脚点时就会直接走它本身。
     */
    private static final Set<BlockPos> exactTargets = new HashSet<>();

    private static PathPlanner.Plan plan;
    private static int index = -1;

    /** 当前这一段 A* 路径（从玩家附近一直连到当前目标） */
    private static List<PathStep> segment = Collections.emptyList();
    /** 走到 segment 的第几个落脚点了 */
    private static int waypoint;

    /** 当前这个挖/垫动作已经持续了多少 tick */
    private static int actionTicks;

    private static Vec3d lastPos;
    private static int stuckTicks;

    /** 本 tick 的输入意图，由 {@link #tick} 算好、由 {@link #applyInputOverride} 消费 */
    private static boolean wantForward;
    private static boolean wantJump;

    /**
     * 已经站到当前目标跟前、正在等调用方处理（收割/补种）。
     *
     * <p>为什么要停在这里而不是直接去下一个：走路和「到站之后干什么」是两件事。
     * 本类只负责把人送到，送到之后由 {@code FarmTaskManager} 接管去挖和种，
     * 办完了再调 {@link #releaseTarget()} 放行。
     */
    private static boolean holding;

    /** 玩家打开了什么界面时挂起：这时候千万别让角色继续走 */
    private static boolean paused;

    private PathWalker() {
    }

    // ================= 对外接口 =================

    /** 开始执行一条路线 */
    public static void start(PathPlanner.Plan newPlan) {
        plan = newPlan != null && newPlan.size() > 0 ? newPlan : null;
        index = plan == null ? -1 : 0;
        clearSegment();
        lastPos = null;
        stuckTicks = 0;
        wantForward = false;
        wantJump = false;
        holding = false;
        resolved.clear();
    }

    /** 停下并交还控制权 */
    public static void stop() {
        plan = null;
        index = -1;
        clearSegment();
        lastPos = null;
        stuckTicks = 0;
        wantForward = false;
        wantJump = false;
        holding = false;
        exactTargets.clear();
        // 注意：resolved 里可能有还没被取走的点，留着让调用方取完再清
    }

    public static boolean isActive() {
        return plan != null && index >= 0 && index < plan.size();
    }

    /**
     * 已经站到目标跟前，正在等调用方干活（收割/补种）。
     *
     * <p>这时候 {@link #tick} 什么都不做，角色原地站着不动 ——
     * 这正是「走到作物跟前会停下来，而不是直接跑去下一个」的实现方式。
     */
    public static boolean isHolding() {
        return holding;
    }

    /** 正被「握着」的那个目标；没握着的时候返回 null */
    public static BlockPos getHeldTarget() {
        return holding ? getCurrentTarget() : null;
    }

    /**
     * 活干完了，放行去下一个目标。
     *
     * <p>调用方（任务大脑）处理完当前目标后必须调它，否则角色会一直站在原地。
     */
    public static void releaseTarget() {
        if (!holding) {
            return;
        }
        holding = false;
        advance();
    }

    /** 打开界面等需要挂起的情况 */
    public static void setPaused(boolean value) {
        paused = value;
    }

    /**
     * 声明「这些目标要精确走到它本身那一格」（掉落物拾取用，见 {@link #exactTargets}）。
     *
     * <p>路线每次重新规划时由调用方重新设置一遍，所以传空集合就等于「这一趟全是普通目标」。
     */
    public static void setExactTargets(Collection<BlockPos> targets) {
        exactTargets.clear();
        if (targets != null) {
            exactTargets.addAll(targets);
        }
    }

    public static BlockPos getCurrentTarget() {
        return isActive() ? plan.order().get(index) : null;
    }

    /** 这条路线上总共有多少个目标 */
    public static int getTotalCount() {
        return plan == null ? 0 : plan.size();
    }

    /** 已经走完（或放弃）了几个 */
    public static int getDoneCount() {
        return plan == null ? 0 : Math.max(0, index);
    }

    /** 从当前位置起，还剩下多少格要走（直线估算 + 挖/垫的额外开销） */
    public static double getRemainingDistance() {
        if (!isActive()) {
            return 0.0;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client == null ? null : client.player;
        double px = player == null ? 0.0 : player.getX();
        double py = player == null ? 0.0 : player.getY();
        double pz = player == null ? 0.0 : player.getZ();

        double sum = 0.0;
        for (PathStep step : segment) {
            sum += straightDistance(px, py, pz, step.pos()) + step.extraCost();
            px = step.pos().getX() + 0.5;
            py = step.pos().getY();
            pz = step.pos().getZ() + 0.5;
        }
        for (int i = index; i < plan.size(); i++) {
            BlockPos pos = plan.order().get(i);
            sum += straightDistance(px, py, pz, pos);
            px = pos.getX() + 0.5;
            py = pos.getY();
            pz = pos.getZ() + 0.5;
        }
        return sum;
    }

    /** 还没走到的作物目标，按访问顺序 */
    public static List<BlockPos> getRoute() {
        if (!isActive()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(plan.order().subList(index, plan.size()));
    }

    /**
     * 当前这一段的 A* 路径（从最近的落脚点一直到当前目标）。
     * 渲染层拿它画线——它才是角色真正会走出来的轨迹。
     */
    public static List<PathStep> getSegment() {
        if (segment.isEmpty() || waypoint >= segment.size()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(segment.subList(waypoint, segment.size()));
    }

    /**
     * 取一个「已经不用再管」的点。
     *
     * <p>包含两种：真的走到了的，以及判定走不到而放弃的。
     * 调用方（任务大脑）会把它们都记进「本次已处理」，
     * 否则下一轮扫描又会规划到同一个到不了的点上，来回死循环。
     */
    public static BlockPos pollResolved() {
        return resolved.poll();
    }

    // ================= 每 tick 驱动 =================

    public static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client == null ? null : client.player;
        if (plan == null || player == null || client.world == null) {
            if (plan != null) {
                stop();
            }
            return;
        }

        // 正在吃东西就站住等它吃完 —— 原版使用物品时移动速度只剩两成，
        // 边走边啃既慢，又容易把这口吃了一半就打断。
        // 这里用的是 isHandlingHunger() 而不是 isEating()：吃完那口之后的冷却里，
        // 只要还跑不动、快捷栏里也还有吃的，就别急着走（否则会走一截停一截地抖）。
        if (paused || player.isSpectator() || AutoEater.isHandlingHunger()) {
            wantForward = false;
            wantJump = false;
            return;
        }

        // 已经站到目标跟前，正等调用方收割：原地站着，别往下一个目标跑
        if (holding) {
            wantForward = false;
            wantJump = false;
            return;
        }

        // ---- 手上没有可走的路，就朝当前目标要一段 ----
        if (!ensureSegment(client, player)) {
            wantForward = false;
            wantJump = false;
            return;
        }
        if (!isActive() || waypoint >= segment.size()) {
            wantForward = false;
            wantJump = false;
            return;
        }

        PathStep step = segment.get(waypoint);

        // ---- 这一步要不要先动一下世界？ ----
        if (step.needsWork()) {
            wantForward = false;
            wantJump = false;
            if (workOnStep(client, player, step)) {
                // 还在挖/还在放，这一 tick 不走路
                if (++actionTicks > ACTION_TIMEOUT_TICKS) {
                    abandonCurrentTarget();
                }
                return;
            }
            // 活儿干完了，这一步退化成普通的「走过去」
        }

        actionTicks = 0;

        // ---- 到了就换下一个落脚点 ----
        if (hasReached(player, step.pos())) {
            waypoint++;
            if (waypoint >= segment.size()) {
                finishCurrentTarget();
                return;
            }
            step = segment.get(waypoint);
        }

        BlockPos target = step.pos();
        double dx = target.getX() + 0.5 - player.getX();
        double dz = target.getZ() + 0.5 - player.getZ();

        // ---- 转向。Minecraft 的 yaw 0 度朝 +Z，顺时针增加，
        //      所以朝向 (dx, dz) 需要的 yaw 是 atan2(-dx, dz) ----
        float desiredYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float diff = MathHelper.wrapDegrees(desiredYaw - player.getYaw());
        player.setYaw(player.getYaw() + MathHelper.clamp(diff, -MAX_TURN_PER_TICK, MAX_TURN_PER_TICK));

        // ---- 卡住检测（只在「真的在往前走」时算，否则挖方块时会误判成卡住） ----
        Vec3d pos = player.getPos();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (lastPos != null && Math.abs(diff) < FORWARD_ANGLE) {
            double moved = Math.sqrt(Math.pow(pos.x - lastPos.x, 2) + Math.pow(pos.z - lastPos.z, 2));
            if (moved < STUCK_EPSILON && horizontal > 0.6) {
                stuckTicks++;
            } else {
                stuckTicks = 0;
            }
        }
        lastPos = pos;

        if (stuckTicks >= STUCK_GIVE_UP_TICKS) {
            abandonCurrentTarget();
            return;
        }

        // ---- 输出意图：转得差不多了才往前走 ----
        boolean facing = Math.abs(diff) < FORWARD_ANGLE;
        boolean needJump = target.getY() > player.getY() + 0.4;
        wantForward = facing;
        // 下一个落脚点比当前高，就是要上台阶 —— 得跳一下
        wantJump = (facing && needJump) || stuckTicks >= STUCK_JUMP_TICKS;

        // ---- 疾跑：赶路时替玩家按住 Ctrl ----
        applySprint(player, facing && !needJump && Math.abs(diff) < SPRINT_ALIGN_ANGLE);
    }

    /**
     * 「锁定疾跑」就是替玩家一直按住 Ctrl。
     *
     * <p>它只在<b>真的要往前迈、而且方向已经朝得差不多</b>的时候才被点上，三种情况会主动松开：
     * <ol>
     *   <li><b>要跳上台阶</b> —— 疾跑跳会往前冲出两三格，本来只想上一格，结果人飞出去了；</li>
     *   <li><b>方向还没转正</b> —— 疾跑有惯性，大角度转弯时跑着会甩出一条弧线，不如先走稳；</li>
     *   <li><b>原版自己不允许</b> —— 饥饿值不够、正举着东西、正在潜行。这三种情况原版每 tick
     *       都会把疾跑关掉，我们要是还硬设 true 就成了「一人按一次」的抽搐，索性先让开。</li>
     * </ol>
     *
     * <p>还有一处必须显式松手的原因：疾跑一旦点上，只要 {@code movementForward} 还是 1，
     * 原版就以为你还按着 Ctrl，不会自己停。而我们恰好一直把 {@code movementForward} 按在 1 上，
     * 所以「不想跑」这件事必须我们自己说出来，不能指望原版收手。
     *
     * <p>顺带一提：疾跑只加快移动，<b>不影响挖掘速度</b>；换来的是去下一个目标路上更快，
     * 代价是饥饿值掉得更快。
     */
    private static void applySprint(ClientPlayerEntity player, boolean sprint) {
        AutoFarmConfig config = ConfigManager.get();
        boolean allowed = config.lockSprint
                && !player.isSneaking()
                && !player.isUsingItem()
                && (player.getHungerManager().getFoodLevel() > 6 || player.getAbilities().allowFlying);
        if (player.isSprinting() != (sprint && allowed)) {
            player.setSprinting(sprint && allowed);
        }
    }

    /**
     * 由 {@code KeyboardInputMixin} 在 {@code KeyboardInput.tick} 结束时调用。
     *
     * <p>只有在「正在接管」时才会动这些字段；其余时候一个字都不改，
     * 玩家的真实操作完全不受影响。
     */
    public static void applyInputOverride(Input input) {
        if (input == null || plan == null) {
            return;
        }

        boolean active = !paused && isActive();

        input.pressingForward = active && wantForward;
        input.pressingBack = false;
        input.pressingLeft = false;
        input.pressingRight = false;
        input.movementForward = input.pressingForward ? 1.0f : 0.0f;
        input.movementSideways = 0.0f;
        input.jumping = active && wantJump;
        input.sneaking = false;
    }

    // ================= 挖 / 垫 =================

    /**
     * 处理这一步要做的改造。
     *
     * @return true 表示还没做完，这一 tick 就停在这里；false 表示已经通了，可以继续走
     */
    private static boolean workOnStep(MinecraftClient client, ClientPlayerEntity player, PathStep step) {
        if (step.action() == PathAction.MINE) {
            BlockPos blocking = firstBlocking(client.world, step.pos());
            if (blocking == null) {
                return false;
            }
            // 先把准星压上去再动镐子（服务端会拿方块朝向做校验，视角乱给会被踢）
            BlockInteractor.aimAt(player, blocking);
            if (!BlockInteractor.isAimedAt(player, blocking, WORK_AIM_TOLERANCE)) {
                return true;
            }
            BlockInteractor.mineTick(client, blocking);
            return true;
        }

        if (step.action() == PathAction.PLACE) {
            if (BlockNavigator.canStand(client.world, step.pos())) {
                return false;
            }
            BlockInteractor.aimAt(player, step.pos());
            if (!BlockInteractor.isAimedAt(player, step.pos(), WORK_AIM_TOLERANCE)) {
                return true;
            }
            if (actionTicks % PLACE_RETRY_INTERVAL == 0) {
                placeBridge(client, step.pos());
            }
            return true;
        }
        return false;
    }

    /** 这一列还有哪一格堵着（先看脚，再看头），都通了返回 null */
    private static BlockPos firstBlocking(ClientWorld world, BlockPos pos) {
        if (!BlockNavigator.isPassableAt(world, pos.getX(), pos.getY(), pos.getZ())) {
            return pos;
        }
        if (!BlockNavigator.isPassableAt(world, pos.getX(), pos.getY() + 1, pos.getZ())) {
            return pos.up();
        }
        return null;
    }

    /**
     * 在 standPos 下方垫一块方块，让它变成能站的落脚点。
     *
     * <p>「具体怎么放」——贴哪一面、构造什么样的命中结果——全在
     * {@link BlockInteractor#placeWith} 里；收割时补种走的是同一个方法，
     * 所以挖、垫、种三件事都只有一份实现。
     */
    private static void placeBridge(MinecraftClient client, BlockPos standPos) {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return;
        }
        int slot = InventoryScanner.findPlaceableHotbarSlot(player);
        if (slot < 0) {
            return;
        }
        BlockInteractor.placeWith(client, standPos.down(), slot);
    }

    // ================= 内部：路径段管理 =================

    private static boolean ensureSegment(MinecraftClient client, ClientPlayerEntity player) {
        if (!segment.isEmpty() && waypoint < segment.size()) {
            return true;
        }
        if (!isActive()) {
            return false;
        }

        BlockPos target = getCurrentTarget();
        PathOptions options = currentOptions(client, player);

        // 走哪一格去干活：目标（作物那一格）本身是能站人的（作物碰撞箱是空的），
        // 照直走会直接走进作物里 —— 那样看着别扭，挖它的时候视角还得垂直朝下，
        // 服务端看到的是一个把头埋进地里的角色。
        // 所以首选「站在旁边能看着作物」的那一格，实在找不到才退回站在作物那一格上。
        //
        // 例外是 {@link #exactTargets} 里那类「要碰到才算数」的目标（掉落物）：
        // 它们必须站到那一格上，站在旁边看是捡不到的，所以直接以它本身为落点。
        BlockPos spot = exactTargets.contains(target)
                ? target
                : BlockNavigator.findViewSpot(client.world, target);
        List<BlockPos> candidates = spot == null || spot.equals(target)
                ? List.of(target)
                : List.of(spot, target);

        List<PathStep> found = null;
        for (BlockPos goal : candidates) {
            found = PathFinder.findPath(client.world, player, player.getBlockPos(), goal, options);
            if (found != null) {
                break;
            }
        }

        if (found == null) {
            abandonCurrentTarget();
            return false;
        }
        if (found.isEmpty()) {
            finishCurrentTarget();
            return false;
        }

        segment = found;
        waypoint = 0;
        actionTicks = 0;
        lastPos = null;
        stuckTicks = 0;
        return true;
    }

    /** 每次要新路径时现取一次「这一趟允许动多少环境」 */
    private static PathOptions currentOptions(MinecraftClient client, ClientPlayerEntity player) {
        AutoFarmConfig config = ConfigManager.get();
        return new PathOptions(
                config.allowMining,
                config.allowPlacing,
                InventoryScanner.countPlaceableInHotbar(player),
                config.autoSelectTool);
    }

    /**
     * 走到目标跟前了。
     *
     * <p>这里<b>不推进 index</b>，只是把状态切成「握着不放」，让角色停在原地，
     * 把「到站之后要干的事」（收割 + 补种）交给任务大脑。它干完会调
     * {@link #releaseTarget()}，那时才真正换到下一个目标。
     *
     * <p>这也是为什么「走到第一棵作物就直接跑去下一棵」的问题会消失：
     * 以前这里是「记一笔 + 立刻 index++」，等于把到站当成走完，
     * 现在到站只是到站，活干完才放行。
     */
    private static void finishCurrentTarget() {
        holding = true;
        clearSegment();
        lastPos = null;
        stuckTicks = 0;
        wantForward = false;
        wantJump = false;
    }

    /** 当前目标到不了：记一笔跳过，效果是「别再来找它」 */
    private static void abandonCurrentTarget() {
        advance();
    }

    /** 换到下一个目标（到站处理完、或者判定放弃时都走这里） */
    private static void advance() {
        BlockPos target = getCurrentTarget();
        if (target != null) {
            resolved.add(target);
        }
        index++;
        clearSegment();
        lastPos = null;
        stuckTicks = 0;
        wantForward = false;
        wantJump = false;
        if (!isActive()) {
            stop();
        }
    }

    private static void clearSegment() {
        segment = Collections.emptyList();
        waypoint = 0;
        actionTicks = 0;
    }

    // ================= 内部小工具 =================

    private static boolean hasReached(ClientPlayerEntity player, BlockPos pos) {
        double dx = pos.getX() + 0.5 - player.getX();
        double dz = pos.getZ() + 0.5 - player.getZ();
        if (dx * dx + dz * dz > WAYPOINT_RADIUS * WAYPOINT_RADIUS) {
            return false;
        }
        return Math.abs(pos.getY() - player.getY()) <= WAYPOINT_HEIGHT;
    }

    /** 只给「剩余路程」估算用的简化距离，和 PathPlanner 里的算法保持一致 */
    private static double straightDistance(double px, double py, double pz, BlockPos pos) {
        double dx = pos.getX() + 0.5 - px;
        double dz = pos.getZ() + 0.5 - pz;
        double dy = Math.abs(pos.getY() - py);
        return Math.sqrt(dx * dx + dz * dz) + dy * 1.5;
    }
}
