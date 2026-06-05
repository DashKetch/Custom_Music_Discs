package dashketch.mods.custom_music_discs.audio;

import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.AudioDevice;
import javazoom.jl.player.JavaSoundAudioDevice;
import javazoom.jl.player.Player;
import org.essentials.custom_background_music.MusicMuter;
import org.essentials.custom_background_music.TrackableInputStream;
import dashketch.mods.custom_music_discs.client.AudioDeviceSync;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import javax.sound.sampled.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.BufferedInputStream;
import java.lang.reflect.Field;

import static dashketch.mods.custom_music_discs.Custom_music_discs.LOGGER;

public class JukeboxAudioEngine {
    private static final JukeboxAudioEngine INSTANCE = new JukeboxAudioEngine();
    private Player player;
    private Thread musicThread;
    private float volume = 1.0f;
    private TrackableInputStream trackableStream;

    // Server tracking states
    private File currentMusicFile;
    private String lastServerIp = null;
    private boolean isPausedByDisconnect = false;

    // Your stream-based pause variables
    private long pauseLocation = 0;

    public static JukeboxAudioEngine getInstance() {
        return INSTANCE;
    }

    private String getCurrentServerAddress() {
        ServerData serverData = Minecraft.getInstance().getCurrentServer();
        if (serverData != null) {
            return serverData.ip;
        }
        if (Minecraft.getInstance().isLocalServer()) {
            return "singleplayer";
        }
        return "none";
    }

    private AudioDevice createSyncedDevice() {
        return new JavaSoundAudioDevice() {
            @Override
            protected void createSource() throws JavaLayerException {
                super.createSource();
                try {
                    Mixer.Info mixerInfo = AudioDeviceSync.getMinecraftSelectedMixer();
                    if (mixerInfo == null) return;

                    Field sourceField = JavaSoundAudioDevice.class.getDeclaredField("source");
                    sourceField.setAccessible(true);
                    SourceDataLine defaultLine = (SourceDataLine) sourceField.get(this);

                    AudioFormat fmt = getAudioFormat();
                    DataLine.Info info = new DataLine.Info(SourceDataLine.class, fmt);
                    Mixer mixer = AudioSystem.getMixer(mixerInfo);

                    if (mixer != null && mixer.isLineSupported(info)) {
                        if (defaultLine != null) {
                            try { defaultLine.close(); } catch (Exception ignored) {}
                        }

                        SourceDataLine hardwareLine = (SourceDataLine) mixer.getLine(info);
                        int bufferSize = (defaultLine != null) ? defaultLine.getBufferSize() : AudioSystem.NOT_SPECIFIED;

                        if (bufferSize > 0) {
                            hardwareLine.open(fmt, bufferSize);
                        } else {
                            hardwareLine.open(fmt);
                        }
                        hardwareLine.start();

                        sourceField.set(this, hardwareLine);
                    }
                } catch (Exception e) {
                    LOGGER.error("[CUSTOM DISCS] Hardware routing failed.", e);
                }
            }
        };
    }

    public synchronized void play(File musicFile) {
        if (musicFile == null || !musicFile.exists() || isPlaying()) return;

        this.currentMusicFile = musicFile;
        this.lastServerIp = getCurrentServerAddress();

        try {
            MusicMuter.muteMinecraftMusic();
        } catch (Exception e) {
            LOGGER.warn("Could not mute vanilla music!", e);
        }

        stopPlaybackThreads();

        musicThread = new Thread(() -> {
            try (FileInputStream fis = new FileInputStream(musicFile)) {
                // Apply your pause location byte skipping logic
                if (pauseLocation > 0) {
                    long skipped = fis.skip(pauseLocation);
                    if (skipped < pauseLocation) pauseLocation = skipped;
                }

                trackableStream = new TrackableInputStream(new BufferedInputStream(fis));
                AudioDevice customDevice = createSyncedDevice();

                player = new Player(trackableStream, customDevice);
                isPausedByDisconnect = false;

                setVolume(this.volume);
                player.play();

                if (player != null && player.isComplete()) {
                    stop();
                }
            } catch (Exception e) {
                LOGGER.warn("[CUSTOM DISCS] Audio stream closed or ended.");
            }
        });
        musicThread.setDaemon(true);
        musicThread.start();
    }

    // Leverages player's exact pause location logic on disconnect
    public synchronized void onPlayerDisconnect() {
        if (isPlaying()) {
            LOGGER.info("[CUSTOM DISCS] Player disconnected. Storing byte offset marker.");

            this.isPausedByDisconnect = true;

            if (trackableStream != null) {
                this.pauseLocation += trackableStream.getBytesRead();
            }

            stopPlaybackThreads();
            MusicMuter.unmuteMinecraftMusic();
        }
    }

    public void onPlayerReconnect() {
        String currentServer = getCurrentServerAddress();

        if (isPausedByDisconnect && currentMusicFile != null && currentMusicFile.exists()) {
            if (currentServer.equals(lastServerIp)) {
                LOGGER.info("[CUSTOM DISCS] Reconnected to same server. Resuming track via byte-offset.");
                play(currentMusicFile);
            } else {
                resetEngineCache();
            }
        } else {
            resetEngineCache();
        }
    }

    private void stopPlaybackThreads() {
        if (player != null) {
            player.close();
            player = null;
        }
        trackableStream = null;
        if (musicThread != null) {
            musicThread.interrupt();
            musicThread = null;
        }
    }

    public synchronized void stop() {
        stopPlaybackThreads();
        resetEngineCache();
        MusicMuter.unmuteMinecraftMusic();
    }

    private void resetEngineCache() {
        this.currentMusicFile = null;
        this.lastServerIp = null;
        this.isPausedByDisconnect = false;
        this.pauseLocation = 0; // Wipe the byte pointer
    }

    public boolean isPlaying() {
        return player != null && musicThread != null && musicThread.isAlive() && !isPausedByDisconnect;
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
                        float dB = (float) (Math.log(this.volume <= 0.0f ? 1.0e-4f : this.volume) / Math.log(10.0f) * 20.0f);
                        dB = Math.clamp(dB, gainControl.getMinimum(), gainControl.getMaximum());
                        gainControl.setValue(dB);
                    }
                }
            } catch (Exception ignored) {}
        }
    }
}