package com.autofarm.client.compat;

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
 * <p>用它做什么：自由视角开着的时候，屏幕上看到的人是不动的，可方块却自己在碎，
 * 那画面太扎眼了。所以模组可以选择「有人在旁边看的时候先停手」。
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
}
