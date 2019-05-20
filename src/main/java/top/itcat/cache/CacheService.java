package top.itcat.cache;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.LocalVariableTableParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import top.itcat.cache.annotation.LoadingCache;
import top.itcat.cache.manage.CacheManager;
import top.itcat.cache.manage.DefaultCacheManager;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("all")
@Component
@Aspect
public class CacheService {
    Logger log = LoggerFactory.getLogger(this.getClass());
    private CacheManager cacheManager;

    public CacheService() {
        cacheManager = new DefaultCacheManager();
    }

    public void setCacheManager(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Pointcut("@annotation(top.itcat.cache.annotation.LoadingCache)")
    public void cacheService() {
    }

    @Around(value = "cacheService()")
    public Object dealCacheService(ProceedingJoinPoint point) throws Throwable {
        try {
            Method method = getMethod(point);
            LoadingCache loadingCache = method.getAnnotation(LoadingCache.class);
            StringBuilder fieldKey = new StringBuilder("");
            String[] fieldKeys = loadingCache.fieldKeys();

            if (fieldKeys.length != 0) {
                for (String key : fieldKeys) {
                    fieldKey.append(parseKey(key, method, point.getArgs())).append(":");
                }
            }

//            if (!StringUtils.isEmpty(fieldKey)) {
//                fieldKey = parseKey(fieldKey, method, point.getArgs());
//            }

            String key = loadingCache.prefix() + ":" + fieldKey.toString();

            switch (loadingCache.cacheOperation()) {
                case QUERY:
                    return getVal(point, key, loadingCache.expireTime(),
                            loadingCache.localExpireTime(), loadingCache.timeUnit());
                case DELETE:
                    return updateVal(point, key);
                case UPDATE:
                    return updateVal(point, key);
                default:
            }
        } catch (Exception e) {
            log.error("dealCacheService error,JoinPoint:{}", point.getSignature(), e);
        }

        return point.proceed();
    }

    public CacheManager getCacheManager() {
        return cacheManager;
    }

    protected Object getVal(ProceedingJoinPoint point, String key,
                            int expireTime, int localExpireTime, TimeUnit timeUnit)
            throws Throwable {
        Object obj = cacheManager.get(key);

        if (obj != null) {
            return obj;
        }

        log.info("Missing Key({})", key);
        Object result = point.proceed();

        if (!(result instanceof Serializable)) {
            return result;
        }

        int redisTime = (int) (timeUnit.toMillis(expireTime) / 1000);
        int locaTime = (int) (timeUnit.toMillis(localExpireTime) / 1000);

        try {
            cacheManager.set(key, (Serializable) result, redisTime, locaTime);
        } catch (Exception e) {
            log.error("cacheManager set error:{}", e);
        }
        return result;

    }

    protected Object updateVal(ProceedingJoinPoint point, String key)
            throws Throwable {
        Object obj = point.proceed();

        try {
            cacheManager.del(key);
        } catch (Exception e) {
            log.error("cacheManager del error:{}", e);
        }

        return obj;
    }

    protected String parseKey(String fieldKey, Method method, Object[] args) {
        String[] paraNameArr = new LocalVariableTableParameterNameDiscoverer().getParameterNames(method);
        ExpressionParser parser = new SpelExpressionParser();
        StandardEvaluationContext context = new StandardEvaluationContext();

        for (int i = 0; i < paraNameArr.length; i++) {
            context.setVariable(paraNameArr[i], args[i]);
        }

        return parser.parseExpression(fieldKey).getValue(context, String.class);
    }

    private Method getMethod(JoinPoint joinPoint) throws Exception {
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        Method method = methodSignature.getMethod();
        return method;
    }
}