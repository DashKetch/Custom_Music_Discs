package dashketch.mods.custom_music_discs.network.event;

import dashketch.mods.custom_music_discs.audio.JukeboxAudioEngine;
import dashketch.mods.custom_music_discs.network.PlayCustomMusicPayload;
import dashketch.mods.custom_music_discs.server.ModConfigs;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.essentials.custom_background_music.AudioManager;

import java.io.File;

import static dashketch.mods.custom_music_discs.Custom_music_discs.LOGGER;
import static dashketch.mods.custom_music_discs.client.override.volume_slider.getJukeboxVolume;

@EventBusSubscriber(modid = "custom_music_discs", bus = EventBusSubscriber.Bus.GAME)
public class JukeboxInterceptor {
    static JukeboxAudioEngine engine = JukeboxAudioEngine.getInstance();
    static AudioManager am = AudioManager.getInstance();
    private static BlockPos playingPos = null;

    @SubscribeEvent
    public static void onJukeboxRightClick(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        ItemStack stack = event.getItemStack();
        BlockState state = level.getBlockState(pos);

        if (state.is(Blocks.JUKEBOX)) {
            // 1. EJECTION LOGIC
            if (state.getValue(JukeboxBlock.HAS_RECORD)) {
                if (level.isClientSide) {
                    engine.stop();
                    playingPos = null;
                }
                return;
            }

            // 2. INSERTION LOGIC
            if (level.isClientSide) {
                am.stop();
            }

            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
            if (customData != null && customData.copyTag().contains("SelectedSong")) {
                String songName = customData.copyTag().getString("SelectedSong");

                // SERVER SIDE: Handle the inventory math, update the block, and Broadcast the song to everyone
                if (!level.isClientSide) {
                    if (level.getBlockEntity(pos) instanceof JukeboxBlockEntity jukebox) {
                        jukebox.setTheItem(stack.copyWithCount(1));
                        level.setBlock(pos, state.setValue(JukeboxBlock.HAS_RECORD, true), 3);
                        stack.shrink(1);

                        // Broadcast to everyone in the same dimension
                        //noinspection DataFlowIssue
                        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
                            //noinspection resource
                            if (player.level() == level) {
                                PacketDistributor.sendToPlayer(player, new PlayCustomMusicPayload(pos, songName));
                            }

                            if (player.hasDisconnected()) {
                                engine.stop();
                            }
                        }
                    }
                }

                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
            }
        }
    }

    // 3. NETWORK PLAYBACK: This runs for every client when they receive the broadcast packet
    public static void handlePlayBroadcast(BlockPos pos, String songName) {
        engine.stop(); // Clean up any old song playing

        File cacheDir = new File(Minecraft.getInstance().gameDirectory, "config/uploaded_music/client_cache");
        File localMusic = new File(cacheDir, songName);

        Minecraft mc = Minecraft.getInstance();

        if (localMusic.exists()) {
            playingPos = pos;
            engine.play(localMusic);
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal("§bNow playing: " + songName.replace(".mp3", "")), true);
            }
        } else {
            // If they don't have the file cached, gently prompt them with the exact command to get it!
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal("§c[!] A custom disc is playing, but you don't have the file! Use /DownloadSong sync " + songName), false);
            }
            LOGGER.warn("[CLIENT] Broadcast requested {}, but it is missing from your local cache.", songName);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || playingPos == null) return;

        BlockState state = mc.level.getBlockState(playingPos);

        if (!state.is(Blocks.JUKEBOX) || !state.getValue(JukeboxBlock.HAS_RECORD)) {
            engine.stop();
            playingPos = null;
            return;
        }

        if (!engine.isPlaying()) {
            playingPos = null;
            return;
        }

        if (mc.player != null && engine.isPlaying()) {
            double dx = mc.player.getX() - (playingPos.getX() + 0.5);
            double dy = mc.player.getY() - (playingPos.getY() + 0.5);
            double dz = mc.player.getZ() - (playingPos.getZ() + 0.5);
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

            float sliderMultiplier = getJukeboxVolume();

            if (ModConfigs.SPEC.isLoaded() && ModConfigs.JUKEBOX_RANGE_BOOL.get()) {
                double maxDistance = ModConfigs.JUKEBOX_RANGE.get() + 16.0;
                double ratio = Math.clamp(distance / maxDistance, 0.0, 1.0);
                float volume = (float) Math.pow(1.0 - ratio, 2) * sliderMultiplier;
                engine.setVolume(volume);
            } else {
                double maxDistance = 64.0 + 16.0;
                double ratio = Math.clamp(distance / maxDistance, 0.0, 1.0);
                float volume = (float) Math.pow(1.0 - ratio, 2) * sliderMultiplier;
                engine.setVolume(volume);
            }
        }
    }
}