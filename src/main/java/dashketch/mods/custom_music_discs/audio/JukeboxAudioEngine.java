package dashketch.mods.custom_music_discs.audio;

import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.AudioDevice;
import javazoom.jl.player.JavaSoundAudioDevice;
import javazoom.jl.player.Player;
import org.essentials.custom_background_music.MusicMuter;
import dashketch.mods.custom_music_discs.client.AudioDeviceSync; // Import the Sync utility!

import javax.sound.sampled.*;
import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Field;

import static dashketch.mods.custom_music_discs.Custom_music_discs.LOGGER;

public class JukeboxAudioEngine {
    private static final JukeboxAudioEngine INSTANCE = new JukeboxAudioEngine();
    private Player player;
    private Thread musicThread;
    private float volume = 1.0f;

    public static JukeboxAudioEngine getInstance() {
        return INSTANCE;
    }

    private AudioDevice createSyncedDevice() {
        try {
            Mixer.Info mixerInfo = AudioDeviceSync.getMinecraftSelectedMixer();
            Mixer mixer = mixerInfo != null ? AudioSystem.getMixer(mixerInfo) : null;

            // Create an on-the-fly subclass of JLayer's AudioDevice
            return new JavaSoundAudioDevice() {
                @Override
                protected void createSource() throws JavaLayerException {
                    try {
                        AudioFormat fmt = getAudioFormat();
                        DataLine.Info info = new DataLine.Info(SourceDataLine.class, fmt);

                        SourceDataLine line;
                        // Force it to use the Minecraft Mixer
                        if (mixer != null) {
                            line = (SourceDataLine) mixer.getLine(info);
                        } else {
                            line = (SourceDataLine) AudioSystem.getLine(info);
                        }

                        line.open(fmt);

                        // Inject the line back into JLayer's private 'source' variable
                        Field sourceField = JavaSoundAudioDevice.class.getDeclaredField("source");
                        sourceField.setAccessible(true);
                        sourceField.set(this, line);

                    } catch (Exception e) {
                        LOGGER.warn("[CUSTOM DISCS] Custom device failed, falling back to default.", e);
                        super.createSource(); // Let JLayer do its default behavior as a safety net
                    }
                }
            };
        } catch (Exception e) {
            LOGGER.warn("[CUSTOM DISCS] Audio Device Factory failed.", e);
            try {
                return javazoom.jl.player.FactoryRegistry.systemRegistry().createAudioDevice();
            } catch (Exception ex) {
                return null;
            }
        }
    }

    public void play(File musicFile) {
        try {
            MusicMuter.muteMinecraftMusic();
        } catch (Exception e) {
            LOGGER.warn("Could not mute the music!", e);
            return;
        }

        stop(); // Ensure old music is dead

        // 1. Check if file is null or missing
        if (musicFile == null) {
            LOGGER.warn("[CUSTOM DISCS FATAL] musicFile is NULL!");
            return;
        }
        if (!musicFile.exists()) {
            LOGGER.warn("[CUSTOM DISCS FATAL] File does not exist at path: {}", musicFile.getAbsolutePath());
            return;
        }

        LOGGER.info("[CUSTOM DISCS] Successfully found file, starting thread for: {}", musicFile.getName());

        musicThread = new Thread(() -> {
            // 2. ATTEMPT 1: Play instantly
            try (FileInputStream fis = new FileInputStream(musicFile)) {
                // Instead of letting JLayer pick the device, pass in the synced one
                AudioDevice customDevice = createSyncedDevice();
                player = new javazoom.jl.player.Player(new java.io.BufferedInputStream(fis), customDevice);

                setVolume(volume);
                player.play();
            } catch (Exception e) {
                LOGGER.warn("[CUSTOM DISCS] Attempt 1 failed (Likely Line Lock): {}", e.getMessage());
            }
        });
        musicThread.setDaemon(true);
        musicThread.start();
    }

    public void stop() {
        if (player != null) {
            player.close();
            player = null;
        }
        if (musicThread != null) {
            musicThread.interrupt();
            musicThread = null;
        }
        MusicMuter.unmuteMinecraftMusic();
    }

    //todo: fix music not playing at all because i'm fixing the bug where the music doesn't play out of the right output device

    public boolean isPlaying() {
        return musicThread != null && musicThread.isAlive();
    }

    @SuppressWarnings("unused")
    public float getVolume() { return this.volume; }

    public void setVolume(float targetVolume) {
        this.volume = Math.clamp(targetVolume, 0.0f, 1.0f);
        if (player != null) {
            try {
                Field deviceField = Player.class.getDeclaredField("audio");
                deviceField.setAccessible(true);
                AudioDevice device = (AudioDevice) deviceField.get(player);

                if (device instanceof JavaSoundAudioDevice jsDevice) {
                    Field sourceField = JavaSoundAudioDevice.class.getDeclaredField("source");
                    sourceField.setAccessible(true);
                    SourceDataLine source = (SourceDataLine) sourceField.get(jsDevice);

                    if (source != null && source.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                        FloatControl gainControl = (FloatControl) source.getControl(FloatControl.Type.MASTER_GAIN);
                        float dB = (float) (Math.log(this.volume <= 0.0f ? 1.0e-4f : this.volume) / Math.log(10.0f) * 20.0f);
                        gainControl.setValue(dB);
                    }
                }
            } catch (Exception ignored) {}
        }
    }
}