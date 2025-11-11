package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 生成全局唯一ID
 */
@Component
public class RedisIdWorker {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    //初始时间戳(2025.1.1)
    private static final long initTimeStamp=1735689600L;
    private static final int COUNT_MOVE=32;
    public long getOnlyId(String keyPrefix){
        //当前时间戳
        long currentTimeStamp = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        //构造id时间戳
        long timestamp=currentTimeStamp-initTimeStamp;

        //生产序列号
        //获取当天时间
        // 获取当前日期时间并格式化为字符串
        String currentDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        Long increment = stringRedisTemplate.opsForValue().increment("inc:" + keyPrefix + ":" + currentDate);

        return timestamp << COUNT_MOVE | increment;
    }

}

