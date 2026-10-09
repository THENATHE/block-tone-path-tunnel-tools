package com.thenathe.blocktools.tunnel;

public final class TunnelConfig {
	public int configVersion = 1;
	public int defaultWidth = 3;
	public int defaultHeight = 3;
	public int poweredRailInterval = 8;
	public int maxCatchUp = 8;

	public void validate() {
		configVersion = 1;
		defaultWidth = Math.max(1, Math.min(5, defaultWidth));
		defaultHeight = Math.max(2, Math.min(5, defaultHeight));
		poweredRailInterval = Math.max(8, Math.min(128, poweredRailInterval));
		maxCatchUp = Math.max(1, Math.min(32, maxCatchUp));
	}
}
