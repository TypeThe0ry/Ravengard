package net.swofty.type.game.replay;

import lombok.Getter;

@Getter
public enum ReplayError {
    UNSUPPORTED_CODEC(3, "不支持的编解码器 {}"),
    REPLAY_NOT_FOUND(404, "未找到回放"),
    JAVA_EXCEPTION(500, "未知异常"),
    REPLAY_INCOMPLETE(501, "回放不完整"),
    ;

    private final int code;
    private final String message;

    ReplayError(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public String format() {
        return "ERROR-" + this.code + " - " + this.message;
    }
}
