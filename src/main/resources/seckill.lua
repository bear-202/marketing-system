---@diagnostic disable: undefined-global
---
--- Created by 21467
--- DateTime: 2025/11/12 18:50
---
--优惠卷id
local voucherId = ARGV[1]
--用户Id
local userId=ARGV[2]
--订单id
local orderId=ARGV[3]
--优惠卷库存key
local stockKey="seckill:stock:" .. voucherId
--订单key,存入用户Id
local orderKey="seckill:order"..voucherId

--判断库存是否充足
if tonumber(redis.call("get",stockKey))<=0 then
--不充足，直接返回1
return 1
end

--充足，查询该用户是否已经下过单
if redis.call("sismember",orderKey,userId)==1 then
   --是，返回2
   return 2
end
--扣减库存
redis.call("incrby",stockKey,-1)
--否,添加该用户id
redis.call("sadd",orderKey,userId)
--将订单信息添加至stream消息队列,XADD stream.orders * k1 v1 k2 v2
redis.call("xadd","stream.orders","*","voucherId",voucherId,"userId",userId,"id",orderId)
--返回0
return 0
