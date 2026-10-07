package com.anfioo.howtocook.common.constant;

/**
 * Redis Key 命名常量（优化 2.1）：统一前缀与命名空间，消除散落魔法字符串。
 * <p>约定：所有 key 以 {@code howtocook:} 为命名空间；具体 key 用静态方法按参数拼接。</p>
 */
public final class RedisKeys {

    private RedisKeys() {
    }

    /** 统一命名空间 */
    private static final String NS = "howtocook:";

    /** 对话频次限流计数（每用户每分钟窗口） */
    private static final String CHAT_RATE = NS + "chat:rate:";

    /** 文档索引任务幂等锁 */
    private static final String INDEX_TASK_LOCK = NS + "index:task:lock:";

    /** 对话限流计数键：howtocook:chat:rate:{userId}:{分钟窗口} */
    public static String chatRate(long userId, long minuteWindow) {
        return CHAT_RATE + userId + ":" + minuteWindow;
    }

    /** 索引任务幂等锁键：howtocook:index:task:lock:{taskNo} */
    public static String indexTaskLock(String taskNo) {
        return INDEX_TASK_LOCK + taskNo;
    }
}
