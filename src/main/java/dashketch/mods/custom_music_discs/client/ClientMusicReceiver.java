package dashketch.mods.custom_music_discs.client;

import java.io.File;
import java.io.FileOutputStream;
import java.util.UUID;

import dashketch.mods.custom_music_discs.audio.JukeboxAudioEngine;
import net.minecraft.client.Minecraft;

public class ClientMusicReceiver {
    private static UUID activeSessionId = null;
    private static File tempFile = null;
    private static FileOutputStream fos = null;
    private static int chunksReceived = 0;

    public static synchronized void handleStartPacket(UUID sessionId, String fileName) {
        try {
            cleanup(); // Reset and close out previous data if a song was skipped/changed

            activeSessionId = sessionId;
            chunksReceived = 0;

            // Saves inside a dynamic 'client_cache' folder inside the player's local game directory
            File cacheDir = new File(Minecraft.getInstance().gameDirectory, "config/uploaded_music/client_cache");
            if (!cacheDir.exists()) //noinspection ResultOfMethodCallIgnored
                cacheDir.mkdirs();

            tempFile = new File(cacheDir, "temp_" + fileName);
            fos = new FileOutputStream(tempFile);

            System.out.println("[CLIENT] Preparing to receive track: " + fileName);
        } catch (Exception e) {
            System.err.println("[CLIENT] Failed to initialize file buffer: " + e.getMessage());
        }
    }

    public static synchronized void handleChunkPacket(UUID sessionId, int chunkIndex, byte[] data, int totalChunks) {
        if (activeSessionId == null || !activeSessionId.equals(sessionId) || fos == null) return;

        try {
            fos.write(data);
            chunksReceived++;

            if (chunksReceived >= totalChunks) {
                fos.flush();
                fos.close();
                fos = null;

                System.out.println("[CLIENT] File transfer complete! Playing: " + tempFile.getName());

                // Trigger your local audio engine using the newly assembled cache file!
                JukeboxAudioEngine.getInstance().play(tempFile);
            }
        } catch (Exception e) {
            System.err.println("[CLIENT] Error writing chunk " + chunkIndex + ": " + e.getMessage());
            cleanup();
        }
    }

    public static void cleanup() {
        try {
            if (fos != null) {
                fos.close();
                fos = null;
            }
        } catch (Exception ignored) {}
        activeSessionId = null;
        chunksReceived = 0;
    }
}