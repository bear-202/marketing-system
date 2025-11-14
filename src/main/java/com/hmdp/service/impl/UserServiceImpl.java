package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_CODE_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;

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
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sentCode(String phone, HttpSession session){
        //校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
             return Result.fail("手机号有误");
        }
        //生产验证码
        String code = RandomUtil.randomNumbers(6);
        //存储验证码到Redis中,并设置有效时间
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,2,TimeUnit.MINUTES);
        //因跳过通过运营商发送给手机验证码，所有验证码到后台取
        log.info("验证码为：{}",code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //用户当前输入的手机号和验证码
        String phone = loginForm.getPhone();
        String currentCode = loginForm.getCode();
        //校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号有误");
        }
        //通过手机号查询是否存在用户
        User user = query().eq("phone", phone).one();
        if (user==null) {
            //不存在用户，存储该新用户
            user=new User();
            user.setPhone(phone);
            user.setNickName("user"+RandomUtil.randomString(6));
            user.setIcon("/imgs/icons/user5-icon.png");
            save(user);
        }
        //校验验证码是否正确
        String code = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        if (currentCode==null || !code.equals(currentCode)) {
            //当前用户输入验证码为空或者验证码输入有误
            return Result.fail("验证码有误");
        }
        //存储基本用户信息到Redis
        UserDTO userDTO=new UserDTO();
        BeanUtils.copyProperties(user,userDTO);
        //存储到redis的hash类型中
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true).
                        setFieldValueEditor((fieldName,fieldValue)->fieldValue.toString()));
        //随机token
        String token = UUID.randomUUID().toString();
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY+token,userMap);
        //设置token有效时间
        stringRedisTemplate.expire(LOGIN_USER_KEY+token,30, TimeUnit.MINUTES);
        //返回token
        return Result.ok(token);
    }
}
