package dashketch.mods.custom_music_discs.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record MusicChunkPacket(UUID trackSessionId, int chunkIndex, byte[] data, int totalChunks) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MusicChunkPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("custom_music_discs", "music_chunk"));

    public static final StreamCodec<FriendlyByteBuf, MusicChunkPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        // 1. Encode UUID
                        buf.writeLong(packet.trackSessionId().getMostSignificantBits());
                        buf.writeLong(packet.trackSessionId().getLeastSignificantBits());

                        // 2. Encode Chunk Index
                        ByteBufCodecs.VAR_INT.encode(buf, packet.chunkIndex());

                        // 3. Encode Total Chunks (ADDED)
                        ByteBufCodecs.VAR_INT.encode(buf, packet.totalChunks());

                        // 4. Encode Byte Array
                        buf.writeInt(packet.data().length);
                        buf.writeBytes(packet.data());
                    },
                    buf -> {
                        // 1. Decode UUID
                        long msb = buf.readLong();
                        long lsb = buf.readLong();
                        UUID id = new UUID(msb, lsb);

                        // 2. Decode Chunk Index
                        int index = ByteBufCodecs.VAR_INT.decode(buf);

                        // 3. Decode Total Chunks
                        int total = ByteBufCodecs.VAR_INT.decode(buf);

                        // 4. Decode Byte Array
                        int length = buf.readInt();
                        byte[] chunkData = new byte[length];
                        buf.readBytes(chunkData);

                        return new MusicChunkPacket(id, index, chunkData, total);
                    }
            );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}