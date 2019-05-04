package top.itcat.cache.manage;

import org.springframework.data.redis.core.RedisTemplate;
import top.itcat.cache.task.UpdateTaskManager;

import java.io.Serializable;
import java.util.HashMap;

public interface CacheManager {
    public boolean set(String key, Serializable val, int time, int localTime);

    public boolean setnx(String key, Serializable val, int time, int localTime);

    public int mset(HashMap<String, Serializable> map, int time, int localTime);

    public Object get(String key);

    public int del(String... key);

    public Object getLocalCache();

    public void withRedis(RedisTemplate redis);

    public void updateAsync(boolean updateAsync, int time);

    public void updateAsync(UpdateTaskManager task);
}
