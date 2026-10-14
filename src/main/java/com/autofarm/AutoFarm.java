package com.autofarm;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutoFarm implements ModInitializer {
	public static final String MOD_ID = "autofarm";

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
