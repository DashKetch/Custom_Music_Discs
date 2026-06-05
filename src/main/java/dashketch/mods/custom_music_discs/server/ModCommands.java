package dashketch.mods.custom_music_discs.server;

import com.mojang.brigadier.arguments.StringArgumentType;
import dashketch.mods.custom_music_discs.Custom_music_discs;
import dashketch.mods.custom_music_discs.network.ServerMusicStreamer;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
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
import java.util.List;
import java.util.stream.Stream;

@EventBusSubscriber(modid = Custom_music_discs.MODID, bus = EventBusSubscriber.Bus.GAME)
public class ModCommands {

    private static List<String> files(Path path) {
        if (!Files.exists(path) || !Files.isDirectory(path)) {
            return List.of(); // Return an empty list if directory doesn't exist
        }

        try (Stream<Path> stream = Files.list(path)) {
            return stream
                    .filter(Files::isRegularFile)          // Ignore subfolders, keep only files
                    .map(p -> p.getFileName().toString())  // Convert Path object to String filename
                    .toList();                             // Collect into an immutable List<String>
        } catch (IOException e) {
            Custom_music_discs.LOGGER.error("Failed to read upload directory for command suggestions", e);
            return List.of(); // Fallback to an empty list on error to prevent crashes
        }
    }


    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("clearuploads")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            MinecraftServer server = context.getSource().getServer();
                            CommandSourceStack source = context.getSource();
                            clearUploads(server, source);
                            return 1;
                        })
        );

        event.getDispatcher().register(Commands.literal("downloadsong")
                .requires(source -> source.hasPermission(1))
                .then(Commands.literal("song")
                        .then(Commands.argument("filename", StringArgumentType.string())
                                .suggests((context, builder) -> (SharedSuggestionProvider.suggest((files(context.getSource().getServer().getWorldPath(LevelResource.ROOT).resolve("config/uploaded_music"))), builder))))
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