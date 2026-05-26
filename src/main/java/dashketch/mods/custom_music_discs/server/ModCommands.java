package dashketch.mods.custom_music_discs.server;

import com.mojang.brigadier.arguments.StringArgumentType;
import dashketch.mods.custom_music_discs.Custom_music_discs;
import dashketch.mods.custom_music_discs.network.ServerMusicStreamer;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

@EventBusSubscriber(modid = Custom_music_discs.MODID, bus = EventBusSubscriber.Bus.GAME)
public class ModCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("ClearUploads")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            MinecraftServer server = context.getSource().getServer();
                            CommandSourceStack source = context.getSource();
                            clearUploads(server, source);
                            return 1;
                        })
        );

        event.getDispatcher().register(Commands.literal("DownloadSong")
                .then(Commands.literal("song")
                        .then(Commands.argument("filename", StringArgumentType.string())
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    String filename = StringArgumentType.getString(context, "filename");

                                    if (source.getEntity() instanceof ServerPlayer player) {
                                        source.sendSuccess(() -> Component.literal("§eRequesting track: " + filename), false);

                                        // DIRECT CALL: Run directly on the server thread, bypassing packet bounces
                                        MinecraftServer server = source.getServer();
                                        syncSong(server, player, filename);
                                    }
                                    return 1;
                                })
                        )
                )
        );
    }

    public static void syncSong(MinecraftServer server, ServerPlayer player, String filename) {
        Path serverPath = server.getWorldPath(LevelResource.ROOT).resolve("config/uploaded_music");
        File targetFile = serverPath.resolve(filename).toFile();

        if (targetFile.exists()) {
            ServerMusicStreamer.streamFileToPlayer(targetFile, player);
        } else {
            player.sendSystemMessage(Component.literal("§cFile not found on server!"));
        }
    }

    public static void clearUploads(MinecraftServer server, CommandSourceStack source) {
        Path serverPath = server.getWorldPath(LevelResource.ROOT).resolve("config/uploaded_music");
        try {
            if (Files.exists(serverPath)) {
                try (Stream<Path> files = Files.list(serverPath)) {
                    files.forEach(file -> {
                        try {
                            if (!Files.isDirectory(file)) {
                                Files.delete(file);
                            }
                        } catch (IOException e) {
                            Custom_music_discs.LOGGER.error("Failed to delete file: {}", file, e);
                        }
                    });
                }
                source.sendSuccess(() -> Component.literal("§aAll uploaded music files cleared!"), true);
            } else {
                source.sendFailure(Component.literal("§cUpload directory does not exist."));
            }
        } catch (IOException e) {
            Custom_music_discs.LOGGER.error("Could not access server music path", e);
            source.sendFailure(Component.literal("§cAn error occurred while clearing files. Check logs."));
        }
    }
}