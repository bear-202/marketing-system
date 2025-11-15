package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {

    /**
     * 判断该用户是否关注了这篇博客的作者
     * @param followId 博客作者id
     * @return
     */
    Result isFollow(Long followId);

    /**
     * 关注
     * @param followId 博客作者Id
     * @param isFollow true :关注 false:取消关注
     * @return
     */
    Result follow(Long followId, boolean isFollow);

    /**
     * 查询当前登录用户和博客作者共同关注的用户
     * @param followId
     * @return
     */
    Result commonFollow(Long followId);
}
