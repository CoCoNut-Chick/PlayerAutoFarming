package com.autofarm.client.inventory;

import com.autofarm.client.compat.CameraCompat;
import com.autofarm.client.config.AutoFarmConfig;
import com.autofarm.client.config.ConfigManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * 「饿到跑不动了就自己吃口东西，而且一次吃到饱」。
 *
 * <h2>什么时候开始吃</h2>
 * 触发条件就是<b>不能疾跑</b>：饥饿值 ≤ {@value #EAT_THRESHOLD}。
 * 原版规定饥饿值 6 以上才能疾跑，所以走到这一步说明「锁定疾跑」已经形同虚设，
 * 这时候补一口食物，跑速立刻回来 —— 它服务的其实是「维持赶路效率」，不是「保命」。
 *
 * <h2>吃到什么时候为止</h2>
 * <b>一直吃到饱食度满</b>（{@value #FULL_FOOD}），而不是「能跑了就停」。
 *
 * <p>这一条是玩家反馈后改的。只吃到「能跑」的话，饥饿值会一直在 7~8 附近晃：
 * 跑几十秒就掉回来，于是每半分钟就要停下来吃一口 —— 一次一小口，
 * 频率高、打扰多。一口气吃到满，回到 6 的间隔被拉长好几倍，
 * 中间一大段时间它都在专心干活。
 *
 * <p>中途吃不到东西（快捷栏里的食物吃完了）也会停手，
 * 不会因为「还没饱」就永远站在地里。
 *
 * <p>吃哪个由 {@link InventoryScanner#findBestFoodSlot} 挑：<b>回饱食度最多的那个</b>，
 * 吃完了自动换下一个食物继续吃，直到吃饱。
 *
 * <h2>怎么让它「吃」</h2>
 * 走的是原版那条路：把<b>使用键</b>按下去，一直按到吃完再松开。
 * 之所以必须这样，是因为原版每 tick 都会检查「使用键还按着吗」，一松手就
 * {@code stopUsingItem}，单点一次是吃不完的。要连着吃好几口，更是要一直按着。
 *
 * <p>动玩家的按键是有代价的，所以这里立了两条规矩：
 * <ul>
 *   <li><b>只在自动收割开着的时候才碰</b>；关掉开关、退出世界立刻松手；</li>
 *   <li>按下前先记下<b>原来是什么状态</b>，松手时原样还回去，绝不留下「按着不放」的残影。</li>
 * </ul>
 *
 * <h2>吃的时候走路吗</h2>
 * 不走。原版使用物品时移动速度只剩两成，边吃边挪还容易把食物吃一半就打断，
 * 索性停下来吃完再走（由 {@code PathWalker} 查询 {@link #isHandlingHunger()} 来让路）。
 */
public final class AutoEater {

    /** 饥饿值低于等于它就开始吃：6 是原版「能不能疾跑」的分界线 */
    public static final int EAT_THRESHOLD = 6;

    /** 饥饿值的上限，也就是「吃饱了」 */
    public static final int FULL_FOOD = 20;

    /**
     * 一次进食最多持续多少 tick，超了就放弃。
     *
     * <p>要吃饱可能得连吃七八口（曲奇一口才回 2 点），一口大约 1.6 秒，
     * 所以这个上限放得比较宽。它的作用只是「别死在状态机里」，
     * 正常情况永远走不到（吃饱、吃光、或者你自己打开背包都会提前结束）。
     */
    private static final int MAX_EATING_TICKS = 600;

    /**
     * 刚按下使用键的前几 tick，还没进入「正在使用」状态。
     * 这段宽限期用来避免把「刚按下还没生效」误判成「已经吃完了」。
     * 原版每次使用物品后有 4 tick 冷却，正好和这个值对上。
     */
    private static final int START_GRACE_TICKS = 4;

    /** 整轮进食结束之后歇几 tick 再考虑下一次，避免在成功/失败之间高频抖动 */
    private static final int RETRY_COOLDOWN = 8;

    /** 整轮进食还开着（从按下第一口到吃饱/吃光） */
    private static boolean eating;

    /** 我们介入之前，使用键本来是什么状态（松手时要还回去） */
    private static boolean useKeyBefore;

    /** 这一轮进食总共持续了多少 tick（管超时） */
    private static int elapsed;

    /** 当前这一口已经按了多久（用来判断「这一口吃完了」） */
    private static int biteTicks;

    private static int cooldown;

    private AutoEater() {
    }

    /**
     * 「吃饭这件事还没办完」—— 走路和收割都要给它让路。
     *
     * <p>它比单纯的「正在吃」宽一点。吃完一口之后有 {@value #RETRY_COOLDOWN} tick 的冷却，
     * 冷却里 {@code eating} 是 false；但只要你还饿着、快捷栏里也还有东西吃，
     * 那这一口迟早要补上。这时候要是把控制权还给走路逻辑，就会走成
     * 「走 8 tick → 停下吃 → 走 8 tick」的抽搐。所以冷却期间同样算「在办吃饭的事」。
     *
     * <p>注意最后那半句：<b>快捷栏里没有吃的就不算</b>。否则「饿着又没有食物」
     * 会让角色永远停在原地不动，那才是真的坏了。
     */
    public static boolean isHandlingHunger() {
        if (eating) {
            return true;
        }
        if (cooldown <= 0) {
            return false;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client == null ? null : client.player;
        if (player == null) {
            return false;
        }
        if (player.getAbilities().allowFlying || player.getHungerManager().getFoodLevel() > EAT_THRESHOLD) {
            return false;
        }
        return InventoryScanner.findBestFoodSlot(player) >= 0;
    }

    /** 每 tick 调一次（在驱动走路之前） */
    public static void tick(MinecraftClient client, AutoFarmConfig config) {
        ClientPlayerEntity player = client == null ? null : client.player;
        if (player == null || client.world == null) {
            reset();
            return;
        }

        // 你打开背包/聊天时不该被偷偷喂食
        if (client.currentScreen != null) {
            stop(client);
            cooldown = 0;
            return;
        }

        // 自由视角（摄像机和身体分开）时也不该被偷偷喂食 —— 屏幕上身体不动，
        // 却在那边自己啃东西，同样扎眼。
        if (CameraCompat.isCameraDetached()) {
            stop(client);
            cooldown = 0;
            return;
        }

        // 不干活的时候，一个字都不碰玩家的按键
        if (!config.enabled || !config.autoEat) {
            stop(client);
            cooldown = 0;
            return;
        }

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        if (eating) {
            keepEating(client, player, config);
        } else {
            maybeStart(client, player);
        }
    }

    /** 退出世界 / 关掉开关时松手 */
    public static void reset() {
        stop(MinecraftClient.getInstance());
        cooldown = 0;
    }

    // ================= 内部 =================

    private static void maybeStart(MinecraftClient client, ClientPlayerEntity player) {
        // 飞在天上的（创造/旁观）本来就不饿，没必要替他操心
        if (player.getAbilities().allowFlying || player.isSpectator()) {
            return;
        }
        if (player.getHungerManager().getFoodLevel() > EAT_THRESHOLD) {
            return; // 还跑得动
        }
        if (player.isUsingItem()) {
            return; // 你正忙着（喝药、举盾），别横插一手
        }

        int slot = InventoryScanner.findBestFoodSlot(player);
        if (slot < 0) {
            return; // 快捷栏里没吃的，那不吃了
        }

        // 换到回饱食度最多的那一格：只改快捷栏选中项，不用打开背包
        player.getInventory().selectedSlot = slot;

        useKeyBefore = client.options.useKey.isPressed();
        client.options.useKey.setPressed(true);
        eating = true;
        elapsed = 0;
        biteTicks = 0;
    }

    private static void keepEating(MinecraftClient client, ClientPlayerEntity player,
                                   AutoFarmConfig config) {
        elapsed++;
        biteTicks++;

        int target = config.eatUntilFull ? FULL_FOOD : EAT_THRESHOLD + 1;

        // ---- 吃饱了：这一轮结束 ----
        if (player.getHungerManager().getFoodLevel() >= target) {
            stop(client);
            return;
        }

        // ---- 手里那格不是食物了（这一口吃完了，或者被别的逻辑换走了）→ 换下一格 ----
        if (!player.getMainHandStack().isFood()) {
            if (!switchToAnotherFood(player)) {
                stop(client);
            }
            return;
        }

        // ---- 使用状态结束了：这一口吃完了，接着吃下一口 ----
        if (biteTicks > START_GRACE_TICKS && !player.isUsingItem()) {
            if (!switchToAnotherFood(player)) {
                stop(client);
            }
            return;
        }

        // ---- 兜底：吃太久就放弃，别让状态机卡在这 ----
        if (elapsed > MAX_EATING_TICKS) {
            stop(client);
            return;
        }

        // 按住不放：原版每 tick 都会检查它，松了就等于不吃了
        client.options.useKey.setPressed(true);
    }

    /**
     * 换到「回饱食度最多的那一格」继续吃，并把「这一口」的计时归零。
     *
     * <p>换手是必须的：一口吃完整栈可能就没了，手里变成空气；
     * 也可能还有别的食物但当前这格空了。每次换完都重置 {@code biteTicks}，
     * 这样下一口会重新走一遍「按下 → 宽限期 → 检测吃完」的流程。
     *
     * @return 还有东西可吃吗
     */
    private static boolean switchToAnotherFood(ClientPlayerEntity player) {
        int slot = InventoryScanner.findBestFoodSlot(player);
        if (slot < 0) {
            return false;
        }
        player.getInventory().selectedSlot = slot;
        biteTicks = 0;
        return true;
    }

    /** 结束这一轮进食：把手松开（恢复成我们介入前的样子），并歇几 tick */
    private static void stop(MinecraftClient client) {
        if (!eating) {
            return;
        }
        if (client != null && client.options != null) {
            client.options.useKey.setPressed(useKeyBefore);
        }
        eating = false;
        elapsed = 0;
        biteTicks = 0;
        // 「无间隔连发」时不吃这一轮结束后的歇气 —— 吃到饱就直接接下一件事。
        AutoFarmConfig config = ConfigManager.get();
        cooldown = config != null && config.instantActions ? 0 : RETRY_COOLDOWN;
    }
}
