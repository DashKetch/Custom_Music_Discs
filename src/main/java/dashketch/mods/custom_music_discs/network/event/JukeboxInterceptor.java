package dashketch.mods.custom_music_discs.network.event;

import dashketch.mods.custom_music_discs.audio.JukeboxAudioEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
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

import java.io.File;

@EventBusSubscriber(modid = "custom_music_discs", bus = EventBusSubscriber.Bus.GAME)
public class JukeboxInterceptor {
    static JukeboxAudioEngine engine = JukeboxAudioEngine.getInstance();

    private static BlockPos playingPos = null;

    @SubscribeEvent
    public static void onJukeboxRightClick(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        ItemStack stack = event.getItemStack();
        BlockState state = level.getBlockState(pos);

        if (state.is(Blocks.JUKEBOX)) {
            // 1. If it HAS a record, do NOTHING.
            // Let Vanilla handle the ejection.
            if (state.getValue(JukeboxBlock.HAS_RECORD)) {
                engine.stop();
            }

            // 2. INSERTION LOGIC
            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
            if (customData != null && customData.copyTag().contains("SelectedSong")) {
                String songName = customData.copyTag().getString("SelectedSong");

                if (level.isClientSide) {
                    // Stop any current jukebox audio before starting new one
                    JukeboxAudioEngine.getInstance().stop();

                    File musicFile = resolveMusicFile(songName);
                    JukeboxAudioEngine.getInstance().play(musicFile);
                    playingPos = pos;
                    event.getEntity().displayClientMessage(Component.literal("§bNow playing: " + songName.replace(".mp3", "")), true);
                } else {
                    if (level.getBlockEntity(pos) instanceof JukeboxBlockEntity jukebox) {
                        // Set the item and update the block state
                        jukebox.setTheItem(stack.copy());
                        level.setBlock(pos, state.setValue(JukeboxBlock.HAS_RECORD, true), 3);

                        if (!event.getEntity().isCreative()) {
                            stack.shrink(1);
                        }
                    }
                }
                // Cancel the event so vanilla doesn't try to treat it like a normal record
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
            }
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

        // If the engine stopped itself (song finished), clear the position
        if (!engine.isPlaying()) {
            playingPos = null;
            return;
        }

        // Distance Check / Volume Fading
        if (mc.player != null) {
            double distSq = mc.player.distanceToSqr(playingPos.getX() + 0.5, playingPos.getY() + 0.5, playingPos.getZ() + 0.5);
            if (distSq > 4096) {
                engine.stop();
                playingPos = null;
            } else {
                float volume = (float) Math.max(0, 1.0 - (Math.sqrt(distSq) / 64.0));
                engine.setVolume(volume);
            }
        }
    }

    private static File resolveMusicFile(String fileName) {
        Minecraft mc = Minecraft.getInstance();
        File mcDir = mc.gameDirectory;
        if (mc.getSingleplayerServer() != null) {
            String worldName = mc.getSingleplayerServer().getWorldData().getLevelName();
            return new File(mcDir, "saves/" + worldName + "/config/uploaded_music/" + fileName);
        }
        return new File(mcDir, "config/uploaded_music/" + fileName);
    }
}