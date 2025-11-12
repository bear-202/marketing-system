package com.hmdp.utils;

public interface ILock {
    /**
     * 尝试获取锁
     * @param expireTime
     * @return 获取成功：true 失败:false
     */
    boolean tryLock(Long expireTime);

    /**
     * 删除锁
     */
    void delLock();
}
