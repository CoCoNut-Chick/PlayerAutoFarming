package com.autofarm.client.scan;

import com.autofarm.client.config.AutoFarmConfig;
import com.autofarm.client.inventory.InventoryScanner;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.block.Block;

/**
 * 「这棵作物要不要收」的判定。
 *
 * <p>把它单独拎出来，是因为判定要同时看<b>配置</b>（勾了哪些）和<b>玩家</b>
 * （手边还有没有种子），塞进 {@code CropRegistry} 会把「作物是什么」和
 * 「你打算怎么处理它」两件事搅在一起。
 *
 * <h2>四种模式</h2>
 * <p>模式常量定义在 {@link AutoFarmConfig} 上（配置自己管自己的取值），含义是：
 * <ul>
 *   <li>{@link AutoFarmConfig#FILTER_OFF} —— 全收；</li>
 *   <li>{@link AutoFarmConfig#FILTER_WHITELIST} —— 只收名单里的；</li>
 *   <li>{@link AutoFarmConfig#FILTER_BLACKLIST} —— 收名单以外的；</li>
 *   <li>{@link AutoFarmConfig#FILTER_ONLY_REPLANTABLE} —— 只收「手边有种子能补种」的。
 *       它不看名单，而是问背包：连种子都没有的作物，收了也补不回去，
 *       索性先放着，等从补给点取回种子再收。</li>
 * </ul>
 *
 * <p>名单存的是<b>方块注册名</b>而不是方块对象 —— 配置要写进 JSON，
 * 方块对象是没法序列化的（而且模组被卸掉之后那个名字就认不出来了，
 * 见 {@link CropRegistry#blockOf} 的兜底）。
 */
public final class CropFilter {

    private CropFilter() {
    }

    /**
     * 这棵作物该不该收。
     *
     * <p>注意判定发生在<b>扫描阶段</b>（见 {@code CropScanner#scan} 的谓词）——
     * 被筛掉的作物压根不会进「成熟作物」列表，所以既不会画描边、也不会被排进路线。
     *
     * @param cropBlock 这一格上的作物方块
     * @return 该收返回 true
     */
    public static boolean allowsHarvest(Block cropBlock, AutoFarmConfig config,
                                        LocalPlayer player) {
        if (config == null || cropBlock == null) {
            return false;
        }
        return switch (config.cropFilterMode) {
            case AutoFarmConfig.FILTER_WHITELIST -> contains(config, cropBlock);
            case AutoFarmConfig.FILTER_BLACKLIST -> !contains(config, cropBlock);
            // 「能补种」= 手边（快捷栏 + 主背包）还有这种作物的种子。
            // 模组作物一样算得准，因为认种子走的是「放下去会不会变作物方块」这套反查
            case AutoFarmConfig.FILTER_ONLY_REPLANTABLE ->
                    InventoryScanner.countSeeds(player, cropBlock) > 0;
            default -> true;
        };
    }

    /** 这棵作物在不在玩家的名单里 */
    public static boolean contains(AutoFarmConfig config, Block cropBlock) {
        String id = CropRegistry.idOf(cropBlock);
        return id != null && config.cropFilter != null && config.cropFilter.contains(id);
    }

    /** 名单是「只收这些」还是「不收这些」——给面板决定每行的显示方式 */
    public static boolean isListPositive(AutoFarmConfig config) {
        return config != null && config.cropFilterMode == AutoFarmConfig.FILTER_WHITELIST;
    }
}
