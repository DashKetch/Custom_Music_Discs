package dashketch.mods.custom_music_discs.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record MusicTransferStartPacket(UUID trackSessionId, String fileName, int totalChunks) implements CustomPacketPayload {

    // Define the unique network ID for this packet
    public static final CustomPacketPayload.Type<MusicTransferStartPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("custom_music_discs", "music_start"));

    public static final StreamCodec<FriendlyByteBuf, MusicTransferStartPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        buf.writeLong(packet.trackSessionId().getMostSignificantBits());
                        buf.writeLong(packet.trackSessionId().getLeastSignificantBits());
                        ByteBufCodecs.STRING_UTF8.encode(buf, packet.fileName());
                        ByteBufCodecs.VAR_INT.encode(buf, packet.totalChunks());
                    },
                    buf -> {
                        long msb = buf.readLong();
                        long lsb = buf.readLong();
                        UUID id = new UUID(msb, lsb);
                        String name = ByteBufCodecs.STRING_UTF8.decode(buf);
                        int chunks = ByteBufCodecs.VAR_INT.decode(buf);
                        return new MusicTransferStartPacket(id, name, chunks);
                    }
            );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}