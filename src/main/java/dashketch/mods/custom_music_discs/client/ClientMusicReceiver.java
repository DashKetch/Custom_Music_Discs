package dashketch.mods.custom_music_discs.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import static dashketch.mods.custom_music_discs.Custom_music_discs.LOGGER;

public class ClientMusicReceiver {
    private static UUID activeSessionId = null;
    private static File downloadingFile = null;
    private static FileOutputStream fos = null;
    private static int chunksReceived = 0;

    public static synchronized void handleStartPacket(UUID sessionId, String fileName) {
        try {
            cleanup();
            activeSessionId = sessionId;
            chunksReceived = 0;

            File cacheDir = new File(Minecraft.getInstance().gameDirectory, "config/uploaded_music/client_cache");
            if (!cacheDir.exists() && !cacheDir.mkdirs()) {
                LOGGER.warn("[CLIENT] Could not create cache directory!");
            }

            downloadingFile = new File(cacheDir, fileName + ".tmp");
            fos = new FileOutputStream(downloadingFile);

            LOGGER.info("[CLIENT] Starting background download for: {}", fileName);
        } catch (Exception e) {
            LOGGER.warn("[CLIENT] Failed to initialize file buffer: {}", e.getMessage());
        }
    }

    public static synchronized void handleChunkPacket(UUID sessionId, int chunkIndex, byte[] data, int totalChunks) {
        if (activeSessionId == null || !activeSessionId.equals(sessionId) || fos == null) return;

        try {
            fos.write(data);
            chunksReceived++;

            // When the final chunk arrives
            if (chunksReceived >= totalChunks) {
                // 1. Flush and explicitly close the stream to release the handle
                try {
                    fos.flush();
                } catch (Exception ignored) {}
                try {
                    fos.close();
                } catch (Exception ignored) {}
                fos = null;

                File sourceFile = downloadingFile;
                String cleanName = sourceFile.getName().replace(".tmp", "");
                File targetFile = new File(sourceFile.getParentFile(), cleanName);

                Path sourcePath = sourceFile.toPath();
                Path targetPath = targetFile.toPath();

                // 2. Battle-tested retry loop to beat the OS filesystem lock
                boolean moved = false;
                for (int i = 0; i < 5; i++) {
                    try {
                        Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                        moved = true;
                        break; // Success! Break out of the loop
                    } catch (Exception e) {
                        // Sleep for 20ms to let the OS release the handle
                        try { Thread.sleep(20); } catch (InterruptedException ignored) {}
                    }
                }

                // Fallback: If NIO Files.move still fails, try standard IO renameTo
                if (!moved && sourceFile.renameTo(targetFile)) {
                    moved = true;
                }

                if (moved) {
                    LOGGER.info("[CLIENT] Successfully cached and renamed: {}", targetFile.getName());

                    if (Minecraft.getInstance().player != null) {
                        Minecraft.getInstance().player.displayClientMessage(
                                Component.literal("§a[Custom Discs] Downloaded new song: " + cleanName), false);
                    }
                } else {
                    LOGGER.error("[CLIENT] Failed to rename temporary file due to a persistent OS lock: {}", sourceFile.getName());
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