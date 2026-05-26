package dashketch.mods.custom_music_discs.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record PlayCustomMusicPayload(BlockPos pos, String songName) implements CustomPacketPayload {
    public static final Type<PlayCustomMusicPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("custom_music_discs", "play_custom_music"));

    public static final StreamCodec<FriendlyByteBuf, PlayCustomMusicPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBlockPos(payload.pos);
                buf.writeUtf(payload.songName);
            },
            buf -> new PlayCustomMusicPayload(buf.readBlockPos(), buf.readUtf())
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}