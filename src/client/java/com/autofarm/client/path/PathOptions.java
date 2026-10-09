package com.autofarm.client.path;

/**
 * 这一趟允许它动多少环境。
 *
 * @param allowMining     允许挖掉挡路的方块
 * @param allowPlacing    允许垫方块铺路
 * @param availableBlocks 快捷栏里能拿来垫的方块总数（决定一路上最多垫几块）
 * @param autoSelectTool  挖掘前是否自动把手里的东西换成快捷栏里最合适的工具。
 *                        打开后，估算挖掘代价也按「换手之后」算，
 *                        所以估算和执行是同一套标准，不会出现「算得很便宜、真挖起来半天挖不动」
 */
public record PathOptions(boolean allowMining, boolean allowPlacing,
                          int availableBlocks, boolean autoSelectTool) {

    /** 什么都不许动，只能在现有地形上绕 */
    public static final PathOptions CONSERVATIVE = new PathOptions(false, false, 0, false);
}
