package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryById(Long id) {
        //根据id查询redis数据
        String shopCacheInfo = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);

        if (StrUtil.isNotBlank(shopCacheInfo)) {
            //存在，直接返回
            return Result.ok(shopCacheInfo);
        }
        //不存在，查询数据库
         Shop shopInfo = getById(id);
        //不存在，直接返回fail
        if (shopInfo==null) {
            return Result.fail("店铺不存在");
        }
        //存在，存入redis,并设置过期时间（防止发生数据库发生异常，缓存数据得不到清理）
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shopInfo),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return Result.ok(shopInfo);
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
}
