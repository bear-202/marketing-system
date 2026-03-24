package com.hmdp.utils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.hmdp.constant.AIConstants;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopReservation;
import com.hmdp.entity.ShopType;
import com.hmdp.service.IShopService;
import com.hmdp.service.ITbShopReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ShopInfoServeTools {
    private final IShopService shopService;
    private final ITbShopReservationService iTbShopReservationService;

    @Tool(description = "根据店铺类型查询店铺信息")
    public List<Shop> queryShopByType(@ToolParam(description = AIConstants.SHOP_TYPE_ID, required = false) Long typeId) {
        //若店铺类型为空，则返回所有店铺
        if (typeId == null) {
            return shopService.list();
        }
        //根据店铺类型查询
        LambdaQueryWrapper<Shop> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.eq(Shop::getTypeId, typeId);

        return shopService.list(queryWrapper);
    }
    @Tool(description = "生产预约单号,返回是否成功，true:成功，false:失败")
    public boolean generateOrderNo(@ToolParam(description = "用户名")String username,
                                    @ToolParam(description = "联系电话")String userPhone,
                                  @ToolParam(description = "店铺名称")String shopName,
                                  @ToolParam(description = "预约日期") LocalDate reservationDate,
                                  @ToolParam(description = "备注",required = false)String remark
                                  ) {
        final ShopReservation shopReservation = new ShopReservation().setUserName(username)
                .setUserPhone(userPhone)
                .setStoreName(shopName)
                .setReservationDate(reservationDate)
                .setReservationTime(LocalDateTime.now())
                .setRemark(remark);
        return iTbShopReservationService.save(shopReservation);
    }
}
