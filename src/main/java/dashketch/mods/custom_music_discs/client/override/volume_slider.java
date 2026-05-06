package dashketch.mods.custom_music_discs.client.override;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;

public class volume_slider {

    /**
     * Retrieves the current value of the vanilla "Jukebox/Note Blocks" volume slider.
     * Returns a float between 0.0f (muted) and 1.0f (max).
     */
    public static float getJukeboxVolume() {
        // Directly access options here as it's initialized on startup
        return Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.RECORDS);
    }
}