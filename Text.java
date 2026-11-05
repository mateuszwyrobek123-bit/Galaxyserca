package pl.galaxy.serca;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.List;

public final class Text {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private Text() {}

    public static Component parse(String s) {
        return LEGACY.deserialize(s == null ? "" : s)
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static List<Component> parseAll(List<String> lines) {
        List<Component> out = new ArrayList<>();
        for (String l : lines) out.add(parse(l));
        return out;
    }
}
