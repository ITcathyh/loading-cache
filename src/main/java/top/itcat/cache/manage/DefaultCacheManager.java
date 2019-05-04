package top.itcat.cache.manage;

import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import net.sf.ehcache.config.CacheConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import top.itcat.cache.annotation.Const;
import top.itcat.cache.task.DefaultUpdateTaskManager;
import top.itcat.cache.task.UpdateTaskManager;
import top.itcat.cache.util.RedisUtil;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class DefaultCacheManager<T> implements CacheManager {
    protected Logger log = LoggerFactory.getLogger(this.getClass());
    protected RedisTemplate redisTemplate = null;
    protected Ehcache cache;
    protected UpdateTaskManager task = null;

    public DefaultCacheManager() {
        this("defalutCache");
    }

    public DefaultCacheManager(String cacheName) {
        if (cacheName == null) {
            throw new NullPointerException();
        }


        net.sf.ehcache.CacheManager manager = net.sf.ehcache.CacheManager.getInstance();
        cache = manager.addCacheIfAbsent(cacheName);

        if (cache == null) {
            throw new IllegalArgumentException();
        }

        CacheConfiguration config = cache.getCacheConfiguration();

        config.setTimeToIdleSeconds(Const.DEFAULT_LOCAL_EXPIRE_TIME);
        config.setTimeToLiveSeconds(Const.DEFAULT_LOCAL_EXPIRE_TIME << 2);
        config.setMaxEntriesLocalHeap(1000);
        config.setMaxEntriesLocalDisk(100000);
        config.setEternal(false);
        config.setMaxEntriesInCache(10000);
        config.setDiskExpiryThreadIntervalSeconds(120);
        config.memoryStoreEvictionPolicy("LRU");
    }

    public DefaultCacheManager(Ehcache cache) {
        if (cache == null) {
            throw new NullPointerException();
        }

        this.cache = cache;
    }

    @Override
    public boolean set(String key, Serializable val, int time, int localTime) {
        if (redisTemplate != null && time != 0) {
            if (time < 0) {
                redisTemplate.opsForValue().set(key, val);
            } else {
                redisTemplate.opsForValue().set(key, val, time, TimeUnit.MILLISECONDS);
            }
        }

        return localSet(key, val, localTime);
    }

    @Override
    public boolean setnx(String key, Serializable val, int time, int localTime) {
        if (redisTemplate != null && time != 0) {
            if (!RedisUtil.setNx(redisTemplate, key, val, time)) {
                log.warn("setnx error,key({})", key);
                return false;
            }
        }

        Element element = null;

        if (localTime < 0) {
            element = cache.putIfAbsent(new Element(key, val));
        } else if (localTime > 0) {
            element = cache.putIfAbsent(new Element(key, val, localTime, localTime));
        }

        return element != null;
    }

    @Override
    public int mset(HashMap<String, Serializable> map, int time, int localTime) {
        int count = 0;

        if (redisTemplate != null) {
            redisTemplate.opsForValue().multiSet(map);
        }

        for (Map.Entry<String, Serializable> e : map.entrySet()) {
            if (e.getKey() != null && e.getValue() != null) {
                if (localSet(e.getKey(), e.getValue(), localTime)) {
                    ++count;
                }
            }
        }

        return count;
    }

    @Override
    public Object get(String key) {
        Element element = cache.get(key);

        if (element == null || element.getObjectValue() == null) {
            if (redisTemplate != null) {
                Object obj = redisTemplate.opsForValue().get(key);

                if (obj != null) {
                    cache.put(new Element(key, obj, Const.DEFAULT_LOCAL_EXPIRE_TIME, Const.DEFAULT_LOCAL_EXPIRE_TIME));
                }

                return obj;
            }

            return null;
        }

        log.debug("Hint Key({}),hint Count({})", key, element.getHitCount());
        return element.getObjectValue();
    }

    @Override
    public int del(String... keys) {
        int count = 0;

        if (redisTemplate != null) {
            redisTemplate.delete(Arrays.asList(keys));
        }

        for (String key : keys) {
            if (cache.remove(key)) {
                ++count;
            }
        }
        return count;
    }

    @Override
    public Object getLocalCache() {
        return cache;
    }

    @Override
    public void withRedis(RedisTemplate redis) {
        redisTemplate = redis;
    }

    @Override
    public void updateAsync(boolean updateAsync, int time) {
        if (!updateAsync) {
            if (task != null) {
                task.stop();
                task = null;
            }

            return;
        }

        task = new DefaultUpdateTaskManager();
        task.update(cache);
    }

    @Override
    public void updateAsync(UpdateTaskManager task) {
        if (this.task != null) {
            this.task.stop();
            this.task = null;
        }

        this.task = task;
        task.update(cache);
    }

    protected boolean localSet(String key, Object val, int localTime) {
        if (localTime < 0) {
            cache.put(new Element(key, val));
        } else if (localTime > 0) {
            cache.put(new Element(key, val, localTime, localTime));
        }

        return true;
    }
}
