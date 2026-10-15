package com.autofarm;

import com.autofarm.client.Edition;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutoFarm implements ModInitializer {
	/**
	 * mod id。
	 *
	 * <p>完整版是 {@code autofarm}，合规版（Modrinth 上架的删减版）是 {@code farmpilot}。
	 * 用 {@link Edition#COMPLIANT} 这个<b>编译期常量</b>来选，所以它同时也是常量表达式 ——
	 * 两个变体的产物里各自只剩自己那个字符串，不存在运行时判断。
	 */
	public static final String MOD_ID = Edition.COMPLIANT ? "farmpilot" : "autofarm";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		LOGGER.info("Hello Fabric world!");
	}

	public static ResourceLocation id(String path) {
		// 1.21 把 ResourceLocation 的构造函数收成了 protected（只能走静态工厂）。
		// 源码里统一写 ResourceLocation，1.21.11+ 会由构建脚本自动改名成 Identifier。
		//? if <1.21 {
		return new ResourceLocation(MOD_ID, path);
		//?} else {
		/*return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
		*///?}
	}
}
