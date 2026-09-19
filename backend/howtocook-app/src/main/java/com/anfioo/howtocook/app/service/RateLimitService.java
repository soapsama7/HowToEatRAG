package com.anfioo.howtocook.app.service;

import com.anfioo.howtocook.common.result.BusinessException;
import com.anfioo.howtocook.common.result.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * 对话限流与稳定性兜底（开发文档 §5.5 / Step 4.4）：
 * <ul>
 *   <li>频次限制：每用户每分钟对话次数（Redis INCR 按分钟窗口计数，可配默认 10），超限抛 429；</li>
 *   <li>并发限制：每用户同时进行的对话数（JVM 内信号量，可配默认 3），超限抛 429。</li>
 * </ul>
 * <p>Redis 不可用时频次检查降级放行（仅告警），不影响对话主链路；
 * 单实例部署下 JVM 信号量即为全局并发上限。</p>
 */
@Slf4j
@Service
public class RateLimitService {

    private static final String RATE_KEY_PREFIX = "howtocook:chat:rate:";
    /** 分钟窗口长度（秒） */
    private static final long WINDOW_SECONDS = 60;

    private final StringRedisTemplate redisTemplate;

    /** 每用户每分钟对话次数上限（howtocook.chat.rate-limit-per-minute） */
    @Value("${howtocook.chat.rate-limit-per-minute:10}")
    private int rateLimitPerMinute;

    /** 每用户并发对话上限（howtocook.chat.max-concurrent-per-user） */
    @Value("${howtocook.chat.max-concurrent-per-user:3}")
    private int maxConcurrentPerUser;

    /** 用户 → 并发信号量 */
    private final ConcurrentHashMap<Long, Semaphore> userSemaphores = new ConcurrentHashMap<>();

    public RateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 频次检查：每用户每分钟窗口计数，超限 429（被拒请求同样计数，防止刷接口绕过） */
    public void checkChatRateLimit(long userId) {
        long window = System.currentTimeMillis() / (WINDOW_SECONDS * 1000);
        String key = RATE_KEY_PREFIX + userId + ":" + window;
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                redisTemplate.expire(key, Duration.ofSeconds(WINDOW_SECONDS * 2));
            }
            if (count != null && count > rateLimitPerMinute) {
                throw new BusinessException(ErrorCode.RATE_LIMITED,
                        "对话过于频繁（每分钟最多 " + rateLimitPerMinute + " 次），请稍后再试");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("对话频次检查降级（Redis 不可用）: {}", e.getMessage());
        }
    }

    /** 并发检查：尝试占用一个对话槽位（须在对话结束时 {@link #releaseSlot} 释放） */
    public boolean tryAcquireSlot(long userId) {
        boolean acquired = semaphore(userId).tryAcquire();
        if (!acquired) {
            log.warn("用户并发对话超限: userId={}, max={}", userId, maxConcurrentPerUser);
        }
        return acquired;
    }

    /** 释放对话槽位 */
    public void releaseSlot(long userId) {
        semaphore(userId).release();
    }

    /** 对话入口统一闸门：频次 + 并发检查，未通过抛 429（返回统一错误体） */
    public void acquireChatSlot(long userId) {
        checkChatRateLimit(userId);
        if (!tryAcquireSlot(userId)) {
            throw new BusinessException(ErrorCode.RATE_LIMITED,
                    "并发对话数已达上限（" + maxConcurrentPerUser + "），请等待当前对话完成");
        }
    }

    private Semaphore semaphore(long userId) {
        return userSemaphores.computeIfAbsent(userId,
                k -> new Semaphore(Math.max(1, maxConcurrentPerUser)));
    }
}
