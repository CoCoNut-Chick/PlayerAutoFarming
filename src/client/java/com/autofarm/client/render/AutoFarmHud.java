package com.autofarm.client.render;

import com.autofarm.client.config.AutoFarmConfig;
import com.autofarm.client.config.ConfigManager;
import com.autofarm.client.harvest.CropHarvester;
import com.autofarm.client.task.FarmTaskManager;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * 左上角那行常驻状态（HUD）。
 *
 * <h2>为什么需要它</h2>
 * 在这之前，「它现在在干嘛、收了多少、种子还够不够、为什么突然停了」这些信息
 * <b>只有打开控制面板才看得到</b> —— 而收割的时候你最不想做的恰好就是开界面。
 * 结果就是：它明明在正常干活，你却总想按一下快捷键确认一下；它一旦停机，
 * 你要么开面板、要么一脸问号。
 *
 * <h2>显示什么</h2>
 * <ul>
 *   <li><b>状态</b>：直接复用 {@link FarmTaskManager#getState()}，和面板里那一行同源；</li>
 *   <li><b>已收 / 种子</b>：这一轮收了多少个、手边还有多少能补种的东西
 *       （种子快见底时数字会变暖色，你会先一步看出来）；</li>
 *   <li><b>提示</b>：有异常才出现这第三行 —— 背包满、缺种子、安全停机都在这儿说，
 *       而不用开面板。</li>
 * </ul>
 *
 * <p>它是纯渲染、只读别人的状态，不参与任何决策；关掉开关
 * （{@link AutoFarmConfig#showHud}）就整块不画。
 */
public final class AutoFarmHud {

    private static final int X = 4;
    private static final int Y = 4;
    /** 行高：比字体高度多一点，读起来不挤 */
    private static final int LINE_HEIGHT = 10;
    private static final int PAD = 2;

    private static final int COLOR_TEXT = 0xFFE8E8E8;
    private static final int COLOR_HINT = 0xFFA0A0A0;
    private static final int COLOR_NOTICE = 0xFFFFC46B;
    private static final int COLOR_OFF = 0xFF808080;
    /** 半透明黑底：白字在任何地形上都看得清 */
    private static final int BACKGROUND = 0x66000000;

    /** 种子少于这个数就变暖色提醒 */
    private static final int SEED_WARN = 8;

    private AutoFarmHud() {
    }

    public static void register() {
        HudRenderCallback.EVENT.register(AutoFarmHud::render);
    }

    private static void render(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        AutoFarmConfig config = ConfigManager.get();
        if (!config.showHud || client == null || client.player == null || client.world == null) {
            return;
        }
        // 开着界面时原版本来就不画 HUD，这里也一起让路；F3 调试界面同理，
        // 不然两堆字会叠在一起
        if (client.currentScreen != null || client.options.hudHidden || client.options.debugEnabled) {
            return;
        }

        List<Line> lines = new ArrayList<>();
        // 「纯拾取模式」是一道独立于总开关的闸：只开它，HUD 也该照常显示
        if (!config.enabled && !config.pickupOnlyMode) {
            // 关着的时候只留一行很淡的字，提示它还在、以及怎么开
            lines.add(new Line(Text.translatable("autofarm.hud.off"), COLOR_OFF));
            draw(context, client, lines);
            return;
        }

        lines.add(new Line(Text.translatable("autofarm.hud.title",
                Text.translatable(FarmTaskManager.getState().getTranslationKey())), COLOR_TEXT));

        // 纯拾取模式：单独标一行，免得看 HUD 的人以为「总开关没开、模组却在动」是出 bug 了
        if (config.pickupOnlyMode) {
            lines.add(new Line(Text.translatable("autofarm.hud.pickup_only"), COLOR_NOTICE));
        }

        int seeds = FarmTaskManager.getSeedCount();
        // 只有「确实在靠种子补种」时才把种子少当成警告：
        // 收甜浆果这类不用种的东西时，种子数恒为 0，一直亮着暖色反而变成噪音
        boolean seedsMatter = CropHarvester.getCropBlock() != null;
        lines.add(new Line(Text.translatable("autofarm.hud.stats",
                FarmTaskManager.getHarvestedCount(), seeds),
                seedsMatter && seeds <= SEED_WARN ? COLOR_NOTICE : COLOR_HINT));

        // 附加功能「HUD 详细统计」（默认关闭）：多一行本轮运行时长与补种数。
        // 时长统计的是真正干活的时间，暂停的那段不算，所以更像一块秒表。
        if (config.verboseHud) {
            lines.add(new Line(Text.translatable("autofarm.hud.verbose",
                    duration(FarmTaskManager.getSessionSeconds()),
                    FarmTaskManager.getPlantedCount()), COLOR_HINT));
        }

        String notice = FarmTaskManager.getHarvestNotice();
        if (notice != null) {
            lines.add(new Line(Text.translatable(notice), COLOR_NOTICE));
        }

        draw(context, client, lines);
    }

    private static void draw(DrawContext context, MinecraftClient client, List<Line> lines) {
        int width = 0;
        for (Line line : lines) {
            width = Math.max(width, client.textRenderer.getWidth(line.text()));
        }
        int height = lines.size() * LINE_HEIGHT;

        context.fill(X - PAD, Y - PAD, X + width + PAD, Y + height + PAD, BACKGROUND);

        int y = Y;
        for (Line line : lines) {
            context.drawTextWithShadow(client.textRenderer, line.text(), X, y, line.color());
            y += LINE_HEIGHT;
        }
    }

    /** 一行字 + 它的颜色 */
    private record Line(Text text, int color) {
    }

    /**
     * 秒数 → 「1m23s」这样的短格式。
     *
     * <p>用 m / s 而不是「分」「秒」：这行字很窄，而这两种单位在中文玩家眼里
     * 也早就见惯了；省下来的宽度留给数字。不满一分钟就不显示分钟那一段。
     */
    private static String duration(int seconds) {
        if (seconds < 60) {
            return seconds + "s";
        }
        return (seconds / 60) + "m" + (seconds % 60) + "s";
    }
}
