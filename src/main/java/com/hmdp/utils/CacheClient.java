package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import static com.hmdp.utils.RedisConstants.*;

/**
 * 缓存工具类，处理多种突发情况
 */
@Component
public class CacheClient {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR= Executors.newFixedThreadPool(10);
    /**
     * 存入缓存
     *
     * @param key        缓存key
     * @param value      要缓存的对象
     * @param expireTime 过期时间
     * @param unit       时间单位
     */
    public void setCache(String key, Object value, Long expireTime, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), expireTime, unit);
    }

    /**
     * 存入缓存，使用逻辑过期时间
     *
     * @param key        缓存key
     * @param value      要缓存的对象
     * @param expireTime 过期时间
     * @param unit       时间单位
     */
    public void setCacheWithLogicalExpire(String key, Object value, Long expireTime, TimeUnit unit) {

        //封装存入对象
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(expireTime)));//设置逻辑过期时间

        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 查询缓存数据,解决缓存穿透问题（在缓存和数据库都没有数据的情况下，在缓存里面存入空值）
     *
     * @param keyPrefix   缓存key前缀
     * @param id          查询数据id
     * @param type        存入缓存的对象类型
     * @param dbFoundBack 查询数据库的方法
     * @param expireTime  过期时间
     * @param unit        时间单位
     * @param <R>         对象类型
     * @param <ID>        id类型
     * @return 数据对象
     */
    public <R, ID> R queryInfoWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFoundBack, Long expireTime, TimeUnit unit) {
        //根据id查询redis数据
        String objectJson = stringRedisTemplate.opsForValue().get(keyPrefix + id);

        if (StrUtil.isNotBlank(objectJson)) {
            //存在，直接返回
            return JSONUtil.toBean(objectJson, type);
        }
        //objectJson是Null或"_"，因为判断过Null的情况，则为"_"
        if (objectJson != null) {
            return null;
        }
        //不存在，查询数据库,返回值为数据库查询映射的对象信息
        R objectInfo = dbFoundBack.apply(id);
        //不存在，直接返回fail
        if (objectInfo == null) {
            //解决缓存穿透情形
            stringRedisTemplate.opsForValue().set(keyPrefix + id, "", expireTime, unit);
            return null;
        }
        //存在，存入redis,并设置过期时间（防止发生数据库发生异常，缓存数据得不到清理）
        setCache(keyPrefix + id, objectInfo, expireTime, unit);
        return objectInfo;
    }

    /**
     * 查询缓存数据,利用逻辑过期解决缓存击穿问题
     * @param keyPrefix   缓存key前缀
     * @param id          查询数据id
     * @param type        存入缓存的对象类型
     * @param dbFoundBack 查询数据库的方法
     * @param expireTime  过期时间
     * @param unit        时间单位
     * @return 数据对象类型
     * @param <R>         对象类型
     * @param <ID>        id类型
     */
    public   <R, ID> R queryInfoWithLogicalExpire(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFoundBack,Long expireTime, TimeUnit unit){
        //根据id查询redis数据
        String objectJson = stringRedisTemplate.opsForValue().get(keyPrefix + id);
        //反序列化
        RedisData redisData = JSONUtil.toBean(objectJson, RedisData.class);
        if (StrUtil.isBlank(objectJson)) {
            //不存在，直接返回
            return null;
        }
        JSONObject data = (JSONObject) redisData.getData();
        R objectInfo = JSONUtil.toBean(data, type);
        //查看缓存是否过期
        if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
            //未过期,直接返回缓存数据
            return objectInfo;
        }
        //过期更新缓存，尝试获取锁,并上锁
        String lockKey = LOCK_SHOP_KEY+id;
        boolean flag = tryLock(lockKey);
        if(flag){
            //锁存在,二次判断缓存是否过期，避免多次重构
            RedisData redisDataSecond = JSONUtil.toBean(stringRedisTemplate.opsForValue().get(keyPrefix + id), RedisData.class);
            if (redisDataSecond.getExpireTime().isAfter(LocalDateTime.now())) {
                JSONObject SecondData=(JSONObject)redisDataSecond.getData();
                //未过期,直接返回缓存数据
                return JSONUtil.toBean(SecondData,type);
            }
            //过期，创建线程执行缓存重构逻辑
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    //重新预热缓存数据
                    this.setCacheWithLogicalExpire(keyPrefix+id,dbFoundBack.apply(id),expireTime,unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    //释放锁
                    delLock(lockKey);
                }
            });
        }
        //锁不存在,返回过期数据
        return objectInfo;
    }

    /**
     * 尝试添加锁
     * @param key 锁名
     * @return 是否成功
     */
    private boolean tryLock(String key) {
        //如果锁不存在，则加锁（采用redis机制实现互斥锁）
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "lockValue", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 移除锁
     *
     * @param key 锁名
     */
    private void delLock(String key) {
        //删除redis缓存的锁
        stringRedisTemplate.delete(key);
    }
}




