package dashketch.mods.custom_music_discs.server;

import net.neoforged.neoforge.common.ModConfigSpec;

public class ModConfigs {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue JUKEBOX_RANGE_BOOL;
    public static final ModConfigSpec.IntValue JUKEBOX_RANGE;

    static {
        BUILDER.comment("SERVER SETTINGS");
        BUILDER.push("Custom Music Discs Settings");

        JUKEBOX_RANGE_BOOL = BUILDER.define("jukebox_attentuation_range_is_vanilla", true);
        BUILDER.comment("If above is false");
        JUKEBOX_RANGE = BUILDER.defineInRange("jukebox_attenuation_range", 64, 16, 128);
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();
}
