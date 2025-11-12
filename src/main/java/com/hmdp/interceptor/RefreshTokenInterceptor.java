package com.hmdp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;

@Component
@Slf4j
public class RefreshTokenInterceptor implements HandlerInterceptor{

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //从前端携带的header中拿出token
        String token = request.getHeader("authorization");

        if (token==null) {
            return true;
        }
        log.info("token：{}验证成功~",token);
        //从redis取出用户信息
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(LOGIN_USER_KEY + token);
        if (userMap.isEmpty()) {
            return true;
        }
        //存入threadLocal，便于单次请求后续业务操作
        UserDTO user = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);//转化成user
        UserHolder.saveUser(user);
        //刷新token有效时间
        stringRedisTemplate.expire(LOGIN_USER_KEY + token,120, TimeUnit.MINUTES);
       return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //单次请求结束移除threadLocal数据，防止内存溢出
        UserHolder.removeUser();
    }
}
