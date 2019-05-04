package top.itcat.cache.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Cache Method
 *
 * @author ITcathyh
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface LoadingCache {
    /**
     * Cache prefix key
     */
    String prefix();

    /**
     * cache param key name
     */
    String fieldKey() default "";

    /**
     * Redis expire time
     * If it is 0, the item will not be cached on redis.
     * If it is -1(NoExpiration), the item never expires.
     */
    int expireTime() default Const.DEFAULT_EXPIRE_TIME;

    /**
     * Local cache expire time.
     * If it is 0, the item will not be cached locally.
     * If it is -1(NoExpiration), the item never expires.
     */
    int localExpireTime() default Const.DEFAULT_LOCAL_EXPIRE_TIME;

    /**
     * Expire time unit
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    CacheOperation cacheOperation();

    enum CacheOperation {
        QUERY,
        UPDATE,
        DELETE;
    }
}
