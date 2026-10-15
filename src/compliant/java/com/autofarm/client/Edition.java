package com.autofarm.client;

/**
 * 合规版（FarmPilot）的变体开关 —— 见完整版同名文件的详细说明。
 *
 * <p>这份文件只在 {@code -Pautofarm.edition=compliant} 的构建里参与编译；
 * 完整版构建会把整个 {@code src/compliant/java} 排除掉，改用自己的那份。
 *
 * <p>它为 {@code true} 时：
 * <ul>
 *   <li>「穿透显示」开关与战斗页的整块 UI 都是不可达代码，编译期即被删除；</li>
 *   <li>受击反制只保留「停手」，去掉「转向攻击者」那一段；</li>
 *   <li>配合被替换成空实现的 {@code ThroughWallPass} 与 {@code KillAura}，
 *       产物里不再存在穿墙渲染与自动攻击这两项能力。</li>
 * </ul>
 */
public final class Edition {
    private Edition() {
    }

    public static final boolean COMPLIANT = true;
}
