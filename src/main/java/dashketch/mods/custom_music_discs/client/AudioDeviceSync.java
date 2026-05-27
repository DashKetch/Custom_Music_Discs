package dashketch.mods.custom_music_discs.client;

import net.minecraft.client.Minecraft;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Mixer;

public class AudioDeviceSync {

    public static Mixer.Info getMinecraftSelectedMixer() {
        // Grab the exact device string the user selected in the Minecraft Audio settings
        String mcDevice = Minecraft.getInstance().options.soundDevice().get();

        // If it's empty, they have "System Default" selected in Minecraft anyway
        //noinspection ConstantValue
        if (mcDevice == null || mcDevice.isEmpty()) {
            return null;
        }

        // OpenAL device names and Java Sound device names are slightly different,
        // but they usually share the core hardware name. We do a partial text match.
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            String javaDeviceName = info.getName();

            // We ignore "Port" mixers and look for actual audio endpoints
            if (mcDevice.contains(javaDeviceName) || javaDeviceName.contains(mcDevice)) {
                return info;
            }
        }

        // Fallback to system default if we can't find a match
        return null;
    }
}