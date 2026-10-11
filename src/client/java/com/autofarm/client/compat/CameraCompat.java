package com.autofarm.client.compat;

import com.autofarm.client.config.AutoFarmConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;

/**
 * 「摄像机是不是已经离开身体了」的判断。
 *
 * <p>原版里摄像机和玩家是同一个实体，{@code MinecraftClient.cameraEntity} 永远指向
 * {@code client.player}。只有三种情况会不一样：旁观模式、以及<b>自由视角 / 灵魂出窍</b>
 * （masa 全家桶里的 Tweakeroo 就带这个功能，把摄像机挂到一个自己的实体上）。
 *
 * <p>所以这个判断不需要认识任何一个具体模组 —— 不写 {@code isModLoaded("tweakeroo")}，
 * 也不反射去读它的配置。任何用同一种方式实现的自由视角它都能认出来。
 *
 * <h2>它为什么不再是「一律停手」</h2>
 * 原版 {@code ClientPlayerEntity.tickNewAi()} 里有这么一段：
 * <pre>{@code
 *  if (this.isCamera()) {                       // 摄像机和身体重合时
 *      this.sidewaysSpeed = this.input.movementSideways;
 *      this.forwardSpeed  = this.input.movementForward;
 *      this.jumping       = this.input.jumping;
 *  }
 * }</pre>
 * 也就是说：<b>摄像机一旦离开身体，原版就再也不把按键抄成「移动意图」了</b> ——
 * 这时角色一步都走不了，跟模组没关系，是原版的设计（免得你飞视角时身体乱跑）。
 *
 * <p>所以本模组在 {@code tickNewAi} 的末尾补上了这步（见 {@code ClientPlayerInputMixin}）：
 * 只要我们确实在接管，就自己把移动意图写进 {@code sidewaysSpeed / forwardSpeed}。
 * 这样一来，自由视角开着也能照常干活，而且<b>完全不碰键盘输入对象</b> ——
 * 自由视角那套「用 WASD 飞摄像机」的操作一点不受影响，两边不再互相抢输入。
 *
 * <p>剩下的 {@code pauseOnFreecam} 开关留给了「就是不想让人在旁边看」的场景：
 * 打开它，自由视角期间整个停手（描边和路线保留）。
 */
public final class CameraCompat {

    private CameraCompat() {
    }

    /**
     * 摄像机和玩家是不是分开了。
     *
     * <p>注意和 F5 第三人称的区别：第三人称只是把镜头往后拉，
     * {@code cameraEntity} 仍然是玩家本人，所以这里会返回 false —— 这正是我们想要的。
     */
    public static boolean isCameraDetached() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return false;
        }
        Entity camera = client.getCameraEntity();
        return camera != null && camera != client.player;
    }

    /**
     * 自由视角下这一 tick 要不要「整个停手」。
     *
     * <p>由配置项「自由视角时停机」决定：开着才停；关着（默认）就照常干活 ——
     * 因为移动意图已经能绕过原版那个 {@code isCamera()} 判断继续写下去。
     * 所有「自由视角下先让路」的地方（撤退、受击回头、自动打怪、空闲环顾、进食、自救）
     * 统一问这一个方法，语义才一致。
     */
    public static boolean pauseForFreecam(AutoFarmConfig config) {
        return config != null && config.pauseOnFreecam && isCameraDetached();
    }
}

