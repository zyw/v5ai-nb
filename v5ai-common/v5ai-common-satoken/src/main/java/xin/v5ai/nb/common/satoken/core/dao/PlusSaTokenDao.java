package xin.v5ai.nb.common.satoken.core.dao;

import cn.dev33.satoken.dao.auto.SaTokenDaoBySessionFollowObject;
import cn.dev33.satoken.util.SaFoxUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import xin.v5ai.nb.common.core.utils.StringUtils;
import xin.v5ai.nb.common.redis.utils.RedisUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Sa-Token持久层接口(使用框架自带RedisUtils实现 协议统一)
 * <p>
 * 采用 caffeine + redis 多级缓存 优化并发查询效率
 * <p>
 * SaTokenDaoBySessionFollowObject 是 SaTokenDao 子集简化了session方法处理
 *
 * @author Lion Li
 */
@Slf4j
public class PlusSaTokenDao implements SaTokenDaoBySessionFollowObject {

    private static final Cache<String, Object> CAFFEINE = Caffeine.newBuilder()
        // 设置最后一次写入或访问后经过固定时间过期
        .expireAfterWrite(5, TimeUnit.SECONDS)
        // 初始的缓存空间大小
        .initialCapacity(100)
        // 缓存的最大条数
        .maximumSize(1000)
        .build();

    /**
     * 获取Value，如无返空
     */
    @Override
    public String get(String key) {
        return getCacheValue(key);
    }

    /**
     * 写入Value，并设定存活时间 (单位: 秒)
     */
    @Override
    public void set(String key, String value, long timeout) {
        writeValue(key, value, timeout);
    }

    /**
     * 修修改指定key-value键值对 (过期时间不变)
     */
    @Override
    public void update(String key, String value) {
        if (RedisUtils.hasKey(key)) {
            RedisUtils.setCacheObject(key, value, true);
            invalidate(key);
        }
    }

    /**
     * 删除Value
     */
    @Override
    public void delete(String key) {
        if (RedisUtils.deleteObject(key)) {
            invalidate(key);
        }
    }

    /**
     * 获取Value的剩余存活时间 (单位: 秒)
     */
    @Override
    public long getTimeout(String key) {
        return toTimeoutSeconds(RedisUtils.getTimeToLive(key));
    }

    /**
     * 修改Value的剩余存活时间 (单位: 秒)
     */
    @Override
    public void updateTimeout(String key, long timeout) {
        RedisUtils.expire(key, Duration.ofSeconds(timeout));
    }


    /**
     * 获取Object，如无返空
     */
    @Override
    public Object getObject(String key) {
        return getCacheValue(key);
    }

    /**
     * 获取 Object (指定反序列化类型)，如无返空
     *
     * @param key 键名称
     * @return object
     */
    @Override
    public <T> T getObject(String key, Class<T> classType) {
        return classType.cast(getCacheValue(key));
    }

    /**
     * 写入Object，并设定存活时间 (单位: 秒)
     */
    @Override
    public void setObject(String key, Object object, long timeout) {
        writeValue(key, object, timeout);
    }

    /**
     * 更新Object (过期时间不变)
     */
    @Override
    public void updateObject(String key, Object object) {
        if (RedisUtils.hasKey(key)) {
            RedisUtils.setCacheObject(key, object, true);
            invalidate(key);
        }
    }

    /**
     * 删除Object
     */
    @Override
    public void deleteObject(String key) {
        if (RedisUtils.deleteObject(key)) {
            invalidate(key);
        }
    }

    /**
     * 获取Object的剩余存活时间 (单位: 秒)
     */
    @Override
    public long getObjectTimeout(String key) {
        return toTimeoutSeconds(RedisUtils.getTimeToLive(key));
    }

    /**
     * 修改Object的剩余存活时间 (单位: 秒)
     */
    @Override
    public void updateObjectTimeout(String key, long timeout) {
        RedisUtils.expire(key, Duration.ofSeconds(timeout));
    }

    /**
     * 搜索数据
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<String> searchData(String prefix, String keyword, int start, int size, boolean sortType) {
        String pattern = prefix + "*" + keyword + "*";
        String cacheKey = pattern + start + StringUtils.COLON + size + StringUtils.COLON + sortType;
        return (List<String>) CAFFEINE.get(cacheKey, k -> {
            Collection<String> keys = RedisUtils.keys(pattern);
            List<String> list = new ArrayList<>(keys);
            return SaFoxUtil.searchList(list, start, size, sortType);
        });
    }

    /**
     * 从缓存读取对象。
     * <p>
     * 会话数据由 Jackson 按类型标识（含全限定类名）序列化，历史数据中的类可能已改包名或改名而无法反序列化。
     * 这类数据已不可恢复，按「会话不存在」处理并清理，避免每次读取都抛异常并波及业务调用方。
     * </p>
     *
     * @param key 缓存键
     * @return 缓存值
     */
    @SuppressWarnings("unchecked")
    private <T> T getCacheValue(String key) {
        try {
            return (T) CAFFEINE.get(key, RedisUtils::getCacheObject);
        } catch (RuntimeException e) {
            if (!isUnreadablePayload(e)) {
                throw e;
            }
            log.warn("会话数据反序列化失败，已按失效处理并清理：key={}", key);
            invalidate(key);
            try {
                RedisUtils.deleteObject(key);
            } catch (RuntimeException deleteError) {
                log.warn("清理失效会话数据失败：key={}", key, deleteError);
            }
            return null;
        }
    }

    /**
     * 判断异常是否由反序列化失败引起（Jackson 无法按类型标识还原对象）。
     *
     * @param e 异常
     * @return true 表示数据无法反序列化
     */
    private static boolean isUnreadablePayload(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof JacksonException) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }

    /**
     * 写入缓存值并刷新本地缓存。
     *
     * @param key     缓存键
     * @param value   缓存值
     * @param timeout 超时时间
     */
    private void writeValue(String key, Object value, long timeout) {
        if (timeout == 0 || timeout <= NOT_VALUE_EXPIRE) {
            return;
        }
        if (timeout == NEVER_EXPIRE) {
            RedisUtils.setCacheObject(key, value);
        } else {
            RedisUtils.setCacheObject(key, value, Duration.ofSeconds(timeout));
        }
        invalidate(key);
    }

    /**
     * 清除本地缓存。
     *
     * @param key 缓存键
     */
    private void invalidate(String key) {
        CAFFEINE.invalidate(key);
    }

    /**
     * 将 Redis TTL 转为秒。
     *
     * @param timeoutRedis Redis TTL 毫秒值
     * @return Sa-Token 需要的秒值
     */
    private long toTimeoutSeconds(long timeoutRedis) {
        // 加1的目的 解决sa-token使用秒 redis是毫秒导致1秒的精度问题 手动补偿
        return timeoutRedis < 0 ? timeoutRedis : timeoutRedis / 1000 + 1;
    }

}
