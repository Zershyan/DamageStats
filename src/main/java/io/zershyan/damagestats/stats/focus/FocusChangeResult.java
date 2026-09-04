package io.zershyan.damagestats.stats.focus;

/** 焦点设置的服务端裁决；客户端据此显示明确结果，不能把拒绝伪装成成功。 */
public enum FocusChangeResult {
    ACCEPTED,
    SOURCE_NOT_ALLOWED,
    UNKNOWN_SOURCE,
    UNKNOWN_TARGET
}
