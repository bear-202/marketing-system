package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private ShopServiceImpl shopService;
    @Resource
    private RedisIdWorker redisIdWorker;
     @Test
    public void testPreSaveCacheInRedis(){
         shopService.preSaveShopCacheInRedis(1L,200L);
     }

     @Test
    public void testId(){
         long id = redisIdWorker.getOnlyId("shop");
         System.out.println(id);
     }
}
