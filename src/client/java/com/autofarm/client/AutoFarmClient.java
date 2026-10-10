package com.autofarm.client;

import com.autofarm.client.config.ConfigManager;
import com.autofarm.client.gui.AutoFarmScreen;
import com.autofarm.client.gui.StatsScreen;
import com.autofarm.client.keybind.AutoFarmKeybinds;
import com.autofarm.client.net.ReconnectManager;
import com.autofarm.client.render.AutoFarmHud;
import com.autofarm.client.render.CropHighlighter;
import com.autofarm.client.stats.StatsManager;
import com.autofarm.client.task.FarmTaskManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 客户端入口。
 *
 * AutoFarm 采用「客户端驱动」架构：扫描、寻路、驱动角色全部在客户端完成，
 * 服务端只当你是一个普通玩家。这样原版服务器和模组服务器都能用，
 * 不需要服主安装任何东西。
 */
public class AutoFarmClient implements ClientModInitializer {

    public static final String MOD_ID = "autofarm";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        ConfigManager.load();
        // 累计统计与异常日志（E 组）：启动时读回来，之后由任务大脑在关键事件点记录
        StatsManager.get().load();
        AutoFarmKeybinds.register();
        // 世界里高亮成熟作物的线框（纯客户端渲染）
        CropHighlighter.register();
        // 左上角那行常驻状态：不开面板也能知道它在干嘛
        AutoFarmHud.register();
        // 附加功能「掉线重连」（C 组）：意外断开时自动回到上一个多人服务器
        ReconnectManager.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // 打开控制面板（用 while 是因为 wasPressed 在一个 tick 内可能累积多次）
            while (AutoFarmKeybinds.openPanel.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new AutoFarmScreen(null));
                }
            }

            // 一键开/关自动收割。放在任务 tick 之前处理，
            // 这样按下的同一个 tick 就能看到新的开关状态、立刻重新扫一遍。
            while (AutoFarmKeybinds.toggleEnabled.wasPressed()) {
                FarmTaskManager.toggleEnabled(client);
            }

            // 打开统计面板（F 组）：纯查看，不开主面板也能看账本
            while (AutoFarmKeybinds.openStats.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new StatsScreen(null));
                }
            }

            // 任务大脑：只在自己操作游戏时运转，开着别的大界面时暂停
            FarmTaskManager.tick(client);
        });

        LOGGER.info("AutoFarm 已加载，配置文件：{}", ConfigManager.getFilePath());
    }
}
