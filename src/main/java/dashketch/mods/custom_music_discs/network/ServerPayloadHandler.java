package dashketch.mods.custom_music_discs.network;

import dashketch.mods.custom_music_discs.item.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.util.Objects;

import static dashketch.mods.custom_music_discs.Custom_music_discs.LOGGER;

public class ServerPayloadHandler {
    public static void handleData(final MusicUploadPayload data, final IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            ItemStack offhandItem = player.getOffhandItem();

            // 1. Validation
            if (!offhandItem.is(ModItems.BLANK_DISC.get())) {
                if (data.isFirstChunk()) {
                    player.sendSystemMessage(Component.literal("§cError: Hold a Blank Disc in your off-hand!"));
                    LOGGER.warn("Player {} must hold a Blank Disc in their off-hand!", player.getName().getString());
                }
                return;
            }

            try {
                // 2. Setup Directory
                Path serverPath = Objects.requireNonNull(player.getServer()).getWorldPath(LevelResource.ROOT).resolve("config/uploaded_music");
                File dir = serverPath.toFile();
                if (!dir.exists()) //noinspection ResultOfMethodCallIgnored
                    dir.mkdirs();

                String safeName = data.fileName().replaceAll("[^a-zA-Z0-9._-]", "_");
                File targetFile = new File(dir, safeName);

                // 3. Write File Chunks
                try (FileOutputStream fos = new FileOutputStream(targetFile, !data.isFirstChunk())) {
                    fos.write(data.data());

                    // 4. Finalize on Last Chunk
                    if (data.isLastChunk()) {
                        offhandItem.set(DataComponents.CUSTOM_NAME, Component.literal("§bBurned Music Disc"));

                        offhandItem.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(
                                java.util.List.of(Component.literal("§7Track: " + safeName))
                        ));

                        offhandItem.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, customData ->
                                customData.update(tag -> tag.putString("SelectedSong", safeName))
                        );

                        offhandItem.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
                        player.sendSystemMessage(Component.literal("§aDisk Burned Successfully!"));
                        player.sendSystemMessage(Component.literal("File name: " + safeName));

                        // PIVOT BROADCAST: Stream the completed file to EVERY player connected to the server
                        LOGGER.info("[SERVER] Broadcast-syncing newly uploaded track: {}", safeName);
                        for (ServerPlayer onlinePlayer : player.getServer().getPlayerList().getPlayers()) {
                            ServerMusicStreamer.streamFileToPlayer(targetFile, onlinePlayer);
                        }
                    }
                }
            } catch (Exception e) {
                if (data.isFirstChunk()) {
                    LOGGER.error("Failed to save music file", e);
                }
            }
        });
    }
}