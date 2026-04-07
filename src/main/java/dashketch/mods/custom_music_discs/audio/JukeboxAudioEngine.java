package dashketch.mods.custom_music_discs.audio;

import javazoom.jl.player.AudioDevice;
import javazoom.jl.player.JavaSoundAudioDevice;
import javazoom.jl.player.Player;
import org.essentials.custom_background_music.MusicMuter;

import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Field;

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
        if (musicFile != null && musicFile.exists()) {
            musicThread = new Thread(() -> {
                try (FileInputStream fis = new FileInputStream(musicFile)) {
                    player = new Player(new BufferedInputStream(fis));
                    setVolume(volume); // Apply current volume
                    player.play();
                } catch (Exception e) {
                    System.out.println("Jukebox stream closed.");
                }
            });
            musicThread.setDaemon(true);
            musicThread.start();
        }
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