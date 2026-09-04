package io.zershyan.damagestats.stats.view;

import io.zershyan.damagestats.stats.filter.StatsFilter;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 服务器命令导出的完整 DTO。它不再作为客户端网络载荷，GUI 使用受限的图表分页协议。
 */
public record StatsSnapshot(
        Component subjectName,
        StatsFilter appliedFilter,
        List<Component> filterLabels,
        StatsView session,
        StatsView lifetime,
        List<SessionView> history
) {}
