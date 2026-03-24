package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.config.RabbitMQConfig;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService iSeckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private RabbitTemplate rabbitTemplate;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;


    static {
        //设置脚本基本信息
        SECKILL_SCRIPT=new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        //用户id
        Long userId = UserHolder.getUser().getId();
        //生成全局唯一id
        long orderId = redisIdWorker.getOnlyId("voucher");
        //使用lua脚本查询库存信息和解决一人一单来决定是否可以抢购优惠卷
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT, Collections.emptyList(), voucherId.toString(), userId.toString(),String.valueOf(orderId));
        //结果为1或者2-》库存不足或者不可重复购入
        int r = result.intValue();
        if (r!=0) {
            return Result.fail(r==1?"库存不足":"不可重复购入");
        }
        //将订单信息发送到RabbitMQ队列
        Map<String, Object> orderMap = new HashMap<>();
        orderMap.put("voucherId", voucherId);
        orderMap.put("userId", userId);
        orderMap.put("id", orderId);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY, orderMap);
        return Result.ok(orderId);
    }

    @Override
    @Transactional
    public void saveVoucherOrder(VoucherOrder voucherOrder) {
        // 先查询订单是否已存在，防止重复消费导致的主键冲突
        VoucherOrder existOrder = getById(voucherOrder.getId());
        if (existOrder != null) {
            log.warn("订单已存在，跳过保存：orderId={}", voucherOrder.getId());
            return;
        }
        //库存充足，更新库存(采用乐观锁的思想，处理高并发的情况）
        //update from tb_seckill_voucher set stock=stock-1 where voucher_id=#{voucherId} and stock > 0
        boolean success = iSeckillVoucherService
                .update()
                .setSql("stock=stock-1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock", 0)
                .update();
        if (!success) {
            log.error("库存更新异常");
        }
        //存入订单表
        save(voucherOrder);
    }


    /*@Override
    public Result seckillVoucher(Long voucherId) {
        //查询优惠卷信息
        SeckillVoucher seckillVoucher = iSeckillVoucherService.getById(voucherId);
        //查看是否过期
        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
            //过期
            return Result.fail("订单已过期");
        }
        Long userId = UserHolder.getUser().getId();

        //单体(采用悲观锁synchronized解决；一人一单问题)
        //以每一个用户id作为锁,防止高并发的线程安全问题，由于toString方法每次会创建一个string对象，则使用intern到静态串池里面去拿到同一个用户id
        //synchronized (userId.toString().intern()){
        //spring会为transaction注解修饰的类创建动态代理类,为了使事务生效，用代理类去调用方法
        //  IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
        //return proxy.creatVoucherOrder(voucherId, seckillVoucher);
        //}
        //集群(采用redis内置的nx充当锁解决集群下的一人一单问题)
        //SimpleLock lock = new SimpleLock(stringRedisTemplate, "order:" + userId);


        //使用Redisson框架获取锁
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        //尝试获取锁
        boolean success = lock.tryLock();
        if (!success) {
            //获取锁不超过
            return Result.fail("优惠卷已拥有~");
        }
        try {
            //执行业务流程
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.creatVoucherOrder(voucherId, seckillVoucher);
        } finally {
            //释放锁
            lock.unlock();
        }
    }*/


    /**
     * 创建订单数据
     *
     * @param voucherId
     * @param seckillVoucher
     * @return
     */
    /*@Transactional
    public Result creatVoucherOrder(Long voucherId, SeckillVoucher seckillVoucher) {
        Long userId = UserHolder.getUser().getId();
        //实现一人一单
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        //判断该用户是否存在订单
        if (count > 0) {
            //则该用户已经购买过该优惠卷
            log.info("优惠卷已拥有~");
            return Result.fail("优惠卷已拥有~");
        }
        //未过期 查看库存
        Integer stock = seckillVoucher.getStock();
        if (stock < 1) {
            //库存不足
            log.info("订单被抢空了~");
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
    }*/
}
