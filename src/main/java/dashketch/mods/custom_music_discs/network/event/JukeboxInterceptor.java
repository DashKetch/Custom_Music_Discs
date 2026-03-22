package dashketch.mods.custom_music_discs.network.event;

import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.essentials.custom_background_music.AudioManager; // Importing the other mod's class
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.io.File;

@EventBusSubscriber(modid = "custom_music_discs", bus = EventBusSubscriber.Bus.GAME)
public class JukeboxInterceptor {

    @SubscribeEvent
    public static void onJukeboxRightClick(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        ItemStack stack = event.getItemStack();
        Player player = event.getEntity();

        // 1. Check if the block is a Jukebox and item is a burned disc
        if (level.getBlockState(pos).is(Blocks.JUKEBOX)) {
            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);

            if (customData != null && customData.copyTag().contains("SelectedSong")) {
                String songName = customData.copyTag().getString("SelectedSong");

                if (level.isClientSide) {
                    // 1. Get the managers from the other mod
                    AudioManager am = AudioManager.getInstance();

                    // 2. Resolve the file
                    File musicFile = resolveMusicFile(songName);

                    // 3. IMPORTANT: Stop any existing music/playlists before starting the jukebox
                    am.stop();

                    if (am.loadMusicFile(musicFile)) {
                        am.play();
                    }
                }else {
                    // 4. Server Side: Visual handling
                    if (level.getBlockEntity(pos) instanceof JukeboxBlockEntity jukebox) {
                        jukebox.setTheItem(stack.copy());
                        if (!player.isCreative()) {
                            stack.shrink(1);
                        }
                    }
                }

                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
            }
        }
    }

    private static File resolveMusicFile(String fileName) {
        Minecraft mc = Minecraft.getInstance();
        File mcDir = mc.gameDirectory;
        File uploadDir;

        // Determine if we are in Singleplayer or Multiplayer
        if (mc.getSingleplayerServer() != null) {
            // Singleplayer: files are in the world's config folder
            String worldName = mc.getSingleplayerServer().getWorldData().getLevelName();
            uploadDir = new File(mcDir, "saves/" + worldName + "/config/uploaded_music");
        } else {
            // Multiplayer/Dedicated: files are in the root config folder
            uploadDir = new File(mcDir, "config/uploaded_music");
        }

        return new File(uploadDir, fileName);
    }

    @SubscribeEvent
    public static void onRightClickJukebox(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();

        // If a jukebox is punched (ejecting the disc), stop the custom music
        if (level.getBlockState(pos).is(Blocks.JUKEBOX) && level.isClientSide) {
            AudioManager.getInstance().stop();
        }
    }

    @SubscribeEvent
    public static void onGuiOpen(ScreenEvent.Opening event) {
        // If they try to open the other mod's music player GUI while a disc is playing
        if (event.getNewScreen() instanceof org.essentials.custom_background_music.MusicGuiScreen) {
            //1: Just close it immediately
            event.setCanceled(true);

            //2:let them know they can't use it for Jukebox discs
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§cManual controls disabled for Jukebox discs!"), true);
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        // If the broken block is a Jukebox, stop the music on the client
        if (event.getState().is(Blocks.JUKEBOX)) {
            if (event.getLevel().isClientSide()) {
                AudioManager.getInstance().stop();
            }
        }
    }
}