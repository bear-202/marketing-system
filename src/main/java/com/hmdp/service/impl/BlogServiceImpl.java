package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import jakarta.annotation.Resource;
import java.util.ArrayList;
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
    @Resource
    private IFollowService followService;

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
            boolean success = update().setSql("liked=liked-1").eq("id", id).update();
            if (success) {
                //取消成功，从redis的sortSet集合移除元素
                stringRedisTemplate.opsForZSet().remove(blogLikeRedisKey+id,userId.toString());
            }
        }else {
            //否，将该用户添加到redis的blog:liked的set集合 更新数据库和redis
            boolean success = update().setSql("liked=liked+1").eq("id", id).update();
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
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean isSuccess = save(blog);
        if (!isSuccess) {
            //存储失败
            return Result.fail("发布失败~");
        }
        //查询作者粉丝
        List<Follow> follows = followService.query().eq("follow_user_id", blog.getUserId()).list();
        //将该发布博客日记的作者的粉丝每一个都推送博客id
        for (Follow follow : follows) {
            String key="feed:"+follow.getUserId();
            stringRedisTemplate.opsForZSet().add(key,blog.getId().toString(),System.currentTimeMillis());
        }
        return Result.ok(blog.getId());
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
    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        //查询用户id,构建key
        Long userId = UserHolder.getUser().getId();
        String key="feed:"+userId;

        //查询该用户的的关注者的发布的博客id,从redis收件箱 ZREVRANGE key max min limit offset count
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet().
                reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        if (typedTuples==null || typedTuples.isEmpty()) {
            return Result.ok();
        }
        //博客id列表最后一个的时间戳
        long minTime=0L;
        //等同于offset，表示偏移量，特殊情况：当minTime存在多个时，偏移量随之改变 为了避免下一页和上一页相同时间发发布时间的博客冲突
        int minTimeCount=1;//默认为1
        List<Long> blogIds=new ArrayList<>();
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            String value = typedTuple.getValue();
            Long blogId = Long.valueOf(value);
            blogIds.add(blogId);
            long l = typedTuple.getScore().longValue();
            if(l==minTime){
                //有相同时间发布的博客 偏移量+1
                minTimeCount++;
            }else{
                minTime=l;
                minTimeCount=1;
            }
        }
        //根据blogIds查询blog信息
        String idStr = StrUtil.join(",", blogIds);
        List<Blog> blogList = query().in("id", blogIds).last("order by field (id," + idStr + ")").list();
        //封装blog
        for (Blog blog : blogList) {
            //查询该用户信息,添加进Blog中
            queryBlogUser(blog);
            //查询用户是否点赞过该博客
            isLikedBlog(blog);
        }
        //封装ScrollResult
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogList);
        scrollResult.setOffset(minTimeCount);
        scrollResult.setMinTime(minTime);
        return Result.ok(scrollResult);
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
