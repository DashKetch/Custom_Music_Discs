package dashketch.mods.custom_music_discs.client;

import dashketch.mods.custom_music_discs.audio.JukeboxAudioEngine;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;

import static dashketch.mods.custom_music_discs.Custom_music_discs.MODID;

@EventBusSubscriber(modid = MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientAudioNetworkEvents {

    @SubscribeEvent
    public static void onLogOut(ClientPlayerNetworkEvent.LoggingOut event) {
        JukeboxAudioEngine.getInstance().onPlayerDisconnect();
    }

    @SubscribeEvent
    public static void onLogIn(ClientPlayerNetworkEvent.LoggingIn event) {
        JukeboxAudioEngine.getInstance().onPlayerReconnect();
    }
}