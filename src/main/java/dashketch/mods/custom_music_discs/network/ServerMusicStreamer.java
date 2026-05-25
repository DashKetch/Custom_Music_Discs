package dashketch.mods.custom_music_discs.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.UUID;

public class ServerMusicStreamer {

    private static final int CHUNK_SIZE = 32768; // 32 KB chunks

    public static void streamFileToPlayer(File file, ServerPlayer player) {
        if (file == null || !file.exists()) return;

        Thread streamerThread = new Thread(() -> {
            UUID sessionId = UUID.randomUUID();
            long fileSize = file.length();
            int totalChunks = (int) Math.ceil((double) fileSize / CHUNK_SIZE);

            // This will now compile flawlessly!
            PacketDistributor.sendToPlayer(player, new MusicTransferStartPacket(sessionId, file.getName(), totalChunks));

            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[CHUNK_SIZE];
                int bytesRead;
                int chunkIndex = 0;

                while ((bytesRead = fis.read(buffer)) != -1) {
                    byte[] payload = (bytesRead == CHUNK_SIZE) ? buffer : Arrays.copyOf(buffer, bytesRead);

                    PacketDistributor.sendToPlayer(player, new MusicChunkPacket(sessionId, chunkIndex, payload, CHUNK_SIZE));
                    chunkIndex++;

                    //noinspection BusyWait
                    Thread.sleep(20);
                }
                System.out.println("[SERVER] Finished streaming music file: " + file.getName());
            } catch (Exception e) {
                System.err.println("[SERVER] Error streaming music file: " + e.getMessage());
            }
        });

        streamerThread.setDaemon(true);
        streamerThread.start();
    }
}