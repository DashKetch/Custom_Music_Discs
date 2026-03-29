package dashketch.mods.custom_music_discs.audio;

import javazoom.jl.player.AudioDevice;
import javazoom.jl.player.JavaSoundAudioDevice;
import javazoom.jl.player.Player;

import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Field;

public class JukeboxAudioEngine {
    private static final JukeboxAudioEngine INSTANCE = new JukeboxAudioEngine();
    public static boolean FORCE_PLAYING_FLAG = false;
    private Player player;
    private Thread musicThread;
    private float volume = 1.0f;

    public static JukeboxAudioEngine getInstance() {
        return INSTANCE;
    }

    public void play(File musicFile) {
        stop();
        FORCE_PLAYING_FLAG = true; // Set flag when starting
        musicThread = new Thread(() -> {
            try (FileInputStream fis = new FileInputStream(musicFile)) {
                player = new Player(new BufferedInputStream(fis));
                setVolume(this.volume);
                player.play();
            } catch (Exception e) {
                FORCE_PLAYING_FLAG = false;
            } finally {
                FORCE_PLAYING_FLAG = false; // Clear flag when song ends
            }
        });
        musicThread.setDaemon(true);
        musicThread.start();
    }

    public void stop() {
        FORCE_PLAYING_FLAG = false; // Clear flag
        if (player != null) { player.close(); player = null; }
        if (musicThread != null) { musicThread.interrupt(); musicThread = null; }
    }

    public boolean isPlaying() {
        return player != null && musicThread != null && musicThread.isAlive();
    }

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

                        float dB = getDB(gainControl, this.volume);

                        gainControl.setValue(dB);
                    }
                }
            } catch (Exception e) {
                // If it fails, print it to the console so we aren't flying blind!
                System.err.println("Failed to set Jukebox volume: " + e.getMessage());
            }
        }
    }

    private static float getDB(FloatControl gainControl, float volume) {
        float dB;
        if (volume <= 0.0f) {
            // If volume is 0, drop it to the absolute minimum the hardware allows (mute)
            dB = gainControl.getMinimum();
        } else {
            // Standard amplitude to decibel conversion
            dB = (float) (Math.log10(volume) * 20.0f);
        }

        // CLAMP the value so we never throw an IllegalArgumentException
        dB = Math.clamp(dB, gainControl.getMinimum(), gainControl.getMaximum());
        return dB;
    }
}