package dashketch.mods.custom_music_discs.client;

import net.minecraft.client.Minecraft;
import javax.sound.sampled.*;

import static dashketch.mods.custom_music_discs.Custom_music_discs.LOGGER;

public class AudioDeviceSync {

    public static Mixer.Info getMinecraftSelectedMixer() {
        String mcDevice = Minecraft.getInstance().options.soundDevice().get();

        // Log all the found devices
        LOGGER.debug("[CUSTOM DISCS] Minecraft reports selected sound device string: \"{}\"", mcDevice);

        // If Minecraft is set to default, return null
        //noinspection ConstantValue
        if (mcDevice == null || mcDevice.isEmpty() || mcDevice.equalsIgnoreCase("System Default") || mcDevice.contains("default")) {
            LOGGER.info("[CUSTOM DISCS] Minecraft is set to default device. Letting Java Sound handle it.");
            return null;
        }

        // Clean up OpenAL prefixes to make matching easier
        String cleanMcDevice = mcDevice.replace("OpenAL Soft on ", "").toLowerCase();

        DataLine.Info playbackRequirement = new DataLine.Info(SourceDataLine.class, null);

        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            try {
                Mixer mixer = AudioSystem.getMixer(info);

                // Skip microphones, lines, and control ports
                if (!mixer.isLineSupported(playbackRequirement)) {
                    continue;
                }

                String javaDeviceName = info.getName().toLowerCase();
                LOGGER.info("[CUSTOM DISCS] Checking Java Sound hardware mixer: \"{}\"", info.getName());

                // Check for a partial name match
                if (cleanMcDevice.contains(javaDeviceName) || javaDeviceName.contains(cleanMcDevice)) {
                    LOGGER.info("[CUSTOM DISCS] MATCH FOUND! Mapping Minecraft to Java Mixer: {}", info.getName());
                    return info;
                }
            } catch (Exception ignored) {}
        }

        LOGGER.warn("[CUSTOM DISCS] Could not find a matching Java Sound mixer for: \"{}\"", mcDevice);
        return null;
    }
}