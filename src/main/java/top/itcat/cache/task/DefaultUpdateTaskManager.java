package top.itcat.cache.task;

import net.sf.ehcache.Ehcache;

public class DefaultUpdateTaskManager implements UpdateTaskManager {
    private volatile boolean stop = false;


    @Override
    public void update(Object cache) {
        Ehcache ehcache = (Ehcache) cache;

    }

    @Override
    public void stop() {
        stop = true;
    }
}
