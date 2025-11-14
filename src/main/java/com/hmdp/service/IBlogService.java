package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    /**
     * 查询该用户的博客信息
     * @param id
     * @return
     */
    Result queryBlogById(Long id);

    /**
     * 查询当前页的所有用户的博客信息
     * @param current
     * @return
     */
    Result queryHotPageBlog(Integer current);

    /**
     * 给博客点赞
     * @param id
     * @return
     */
    Result likeBlogById(Long id);

    /**
     * 根据id查询前五个点赞的用户
     * @param id
     * @return
     */
    Result queryBlogLikesById(Long id);
}
