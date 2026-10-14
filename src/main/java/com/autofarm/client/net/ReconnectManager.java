package com.autofarm.client.net;

import com.autofarm.client.config.AutoFarmConfig;
import com.autofarm.client.config.ConfigManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 「掉线重连」（C 组）：连接被意外切断时，自动回到上一个多人服务器。
 *
 * <h2>它处理什么、不处理什么</h2>
 * <ul>
 *   <li><b>处理</b>：在多人服务器上因为超时、服务器重启、被踢而掉线 ——
 *       等一会儿自动重新连回去，可以设重试次数上限；</li>
 *   <li><b>不处理</b>：自己点「保存并退出」回到标题页（那是有意的，不该被拉回来），
 *       以及单人存档（没有服务器地址可重连）。</li>
 * </ul>
 *
 * <h2>怎么区分「意外掉线」和「主动退出」</h2>
 * 靠<b>断开之后停在哪块界面</b>：被踢 / 断线会停在「连接中断」界面，而自己保存退出
 * 会回到标题页。所以断开时先记下「要重连」，倒计时期间只要发现回到了标题页就取消。
 * 倒计时本身也留了 {@link AutoFarmConfig#reconnectDelaySeconds} 秒的缓冲，
 * 足够让主动退出的标题页出现。
 *
 * <p>重连用的是原版 {@link ConnectScreen#connect}，和玩家手动点「加入服务器」
 * 走同一条路 —— 不伪造任何登录信息。
 */
public final class ReconnectManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("autofarm");

    /** 最近一次所在的多人服务器；进单人存档时清成 null */
    private static ServerData lastServer;
    /** 是否已经安排了重连 */
    private static boolean pending;
    /** 距离真正重连还剩多少 tick */
    private static int countdown;
    /** 这一轮掉线已经重试了几次 */
    private static int attempts;

    private ReconnectManager() {
    }

    /** 由 {@code AutoFarmClient} 在初始化时调用一次 */
    public static void register() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> onDisconnect(client));
        ClientTickEvents.END_CLIENT_TICK.register(ReconnectManager::tick);
    }

    /** 连接断开：功能开着、而且上一站是多人服务器，就安排一次重连 */
    private static void onDisconnect(Minecraft client) {
        AutoFarmConfig config = ConfigManager.get();
        if (client == null || config == null || !config.autoReconnectEnabled) {
            return;
        }
        if (lastServer == null) {
            return; // 单人存档 / 还没进过服务器
        }
        int max = config.reconnectMaxAttempts;
        if (max > 0 && attempts >= max) {
            LOGGER.info("AutoFarm 掉线重连：已重试 {} 次，达到上限，放弃", attempts);
            pending = false;
            return;
        }
        pending = true;
        countdown = Math.max(3, config.reconnectDelaySeconds) * 20;
        LOGGER.info("AutoFarm 掉线重连：{} 秒后尝试重连 {}",
                Math.max(3, config.reconnectDelaySeconds), lastServer.ip);
    }

    private static void tick(Minecraft client) {
        if (client == null) {
            return;
        }
        AutoFarmConfig config = ConfigManager.get();
        if (config == null) {
            return;
        }

        // 始终记着「当前在哪个多人服务器」；进单人存档时它会变成 null
        if (client.level != null) {
            lastServer = client.getCurrentServer();
            if (!pending) {
                attempts = 0; // 正常在游戏里 = 上一轮重连成功（或本来就没事）
            }
        }

        if (!pending) {
            return;
        }
        if (!config.autoReconnectEnabled) {
            pending = false;
            attempts = 0;
            return;
        }
        if (client.level != null) {
            pending = false; // 已经回到游戏里了
            attempts = 0;
            return;
        }
        // 自己点「保存并退出」会停在标题页 —— 那是有意为之，取消重连
        if (client.screen instanceof TitleScreen) {
            pending = false;
            attempts = 0;
            return;
        }
        // 还没出现任何界面（断开处理中）→ 再等等
        if (client.screen == null) {
            return;
        }

        if (countdown > 0) {
            countdown--;
            return;
        }

        ServerData info = lastServer;
        if (info == null) {
            pending = false;
            return;
        }
        attempts++;
        pending = false;
        try {
            ServerAddress address = ServerAddress.parseString(info.ip);
            // 1.20.1 的签名：(父界面, 客户端, 地址, 服务器信息, 是否快速加入)
            // **1.20.5** 起末尾多了一个 TransferState（跨服传送的上下文），
            // 普通掉线重连没有这东西，传 null 就是「不是传送过来的」。
            //? if <1.20.5 {
            ConnectScreen.startConnecting(client.screen, client, address, info, false);
            //?} else {
            /*ConnectScreen.startConnecting(client.screen, client, address, info, false, null);
            *///?}
        } catch (Exception e) {
            LOGGER.warn("AutoFarm 掉线重连失败：{}", e.toString());
        }
    }
}
