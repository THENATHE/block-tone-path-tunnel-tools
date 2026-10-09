package com.thenathe.blocktools.tunnel;

import java.util.Locale;

public record TunnelSpec(
	int width,
	int height,
	TunnelPalette walls,
	TunnelPalette floor,
	TunnelPalette ceiling,
	RailMode railMode
) {
	public static TunnelSpec parse(final String input, final int defaultWidth, final int defaultHeight) {
		if (input == null || input.isBlank()) throw new IllegalArgumentException("Tunnel settings are required.");
		Integer width = null;
		Integer height = null;
		String all = null;
		String walls = null;
		String floor = null;
		String ceiling = null;
		RailMode rail = null;
		for (String token : input.trim().split("\\s+")) {
			int separator = token.indexOf(':');
			if (separator <= 0 || separator == token.length() - 1) {
				throw new IllegalArgumentException("Unknown tunnel clause '" + token + "'. Use name:value clauses.");
			}
			String name = token.substring(0, separator).toLowerCase(Locale.ROOT);
			String value = token.substring(separator + 1);
			switch (name) {
				case "width" -> {
					if (width != null) throw duplicate("width:");
					width = dimension(value, "width", 1, 5);
				}
				case "height" -> {
					if (height != null) throw duplicate("height:");
					height = dimension(value, "height", 2, 5);
				}
				case "to", "all" -> {
					if (all != null) throw duplicate("to:");
					all = value;
				}
				case "wall", "walls" -> {
					if (walls != null) throw duplicate("walls:");
					walls = value;
				}
				case "floor", "floors" -> {
					if (floor != null) throw duplicate("floor:");
					floor = value;
				}
				case "ceiling", "ceilings" -> {
					if (ceiling != null) throw duplicate("ceiling:");
					ceiling = value;
				}
				case "rail", "rails" -> {
					if (rail != null) throw duplicate("rail:");
					rail = RailMode.parse(value);
				}
				default -> throw new IllegalArgumentException("Unknown tunnel clause '" + name + ":'.");
			}
		}

		int actualWidth = width == null ? defaultWidth : width;
		int actualHeight = height == null ? defaultHeight : height;
		RailMode actualRail = rail == null ? RailMode.OFF : rail;
		if (actualWidth < 2 && actualRail != RailMode.OFF) {
			throw new IllegalArgumentException("Rail mode requires tunnel width 2–5 so booster torches have a separate lane.");
		}
		boolean hasSeparate = walls != null || floor != null || ceiling != null;
		if (actualRail == RailMode.ONLY) {
			if (all != null || hasSeparate) throw new IllegalArgumentException("rail:only cannot be combined with lining palettes.");
			return new TunnelSpec(actualWidth, actualHeight, null, null, null, actualRail);
		}
		if (all != null && hasSeparate) {
			throw new IllegalArgumentException("Use either to:<mix> or all three walls:/floor:/ceiling: mixes, not both.");
		}
		if (all == null && !hasSeparate) {
			throw new IllegalArgumentException("Add to:<mix>, all three surface mixes, or use rail:only.");
		}
		TunnelPalette wallPalette;
		TunnelPalette floorPalette;
		TunnelPalette ceilingPalette;
		if (all != null) {
			TunnelPalette shared = TunnelPalette.parse(all);
			wallPalette = shared;
			floorPalette = shared;
			ceilingPalette = shared;
		} else {
			if (walls == null || floor == null || ceiling == null) {
				throw new IllegalArgumentException("Separate lining requires walls:<mix>, floor:<mix>, and ceiling:<mix>.");
			}
			wallPalette = TunnelPalette.parse(walls, true);
			floorPalette = TunnelPalette.parse(floor);
			ceilingPalette = TunnelPalette.parse(ceiling, true);
		}
		return new TunnelSpec(actualWidth, actualHeight, wallPalette, floorPalette, ceilingPalette, actualRail);
	}

	public boolean linesTunnel() {
		return railMode != RailMode.ONLY;
	}

	private static int dimension(final String value, final String name, final int minimum, final int maximum) {
		try {
			int parsed = Integer.parseInt(value);
			if (parsed < minimum || parsed > maximum) throw new NumberFormatException();
			return parsed;
		} catch (NumberFormatException exception) {
			throw new IllegalArgumentException("Tunnel " + name + " must be " + minimum + "–" + maximum + ".");
		}
	}

	private static IllegalArgumentException duplicate(final String clause) {
		return new IllegalArgumentException("The " + clause + " clause appears more than once.");
	}

	public enum RailMode {
		OFF,
		ON,
		ONLY;

		private static RailMode parse(final String value) {
			return switch (value.toLowerCase(Locale.ROOT)) {
				case "off", "no", "false" -> OFF;
				case "on", "yes", "true" -> ON;
				case "only", "railonly", "rail-only" -> ONLY;
				default -> throw new IllegalArgumentException("rail: must be on, off, or only.");
			};
		}
	}
}
