package io.zershyan.damagestats.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public interface CustomPacketPayload {
    Type<? extends CustomPacketPayload> type();

    final class Type<T extends CustomPacketPayload> {
        private final ResourceLocation id;

        public Type(ResourceLocation id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        public ResourceLocation id() {
            return id;
        }

        @Override
        public boolean equals(Object other) {
            return this == other || other instanceof Type<?> type && id.equals(type.id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }

        @Override
        public String toString() {
            return id.toString();
        }
    }

    interface IPayloadContext {
        @Nullable
        Player player();

        void enqueueWork(Runnable work);
    }
}
