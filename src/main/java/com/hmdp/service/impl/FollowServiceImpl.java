package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService userService;
    @Override
    public Result isFollow(Long followId) {
        //判断是否关注
        Long userId = UserHolder.getUser().getId();
        //查询用户关注关联表，用户是否关注该作者-》count>0则表示关注了该作者
        Long count = query().eq("user_id", userId).eq("follow_user_id", followId).count();
        return Result.ok(count > 0);
    }

    @Override
    public Result follow(Long followId, boolean isFollow) {
        //登录用户id
        Long userId = UserHolder.getUser().getId();
        //redis的set集合key
        String key="follows:"+userId;
        //判断isFollow ture:关注 false:取消关注
        if (isFollow) {
            //关注,封装follow关系表
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followId);
            //存入tb_follow表
            boolean isSuccess = save(follow);
            if (isSuccess) {
                //存入数据库成功,添加至到redis
                stringRedisTemplate.opsForSet().add(key, String.valueOf(followId));
            }
        }else{
            //取消关注,从关系表中移除该用户关注的信息列表
            boolean isSuccess = remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", followId));
            if (isSuccess) {
                //同步redis
                stringRedisTemplate.opsForSet().remove(key,String.valueOf(followId));
            }
        }
        return Result.ok();
    }

    @Override
    public Result commonFollow(Long followId) {
        //使用interSet的redis功能查询出当前用户和作者共同关注用户
        String userKey="follows:"+UserHolder.getUser().getId();
        String followKey="follows:"+followId;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(userKey, followKey);
        if (intersect==null||intersect.isEmpty()) {
            //没有共同关注用户
            return Result.ok(Collections.emptyList());
        }
        //有共同关注用户
        List<Long> commonFollowIds = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        //查询共同关注用户信息
        List<User> users = userService.listByIds(commonFollowIds);
        //封装UserDTO
        List<UserDTO> commonUser = users.stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
        return Result.ok(commonUser);
    }
}
