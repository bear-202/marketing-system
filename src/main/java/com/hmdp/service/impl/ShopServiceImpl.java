package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.*;
import static java.time.LocalDateTime.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR= Executors.newFixedThreadPool(10);
    @Override
    public Result queryById(Long id) {

        //缓存穿透情况
        //Shop shop = queryShopWithPassThrough(id);

        //使用互斥锁解决缓存击穿问题
        //Shop shop = queryWithMutex(id);

        //采用逻辑过期解决缓存击穿问题
       // Shop shop = queryShopWithLogicalExpire(id);

        //工具类的调用
        Shop shop = cacheClient.queryInfoWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, 20L, TimeUnit.SECONDS);
        if(shop==null){
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    /**
     * 查询店铺信息，采用逻辑过期原理解决缓存击穿问题
     * @param id
     * @return
     */
    private Shop queryShopWithLogicalExpire(Long id){
        //根据id查询redis数据
        String shopCacheInfo = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //反序列化
        RedisData redisData = JSONUtil.toBean(shopCacheInfo, RedisData.class);
        if (StrUtil.isBlank(shopCacheInfo)) {
            //不存在，直接返回
            return null;
        }
        JSONObject data = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);
        //查看缓存是否过期
        LocalDateTime expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())) {
            //未过期,直接返回缓存数据
            return shop;
        }
        //过期更新缓存，尝试获取锁,并上锁
        String lockKey = LOCK_SHOP_KEY+id;
        boolean flag = tryLock(lockKey);
        if(flag){
            //锁存在,二次判断缓存是否过期，避免多次重构
            RedisData redisDataSecond = JSONUtil.toBean(stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id), RedisData.class);
            if (redisDataSecond.getExpireTime().isAfter(LocalDateTime.now())) {
                JSONObject SecondData=(JSONObject)redisDataSecond.getData();
                //未过期,直接返回缓存数据
                return JSONUtil.toBean(SecondData,Shop.class);
            }
            //过期，创建线程执行缓存重构逻辑
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    //重新预热缓存数据
                    this.preSaveShopCacheInRedis(id,20L);
                    log.info("重构缓存数据");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    //释放锁
                    delLock(lockKey);
                }
            });
        }
        //锁不存在,返回过期数据
        return shop;
    }

    /**
     * 热点key，提取预热存储到redis
     * @param id 店铺id
     * @param expireSecondsTime  热点key过期时间
     */
    public void preSaveShopCacheInRedis(Long id,Long expireSecondsTime){
        //从数据库查询店铺信息
        Shop shop = getById(id);
        //模拟延迟
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        //封装存入redis的数据
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(now().plusSeconds(expireSecondsTime));
        //存入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(redisData));
    }
    /**
     * 获取店铺信息，采用互斥锁解决缓存击穿的情况
     * @param id 店铺id
     * @return 店铺信息
     */
    private Shop queryWithMutex(Long id){
        //根据id查询redis数据
        String shopCacheInfo = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);

        if (StrUtil.isNotBlank(shopCacheInfo)) {
            //存在，直接返回
            return JSONUtil.toBean(shopCacheInfo,Shop.class);
        }
        String lockKey = null;
        Shop shopInfo = null;
        try {
            //尝试获取锁
            lockKey = LOCK_SHOP_KEY+id;
            boolean flag = tryLock(lockKey);
            System.out.println(stringRedisTemplate.opsForValue().get(lockKey));
            if(!flag){
                //锁不存在
                Thread.sleep(50);
                //则重新从头开始查询缓存数据，直到拿到数据返回结果
                return queryWithMutex(id);
            }
            //拿到锁，再次根据id查询redis数据（当锁里面的线程A刚好释放锁，A还没返回数据，B拿到了锁，避免再次对数据库操作）
            shopCacheInfo = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
            if (StrUtil.isNotBlank(shopCacheInfo)) {
                //存在，直接返回
                return JSONUtil.toBean(shopCacheInfo,Shop.class);
            }
            //不存在，查询数据库
            shopInfo = getById(id);
            //模拟延迟
            Thread.sleep(200);
            //不存在，直接返回fail
            if (shopInfo==null) {
                //解决缓存穿透情形
                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
                return null;
            }
            //存在，存入redis,并设置过期时间（防止发生数据库发生异常，缓存数据得不到清理）
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shopInfo),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //释放锁
            delLock(lockKey);
        }
        return shopInfo;
    }
    /**
     * 查询店铺信息，解决缓存穿透问题
     * @param id 店铺id
     * @return 店铺信息
     */
    private Shop queryShopWithPassThrough(Long id){
         //根据id查询redis数据
         String shopCacheInfo = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);

         if (StrUtil.isNotBlank(shopCacheInfo)) {
             //存在，直接返回
             return JSONUtil.toBean(shopCacheInfo, Shop.class);
         }
         //shopCacheInfo是Null或"_"，因为判断过Null的情况，则为"_"
         if(shopCacheInfo!=null){
             return null;
         }
         //不存在，查询数据库
         Shop shopInfo = getById(id);
         //不存在，直接返回fail
         if (shopInfo==null) {
             //解决缓存穿透情形
             stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
             return null;
         }
         //存在，存入redis,并设置过期时间（防止发生数据库发生异常，缓存数据得不到清理）
         stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shopInfo),CACHE_SHOP_TTL, TimeUnit.MINUTES);
         return shopInfo;
     }
    /**
     * 尝试添加锁
     * @param key 锁名
     * @return 是否成功
     */
    private boolean tryLock(String key){
        //如果锁不存在，则加锁（采用redis机制实现互斥锁）
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "lockValue", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 移除锁
     * @param key 锁名
     */
    private void delLock(String key){
        //删除redis缓存的锁
        stringRedisTemplate.delete(key);
    }
    @Override
    @Transactional
    public Result update(Shop shop) {
        if (shop==null) {
            return Result.fail("店铺异常");
        }
        //更新店铺信息
        updateById(shop);
        //清理缓存数据
        stringRedisTemplate.delete(CACHE_SHOP_KEY+shop.getId());
        return Result.ok();
    }

    @Override
    public Result queryShopByShopType(Integer typeId, Integer current, Double x, Double y) {
        //判断经纬度是否存在
        if(x==null||y==null){
            //不存在 根据类型分页查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page);
        }
        //存在，根据类型分页查询，随带经纬度距离
        String key=SHOP_GEO_KEY+typeId;
        //指定分页参数
        int from = (current - 1) *SystemConstants.DEFAULT_PAGE_SIZE;
        int end =current*SystemConstants.MAX_PAGE_SIZE;
        GeoResults<RedisGeoCommands.GeoLocation<String>> search = stringRedisTemplate.opsForGeo(). //GEOSearch key x y distance
                search(
                key,
                GeoReference.fromCoordinate(x, y),new Distance(5000),//用户的经纬度为圆心，5000m为半径
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end) //返回包括距离，和进行分页从0~end
        );
        if (search==null) {
            //没有对应店铺信息
            return Result.ok(Collections.emptyList());
        }
        //所有店铺id 和经纬度信息
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = search.getContent();
        if(from>=content.size()){
            //当店铺信息数量小于分页查询起始值则无需查询
            return Result.ok(Collections.emptyList());
        }
        //分页查询，从from到end（因redis查询出来默认从0~end）
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> pageContent = content.stream().skip(from).collect(Collectors.toList());

        //根据shopId查询店铺信息
        List<Long> shopIdList=new ArrayList<>(pageContent.size());
        Map<String,Double> shopDistanceList=new HashMap<>(pageContent.size());
        for (GeoResult<RedisGeoCommands.GeoLocation<String>> geoLocationGeoResult : pageContent) {
            //店铺Id
            String shopId = geoLocationGeoResult.getContent().getName();
            shopIdList.add(Long.valueOf(shopId));

            double distance = geoLocationGeoResult.getDistance().getValue();
            shopDistanceList.put(shopId,distance);
        }
        String idStr = StrUtil.join(",", shopIdList);
        List<Shop> shopList = query().in("id", shopIdList).last("order by field (id," + idStr + ")").list();
        //设置店铺与用户距离信息
        for (Shop shop : shopList) {
            Long id = shop.getId();
            String idString = String.valueOf(id);
            Double distance = shopDistanceList.get(idString);
            shop.setDistance(distance);
        }
        //返回
        return Result.ok(shopList);
    }
}
