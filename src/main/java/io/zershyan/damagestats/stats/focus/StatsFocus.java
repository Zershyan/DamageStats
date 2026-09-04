package io.zershyan.damagestats.stats.focus;

import io.zershyan.damagestats.stats.DamageRecord;
import io.zershyan.damagestats.stats.EntityRef;
import io.zershyan.damagestats.stats.filter.EntitySelector;
import io.zershyan.damagestats.stats.filter.StatsFilter;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/** 每位玩家唯一的服务端权威焦点，页头与 Overlay 都使用同一份范围。 */
public record StatsFocus(
        long version,
        Optional<EntitySelector> source,
        Optional<EntitySelector> target,
        boolean sourceIsDirectSource
) {
    public static StatsFocus forPlayer(ServerPlayer player) {
        return new StatsFocus(0, Optional.of(new EntitySelector.Instance(EntityRef.of(player))), Optional.empty(), false);
    }

    public StatsFilter filter() {
        return new StatsFilter(source, target, Optional.empty(), Optional.empty(), sourceIsDirectSource);
    }

    public boolean matches(DamageRecord record) {
        return filter().matches(record);
    }

    public StatsFocus next(Optional<EntitySelector> newSource, Optional<EntitySelector> newTarget,
                           boolean newSourceIsDirectSource) {
        return new StatsFocus(version + 1, newSource, newTarget, newSourceIsDirectSource);
    }
}
