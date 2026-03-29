package dashketch.mods.custom_music_discs.network.event;

import dashketch.mods.custom_music_discs.audio.JukeboxAudioEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
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

import static dashketch.mods.custom_music_discs.Custom_music_discs.LOGGER;

// use the GAME bus for ticks and interactions
@EventBusSubscriber(modid = "custom_music_discs", bus = EventBusSubscriber.Bus.GAME)
public class JukeboxInterceptor {

    // Use a Volatile static to ensure visibility across different threads/instances
    private static volatile BlockPos playingPos = null;

    @SubscribeEvent
    public static void onJukeboxRightClick(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        ItemStack stack = event.getItemStack();

        if (level.getBlockState(pos).is(Blocks.JUKEBOX)) {
            // Ejection check
            if (level.getBlockState(pos).getValue(JukeboxBlock.HAS_RECORD)) return;

            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
            if (customData != null && customData.copyTag().contains("SelectedSong")) {
                String songName = customData.copyTag().getString("SelectedSong");

                if (level.isClientSide) {
                    LOGGER.info("DEBUG: RIGHT-CLICK DETECTED. Setting playingPos to: {}", pos);
                    JukeboxAudioEngine.getInstance().stop();
                    JukeboxAudioEngine.getInstance().play(resolveMusicFile(songName));
                    playingPos = pos;
                } else {
                    if (level.getBlockEntity(pos) instanceof JukeboxBlockEntity jukebox) {
                        jukebox.setTheItem(stack.copy());
                        level.setBlock(pos, level.getBlockState(pos).setValue(JukeboxBlock.HAS_RECORD, true), 3);
                        if (!event.getEntity().isCreative()) stack.shrink(1);
                    }
                }
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        // --- EMERGENCY DIAGNOSIS ---
        // If you don't see this in your console every second, the event isn't registered!
        if (Minecraft.getInstance().level != null && Minecraft.getInstance().level.getGameTime() % 20 == 0) {
            LOGGER.info("DEBUG: Tick is alive. playingPos is: {}", playingPos == null ? "NULL" : playingPos);
        }

        if (playingPos == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        BlockState state = mc.level.getBlockState(playingPos);
        JukeboxAudioEngine engine = JukeboxAudioEngine.getInstance();

        // 1. Check if the block was tampered with
        if (!state.is(Blocks.JUKEBOX) || !state.getValue(JukeboxBlock.HAS_RECORD)) {
            LOGGER.info("DEBUG: Jukebox changed state. Stopping audio.");
            engine.stop();
            playingPos = null;
            return;
        }

        // 2. Proximity Math
        double dSq = mc.player.distanceToSqr(playingPos.getX() + 0.5, playingPos.getY() + 0.5, playingPos.getZ() + 0.5);
        float master = mc.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER);
        float records = mc.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.RECORDS);

        float dist = (float) Math.sqrt(dSq);
        float fade = Math.max(0.0f, 1.0f - (dist / 64.0f));

        engine.setVolume(fade * master * records);
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

    /*
    TODO: Test fading/fix fading
     */
}