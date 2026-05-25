package dashketch.mods.custom_music_discs.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record MusicDownloadRequestPayload(String fileName) implements CustomPacketPayload {
    public static final Type<MusicDownloadRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("custom_music_discs", "music_download_request"));

    public static final StreamCodec<FriendlyByteBuf, MusicDownloadRequestPayload> CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeUtf(payload.fileName),
            buf -> new MusicDownloadRequestPayload(buf.readUtf())
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}