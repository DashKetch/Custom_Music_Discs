package dashketch.mods.custom_music_discs.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.io.FileOutputStream;

import static dashketch.mods.custom_music_discs.Custom_music_discs.LOGGER;

public class ClientMusicReceiver {
    private static java.util.UUID activeSessionId = null;
    private static File downloadingFile = null;
    private static FileOutputStream fos = null;
    private static int chunksReceived = 0;

    public static synchronized void handleStartPacket(java.util.UUID sessionId, String fileName) {
        try {
            cleanup();
            activeSessionId = sessionId;
            chunksReceived = 0;

            File cacheDir = new File(Minecraft.getInstance().gameDirectory, "config/uploaded_music/client_cache");
            if (!cacheDir.exists() && !cacheDir.mkdirs()) {
                LOGGER.warn("[CLIENT] Could not create cache directory!");
            }

            // Write directly to the final file destination—no .tmp extensions
            downloadingFile = new File(cacheDir, fileName);
            fos = new FileOutputStream(downloadingFile);

            LOGGER.info("[CLIENT] Starting direct download for: {}", fileName);
        } catch (Exception e) {
            LOGGER.warn("[CLIENT] Failed to initialize file buffer: {}", e.getMessage());
        }
    }

    public static synchronized void handleChunkPacket(java.util.UUID sessionId, int chunkIndex, byte[] data, int totalChunks) {
        if (activeSessionId == null || !activeSessionId.equals(sessionId) || fos == null) return;

        try {
            fos.write(data);
            chunksReceived++;

            // When the final chunk arrives, close the stream
            if (chunksReceived >= totalChunks) {
                fos.flush();
                fos.close();
                fos = null;

                String cleanName = downloadingFile.getName().replace(".mp3", "");
                LOGGER.info("[CLIENT] Successfully downloaded and cached: {}", downloadingFile.getName());

                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.displayClientMessage(
                            Component.literal("§a[Custom Discs] Downloaded new song: " + cleanName), false);
                }

                cleanup();
            }
        } catch (Exception e) {
            LOGGER.warn("[CLIENT] Error writing chunk {}: {}", chunkIndex, e.getMessage());
            cleanup();
        }
    }

    public static void cleanup() {
        try { if (fos != null) fos.close(); } catch (Exception ignored) {}
        fos = null;
        activeSessionId = null;
        chunksReceived = 0;
    }
}