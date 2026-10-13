package com.autofarm.client.stats;

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
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 累计统计与异常日志（E 组）。
 *
 * <h2>它记的和 HUD 记的不是一回事</h2>
 * {@code FarmTaskManager} 里的收割数 / 补种数 / 时长都是<b>本轮</b>的 —— 关一次开关就清零。
 * 这里记的是<b>累计</b>：从模组装上到现在，总共收了多少、补了多少、撒了多少骨粉、
 * 跑了几趟补给、异常过几次。它们会写进磁盘，重进游戏还在。
 *
 * <h2>两个文件</h2>
 * <ul>
 *   <li><b>{@code config/autofarm-stats.json}</b> —— 累计统计，脏了才写（见 {@link #dirty}）；</li>
 *   <li><b>{@code config/autofarm.log}</b> —— 异常日志，逐条追加、带时间戳。
 *       挖不动、补给失败、卡住这类「它为什么停手 / 绕开」的事都记在这里，
 *       方便事后排查，不用盯着屏幕等它再犯一次。</li>
 * </ul>
 *
 * <p>它不参与任何决策：纯粹是「记下来给人看」。统计文件写坏、日志写不进，都只打一条
 * 日志、绝不让模组因为统计崩掉。
 */
public final class StatsManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("autofarm");
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private static final Path STATS_FILE =
            FabricLoader.getInstance().getConfigDir().resolve("autofarm-stats.json");
    private static final Path LOG_FILE =
            FabricLoader.getInstance().getConfigDir().resolve("autofarm.log");

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final StatsManager INSTANCE = new StatsManager();

    // ---- 累计统计（跨会话）----

    /** 总共收割了多少个 */
    private long totalHarvested;
    /** 总共往空耕地上补种了多少格 */
    private long totalPlanted;
    /** 总共捡回来多少个作物掉落物 */
    private long totalPickedUp;
    /** 总共用掉多少次骨粉 */
    private long totalBoneMeal;
    /** 总共跑了几趟补给点 */
    private long totalSupplyRuns;
    /** 总共实打实干了多少秒 */
    private long totalWorkSeconds;
    /** 累计异常次数（写进日志的那些） */
    private int issueCount;

    /** 有没有还没写盘的改动。transient 让 Gson 别把它序列化进统计文件 */
    private transient boolean dirty;

    private StatsManager() {
    }

    public static StatsManager get() {
        return INSTANCE;
    }

    // ================= 读写 =================

    /** 启动时读一次。文件不存在或损坏都从零开始，绝不让模组因为统计崩掉 */
    public void load() {
        if (!Files.exists(STATS_FILE)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(STATS_FILE, StandardCharsets.UTF_8)) {
            StatsManager loaded = GSON.fromJson(reader, StatsManager.class);
            if (loaded != null) {
                totalHarvested = loaded.totalHarvested;
                totalPlanted = loaded.totalPlanted;
                totalPickedUp = loaded.totalPickedUp;
                totalBoneMeal = loaded.totalBoneMeal;
                totalSupplyRuns = loaded.totalSupplyRuns;
                totalWorkSeconds = loaded.totalWorkSeconds;
                issueCount = loaded.issueCount;
            }
        } catch (Exception e) {
            LOGGER.warn("读取统计文件失败，从零开始：{}", STATS_FILE, e);
        }
    }

    /** 有改动就写盘（没改动直接跳过，别每次 tick 都往磁盘上写） */
    public void save() {
        if (!dirty) {
            return;
        }
        try {
            Files.createDirectories(STATS_FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(STATS_FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
            dirty = false;
        } catch (IOException e) {
            LOGGER.error("写入统计文件失败：{}", STATS_FILE, e);
        }
    }

    // ================= 记录（被任务大脑在关键事件点调用） =================

    public void recordHarvest() {
        totalHarvested++;
        dirty = true;
    }

    public void recordPlant() {
        totalPlanted++;
        dirty = true;
    }

    public void recordPickup() {
        totalPickedUp++;
        dirty = true;
    }

    public void recordBoneMeal() {
        totalBoneMeal++;
        dirty = true;
    }

    public void recordSupplyRun() {
        totalSupplyRuns++;
        dirty = true;
    }

    /** 把本轮干活的秒数并入累计（收工 / 关开关时调用一次） */
    public void addWorkSeconds(int seconds) {
        if (seconds > 0) {
            totalWorkSeconds += seconds;
            dirty = true;
        }
    }

    /**
     * 记一条异常，并追加进 {@code config/autofarm.log}。
     *
     * <p>参数是给日志看的原文（带坐标 / 原因），不是翻译键 —— 日志是写给人排查的，
     * 写英文描述比写一堆 {@code autofarm.notice.xxx} 好读。
     */
    public void logIssue(String message) {
        issueCount++;
        dirty = true;
        String line = "[" + LocalDateTime.now().format(TIME) + "] " + message;
        try {
            Files.createDirectories(LOG_FILE.getParent());
            Files.writeString(LOG_FILE, line + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOGGER.error("写入异常日志失败：{}", LOG_FILE, e);
        }
    }

    // ================= 面板操作 =================

    /** 把累计统计全部归零（面板上的「清零」按钮） */
    public void clear() {
        totalHarvested = 0;
        totalPlanted = 0;
        totalPickedUp = 0;
        totalBoneMeal = 0;
        totalSupplyRuns = 0;
        totalWorkSeconds = 0;
        issueCount = 0;
        dirty = true;
        save();
    }

    /** 清空异常日志文件（面板上的「清空日志」按钮） */
    public void clearLog() {
        try {
            Files.deleteIfExists(LOG_FILE);
        } catch (IOException e) {
            LOGGER.error("清空异常日志失败：{}", LOG_FILE, e);
        }
    }

    // ================= getter =================

    public long getTotalHarvested() {
        return totalHarvested;
    }

    public long getTotalPlanted() {
        return totalPlanted;
    }

    public long getTotalPickedUp() {
        return totalPickedUp;
    }

    public long getTotalBoneMeal() {
        return totalBoneMeal;
    }

    public long getTotalSupplyRuns() {
        return totalSupplyRuns;
    }

    public long getTotalWorkSeconds() {
        return totalWorkSeconds;
    }

    public int getIssueCount() {
        return issueCount;
    }
}
