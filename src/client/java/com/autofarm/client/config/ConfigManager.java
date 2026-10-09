package com.autofarm.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 配置的读写。文件位置：<游戏目录>/config/autofarm.json
 * 开发环境下即 run/config/autofarm.json
 */
public final class ConfigManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("autofarm");
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private static final Path FILE =
            FabricLoader.getInstance().getConfigDir().resolve("autofarm.json");

    private static AutoFarmConfig config = new AutoFarmConfig();

    private ConfigManager() {
    }

    public static AutoFarmConfig get() {
        return config;
    }

    public static Path getFilePath() {
        return FILE;
    }

    /** 启动时读一次。文件不存在或损坏都退回到默认值，绝不让模组因为配置崩掉 */
    public static void load() {
        if (!Files.exists(FILE)) {
            config = new AutoFarmConfig();
            persist();
            return;
        }
        try (Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            AutoFarmConfig loaded = GSON.fromJson(reader, AutoFarmConfig.class);
            config = loaded != null ? loaded : new AutoFarmConfig();
            config.clamp();
        } catch (Exception e) {
            LOGGER.warn("读取配置文件失败，使用默认值：{}", FILE, e);
            config = new AutoFarmConfig();
        }
    }

    /** 用面板里编辑好的草稿整体替换并落盘 */
    public static void save(AutoFarmConfig newConfig) {
        newConfig.clamp();
        config = newConfig;
        persist();
    }

    /** 把当前配置写回磁盘 */
    public static void persist() {
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            LOGGER.error("写入配置文件失败：{}", FILE, e);
        }
    }
}
