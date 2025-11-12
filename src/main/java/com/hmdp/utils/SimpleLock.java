package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;
@Slf4j
public class SimpleLock implements ILock{
    private StringRedisTemplate stringRedisTemplate;
    private String keyName;
    private final static String KEY_LOCK="lock:";
    private final static String ID_VALUE_PREFIX= UUID.randomUUID().toString(true)+"-";


    public SimpleLock(StringRedisTemplate stringRedisTemplate, String keyName) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.keyName = keyName;
    }

    @Override
    public boolean tryLock(Long expireTime) {
        //锁的值存入线程id
        long threadId = Thread.currentThread().getId();
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KEY_LOCK + keyName, ID_VALUE_PREFIX+threadId, expireTime, TimeUnit.SECONDS);
        log.info("添加锁:{}",KEY_LOCK + keyName);
        return Boolean.TRUE.equals(success);//避免success为null，自动拆箱发生空指针异常
    }

    @Override
    public void delLock() {
        //注意：极端情况下，若在查询完锁是否有效后，锁在删除前失效了可能会引发并发安全问题，可采用lua脚本解决，达到查询删除原子性的效果
        //在删除锁前判断该锁是否有效
        String value = stringRedisTemplate.opsForValue().get(KEY_LOCK + keyName);
        long threadId = Thread.currentThread().getId();
        if(value!=null&&value.equals(ID_VALUE_PREFIX+threadId)){
            //该锁为该线程的锁,执行删除
            stringRedisTemplate.delete(KEY_LOCK+keyName);
        }

    }
}
