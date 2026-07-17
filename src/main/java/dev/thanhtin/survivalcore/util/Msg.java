package dev.thanhtin.survivalcore.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.command.CommandSender;

/** Small helpers for colored chat messages with a consistent plugin prefix. */
public final class Msg {

    public static final TextColor BRAND = TextColor.color(0x4FC3F7);
    private static final Component PREFIX = Component.text("[SurvivalCore] ", BRAND);

    private Msg() {}

    public static void info(CommandSender to, String text) {
        to.sendMessage(PREFIX.append(Component.text(text, NamedTextColor.GRAY)));
    }

    public static void ok(CommandSender to, String text) {
        to.sendMessage(PREFIX.append(Component.text(text, NamedTextColor.GREEN)));
    }

    public static void warn(CommandSender to, String text) {
        to.sendMessage(PREFIX.append(Component.text(text, NamedTextColor.YELLOW)));
    }

    public static void error(CommandSender to, String text) {
        to.sendMessage(PREFIX.append(Component.text(text, NamedTextColor.RED)));
    }

    public static void plain(CommandSender to, String text, NamedTextColor color) {
        to.sendMessage(Component.text(text, color));
    }
}
