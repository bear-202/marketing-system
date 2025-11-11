package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService iSeckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Override
    public Result seckillVoucher(Long voucherId) {
        //查询优惠卷信息
        SeckillVoucher seckillVoucher = iSeckillVoucherService.getById(voucherId);
        //查看是否过期
        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
            //过期
            return Result.fail("订单已过期");
        }
        Long userId = UserHolder.getUser().getId();
        //以每一个用户id作为锁,防止高并发的线程安全问题，由于toString方法每次会创建一个string对象，则使用intern到静态串池里面去拿到同一个用户id
        synchronized (userId.toString().intern()){
            //spring会为transaction注解修饰的类创建动态代理类,为了使事务生效，用代理类去调用方法
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.creatVoucherOrder(voucherId, seckillVoucher);
        }

    }

    /**
     * 创建订单数据
     * @param voucherId
     * @param seckillVoucher
     * @return
     */
    public Result creatVoucherOrder(Long voucherId, SeckillVoucher seckillVoucher) {
        Long userId = UserHolder.getUser().getId();
        //实现一人一单
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        //判断该用户是否存在订单
        if(count>0){
            //则该用户已经购买过该优惠卷
            return Result.fail("优惠卷已拥有~");
        }
        //未过期 查看库存
        Integer stock = seckillVoucher.getStock();
        if (stock<1) {
            //库存不足
            return Result.fail("订单被抢空了~");
        }
        //库存充足，更新库存(采用乐观锁的思想，处理高并发的情况）
        //update from tb_seckill_voucher set stock=stock-1 where voucher_id=#{voucherId} and stock > 0
        boolean success = iSeckillVoucherService
                .update()
                .setSql("stock=stock-1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!success) {
            return Result.fail("订单被抢空了~");
        }
        //添加订单信息
        VoucherOrder voucherOrder = new VoucherOrder();
        //设置优惠卷id
        voucherOrder.setVoucherId(voucherId);
        //用户id
        voucherOrder.setUserId(userId);
        //设置全局唯一id
        long voucherOrderId = redisIdWorker.getOnlyId("voucher");
        voucherOrder.setId(voucherOrderId);
        //存入订单表
        save(voucherOrder);
        return Result.ok(voucherOrderId);
    }
}
