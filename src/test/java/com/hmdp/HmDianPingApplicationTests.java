package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.entity.Voucher;
import com.hmdp.service.IVoucherService;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisCommand;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoLocation;

import jakarta.annotation.Resource;

import java.time.LocalDateTime;
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
    @Resource
    private IVoucherService iVoucherService;

    /**
     * 预热缓存-店铺信息
     */
    @Test
    public void testPreSaveCacheInRedis(){
         /*for(int i=2;i<=14;i++){
             shopService.preSaveShopCacheInRedis((long) i,200L);
         }*/
        shopService.preSaveShopCacheInRedis(1L,200L);

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

     @Test
    void addVoucher(){

         //{
         //"shopId": 1,
         //"title": "100元代金券",
         //"subTitle": "周一至周五均可使用",
         //"rules": "全场通用\\n无需预约\\n可无限叠加\\不兑现、不找零\\n仅限堂食",
         //"payValue": 8000,
         //"actualValue": 10000,
         //"type": 1,
         //"stock": 100,
         //"beginTime": "2022-08-09T10:25:01",
         //"endTime": "2022-08-10T10:25:01"
         //}
         final Voucher voucher = new Voucher().setShopId(1L)
                 .setTitle("200元代金券")
                 .setSubTitle("周一至周五均可使用")
                 .setRules("全场通用\\n无需预约\\n可无限叠加\\不兑现、不找零\\n仅限堂食")
                 .setPayValue(8000L)
                 .setActualValue(10000L)
                 .setType(1)
                 .setStock(100)
                 .setBeginTime(LocalDateTime.now())
                 .setEndTime(LocalDateTime.now().plusDays(1));
         this.iVoucherService.addSeckillVoucher(voucher);
     }

}
