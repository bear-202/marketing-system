package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.SHOP_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryList() {
        //查询缓存数据
        List<String> shopTypeCacheRange = stringRedisTemplate.opsForList().range(SHOP_TYPE_KEY, 0, -1);

        //存在返回数据
        if(shopTypeCacheRange!=null&&!shopTypeCacheRange.isEmpty()){
            //将list集合中所有所有数据转换为shopType对象
            List<ShopType> shopTypeList = new ArrayList<>();
            for (String shopTypeStr : shopTypeCacheRange) {
                ShopType shopType = JSONUtil.toBean(shopTypeStr, ShopType.class);
                shopTypeList.add(shopType);
            }
            return Result.ok(shopTypeList);
        }
        //不存在查询数据库
        List<ShopType> shopTypeList = query().orderByAsc("sort").list();
        //不存在返回fail
        if (shopTypeList==null) {
            return Result.fail("店铺类型不存在");
        }
        //存在，存入缓存
        List<String> shopTypeStrList = shopTypeList.stream().map(JSONUtil::toJsonStr).collect(Collectors.toList());
        stringRedisTemplate.opsForList().rightPushAll(SHOP_TYPE_KEY,shopTypeStrList);
        return Result.ok();
    }
}
