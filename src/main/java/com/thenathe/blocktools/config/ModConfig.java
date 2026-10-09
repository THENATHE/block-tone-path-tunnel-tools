package com.thenathe.blocktools.config;

public final class ModConfig {
	public Integer configVersion;
	public volatile boolean enablePolymerTools = true;
	public String selectionItem = "minecraft:breeze_rod";
	public int defaultPathWidth = 3;
	public int maxToneBlocks = 65_536;
	public int pathVerticalSearch = 6;
	public int maxPathCatchUp = 8;

	public void normalize() {
		if ((configVersion == null || configVersion < 2) && defaultPathWidth <= 1) defaultPathWidth = 3;
		configVersion = 4;
		if (selectionItem == null || selectionItem.isBlank()) selectionItem = "minecraft:breeze_rod";
		defaultPathWidth = Math.max(1, Math.min(5, defaultPathWidth));
		maxToneBlocks = Math.max(1, Math.min(1_000_000, maxToneBlocks));
		pathVerticalSearch = Math.max(2, Math.min(32, pathVerticalSearch));
		maxPathCatchUp = Math.max(1, Math.min(32, maxPathCatchUp));
	}
}
