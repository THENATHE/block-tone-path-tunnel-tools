package com.thenathe.blocktools;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChatStyling {
	private static final Pattern COORDINATES = Pattern.compile("\\((-?\\d+),\\s*(-?\\d+),\\s*(-?\\d+)\\)");

	private ChatStyling() {
	}

	public static Component tagged(
		final String heading,
		final ChatFormatting headingColor,
		final String text,
		final ChatFormatting textColor
	) {
		return Component.literal("[" + heading + "] ").withStyle(headingColor)
			.append(styled(text, textColor));
	}

	static MutableComponent styled(final String text, final ChatFormatting textColor) {
		MutableComponent result = Component.empty();
		String[] lines = text.split("\\n", -1);
		for (int index = 0; index < lines.length; index++) {
			if (index > 0) result.append(Component.literal("\n").withStyle(textColor));
			appendLine(result, lines[index], textColor);
		}
		return result;
	}

	private static void appendLine(
		final MutableComponent result,
		final String line,
		final ChatFormatting textColor
	) {
		int start = 0;
		while (start < line.length() && Character.isWhitespace(line.charAt(start))) start++;
		int colon = line.indexOf(':', start);
		boolean label = colon >= start
			&& colon - start <= 32
			&& (start >= 2 || (start < line.length() && Character.isUpperCase(line.charAt(start))))
			&& (line.indexOf('`') < 0 || colon < line.indexOf('`'));
		if (label) {
			if (start > 0) result.append(Component.literal(line.substring(0, start)).withStyle(textColor));
			result.append(Component.literal(line.substring(start, colon + 1)).withStyle(ChatFormatting.YELLOW));
			appendInline(result, line.substring(colon + 1), textColor);
			return;
		}
		appendInline(result, line, textColor);
	}

	private static void appendInline(
		final MutableComponent result,
		final String text,
		final ChatFormatting textColor
	) {
		int cursor = 0;
		while (cursor < text.length()) {
			int commandStart = text.indexOf('`', cursor);
			if (commandStart < 0) {
				appendCoordinates(result, text.substring(cursor), textColor);
				return;
			}
			appendCoordinates(result, text.substring(cursor, commandStart), textColor);
			int commandEnd = text.indexOf('`', commandStart + 1);
			if (commandEnd < 0) {
				appendCoordinates(result, text.substring(commandStart), textColor);
				return;
			}
			result.append(Component.literal(text.substring(commandStart + 1, commandEnd)).withStyle(ChatFormatting.GRAY));
			cursor = commandEnd + 1;
		}
	}

	private static void appendCoordinates(
		final MutableComponent result,
		final String text,
		final ChatFormatting textColor
	) {
		Matcher matcher = COORDINATES.matcher(text);
		int cursor = 0;
		while (matcher.find()) {
			result.append(Component.literal(text.substring(cursor, matcher.start())).withStyle(textColor));
			result.append(Component.literal("(").withStyle(textColor));
			result.append(Component.literal(matcher.group(1)).withStyle(ChatFormatting.RED));
			result.append(Component.literal(", ").withStyle(textColor));
			result.append(Component.literal(matcher.group(2)).withStyle(ChatFormatting.GREEN));
			result.append(Component.literal(", ").withStyle(textColor));
			result.append(Component.literal(matcher.group(3)).withStyle(ChatFormatting.AQUA));
			result.append(Component.literal(")").withStyle(textColor));
			cursor = matcher.end();
		}
		result.append(Component.literal(text.substring(cursor)).withStyle(textColor));
	}
}
