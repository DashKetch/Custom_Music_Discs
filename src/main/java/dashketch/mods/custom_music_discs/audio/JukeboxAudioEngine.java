package dashketch.mods.custom_music_discs.audio;

import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.AudioDevice;
import javazoom.jl.player.JavaSoundAudioDevice;
import javazoom.jl.player.Player;
import org.essentials.custom_background_music.MusicMuter;
import dashketch.mods.custom_music_discs.client.AudioDeviceSync;

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
        return new JavaSoundAudioDevice() {
            @Override
            protected void createSource() throws JavaLayerException {
                // 1. Let JLayer run its default setup so all internal states/buffers are fully initialized
                super.createSource();

                try {
                    Mixer.Info mixerInfo = AudioDeviceSync.getMinecraftSelectedMixer();

                    // Check if system defualt device
                    if (mixerInfo == null) {
                        return;
                    }

                    LOGGER.info("[CUSTOM DISCS] Intercepting pipeline for hardware mixer: {}", mixerInfo.getName());

                    // 2. Grab JLayer's internal private 'source' field using reflection
                    Field sourceField = JavaSoundAudioDevice.class.getDeclaredField("source");
                    sourceField.setAccessible(true);
                    SourceDataLine defaultLine = (SourceDataLine) sourceField.get(this);

                    // 3. Grab JLayer's internal format
                    AudioFormat fmt = getAudioFormat();
                    DataLine.Info info = new DataLine.Info(SourceDataLine.class, fmt);
                    Mixer mixer = AudioSystem.getMixer(mixerInfo);

                    if (mixer != null && mixer.isLineSupported(info)) {
                        // Close the default system line before swapping
                        if (defaultLine != null) {
                            try { defaultLine.close(); } catch (Exception ignored) {}
                        }

                        // Open the new hardware line using the buffer size JLayer calculated
                        SourceDataLine hardwareLine = (SourceDataLine) mixer.getLine(info);

                        // Pull the calculated buffer size directly from the old line properties if available
                        int bufferSize = (defaultLine != null) ? defaultLine.getBufferSize() : AudioSystem.NOT_SPECIFIED;

                        if (bufferSize > 0) {
                            hardwareLine.open(fmt, bufferSize);
                        } else {
                            hardwareLine.open(fmt);
                        }

                        hardwareLine.start();

                        // 4. Inject the hardware-bound line back into JLayer
                        sourceField.set(this, hardwareLine);
                        LOGGER.info("[CUSTOM DISCS] Audio pipeline cleanly bound to hardware device.");
                    } else {
                        LOGGER.warn("[CUSTOM DISCS] Target mixer does not support this format. Keeping default line.");
                    }

                } catch (Exception e) {
                    LOGGER.error("[CUSTOM DISCS] Hardware routing failed, staying on default engine track.", e);
                }
            }
        };
    }

    public void play(File musicFile) {
        try {
            MusicMuter.muteMinecraftMusic();
        } catch (Exception e) {
            LOGGER.warn("Could not mute the music!", e);
            return;
        }

        stop();

        if (musicFile == null || !musicFile.exists()) {
            LOGGER.warn("[CUSTOM DISCS FATAL] File missing or null!");
            return;
        }

        LOGGER.info("[CUSTOM DISCS] Starting thread for: {}", musicFile.getName());

        musicThread = new Thread(() -> {
            try (FileInputStream fis = new FileInputStream(musicFile)) {
                AudioDevice customDevice = createSyncedDevice();
                player = new javazoom.jl.player.Player(new java.io.BufferedInputStream(fis), customDevice);

                setVolume(volume);
                player.play();
            } catch (Exception e) {
                LOGGER.warn("[CUSTOM DISCS] Playback failed: {}", e.getMessage());
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
                        dB = Math.clamp(dB, gainControl.getMinimum(), gainControl.getMaximum());
                        gainControl.setValue(dB);
                    }
                }
            } catch (Exception ignored) {}
        }
    }
}