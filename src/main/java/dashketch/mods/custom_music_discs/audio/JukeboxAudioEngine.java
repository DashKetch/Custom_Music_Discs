package dashketch.mods.custom_music_discs.audio;

import javazoom.jl.player.AudioDevice;
import javazoom.jl.player.JavaSoundAudioDevice;
import javazoom.jl.player.Player;
import org.essentials.custom_background_music.MusicMuter;

import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;
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

    public void play(File musicFile) {
        MusicMuter.muteMinecraftMusic();
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

        LOGGER.warn("[CUSTOM DISCS] Successfully found file, starting thread for: {}", musicFile.getName());

        musicThread = new Thread(() -> {
            // 2. ATTEMPT 1: Play instantly (No Thread.sleep to mess up tick logic)
            try (FileInputStream fis = new FileInputStream(musicFile)) {
                player = new javazoom.jl.player.Player(new java.io.BufferedInputStream(fis));
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