package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    //redis的set集合key
    String blogLikeRedisKey="blog:liked:";
    @Override
    public Result queryHotPageBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户，并判断该用户是否点赞
        records.forEach(blog -> {
            this.isLikedBlog(blog);
            this.queryBlogUser(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result likeBlogById(Long id) {

        //当前用户id
        Long userId = UserHolder.getUser().getId();
        //判断该用户是否已经点过赞
        Double score = stringRedisTemplate.opsForZSet().score(blogLikeRedisKey + id, userId.toString());
        if (score!=null) {
            //是,则取消点赞,更新数据库和redis
            log.debug("数据库-1");
            boolean success = update().setSql("liked=liked-1").eq("id", id).update();
            log.debug("数据库-1");
            if (success) {
                //取消成功，从redis的sortSet集合移除元素
                stringRedisTemplate.opsForZSet().remove(blogLikeRedisKey+id,userId.toString());
            }
        }else {
            //否，将该用户添加到redis的blog:liked的set集合 更新数据库和redis
            log.debug("数据库+1");
            boolean success = update().setSql("liked=liked+1").eq("id", id).update();
            log.debug("数据库+1");
            if (success) {
                //添加成功,从redis的sortSet集合添加元素
                stringRedisTemplate.opsForZSet().add(blogLikeRedisKey+id,userId.toString(),System.currentTimeMillis());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikesById(Long id) {
        //从sortSet取出前五名用户
        Set<String> range = stringRedisTemplate.opsForZSet().range(blogLikeRedisKey + id, 0, 4);
        if (range==null || range.isEmpty()) {
            //redis不存在返回空
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = range.stream().map(Long::valueOf).collect(Collectors.toList());
        String idsStr = StrUtil.join(",", ids);
        //查询数据库取出前五用户 select * from t_user where id in(ids) order by field(id,1,2,...) 根据给的idStr排序
        List<User> userList = userService.
                query().
                in("id", ids).
                last("order by field (id," + idsStr + ")").list();
        //封装UserDTO
        List<UserDTO> userDTOList = userList.stream().map(user -> BeanUtil.copyProperties(user,UserDTO.class)).collect(Collectors.toList());
        return Result.ok(userDTOList);
    }

    @Override
    public Result queryBlogById(Long id) {
        //根据Id查询该用户的博客信息
        Blog blog = getById(id);
        if(blog==null){
            return Result.fail("博客不存在");
        }
        //查询该用户信息,添加进Blog中
        queryBlogUser(blog);
        isLikedBlog(blog);
        return Result.ok(blog);
    }
    private void isLikedBlog(Blog blog){
        UserDTO user = UserHolder.getUser();
        if(user==null){
            //用户不存在
            return;
        }
        Double score = stringRedisTemplate.opsForZSet().score(blogLikeRedisKey + blog.getId(), user.getId().toString());
        if (score!=null) {
            blog.setIsLike(true);
        }else {
            blog.setIsLike(false);
        }
    }
    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

}
