package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisCommand;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoLocation;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private ShopServiceImpl shopService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
     @Test
    public void testPreSaveCacheInRedis(){
         for(int i=2;i<=14;i++){
             shopService.preSaveShopCacheInRedis((long) i,200L);
         }

     }

     @Test
    public void testId(){
         long id = redisIdWorker.getOnlyId("shop");
         System.out.println(id);
     }

     @Test
    public void loadShopGeoTest(){
         //加载店铺经纬度到redis
         //查询店铺信息
         List<Shop> shopList = shopService.list();
         //根据店铺类型进行分类
         Map<Long,List<Shop>> map=shopList.stream().collect(Collectors.groupingBy(shop -> shop.getTypeId()));
         for (Map.Entry<Long, List<Shop>> longListEntry : map.entrySet()) {

             //key为店铺类型
             String key=SHOP_GEO_KEY+longListEntry.getKey();
             //value为 店铺Id：经纬度
             List<Shop> shop = longListEntry.getValue();

             List<RedisGeoCommands.GeoLocation<String>> geoLocations=new ArrayList<>();
             for (Shop shop1 : shop) {
                 geoLocations.add(new RedisGeoCommands.GeoLocation<>(shop1.getId().toString(),new Point(shop1.getX(),shop1.getY())));
             }
             //传入redis的geo类型,封装成多个geo，key为店铺类型
             stringRedisTemplate.opsForGeo().add(key, geoLocations);
         }

     }

}
