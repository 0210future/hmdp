package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.support.LocalCodeStore;
import com.hmdp.utils.JwtUtils;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_CODE_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_CODE_TTL;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;
import static com.hmdp.utils.RedisConstants.USER_SIGN_KEY;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private LocalCodeStore localCodeStore;

    @Value("${auth.mode:jwt}")
    private String authMode;

    @Value("${auth.code-store:memory}")
    private String codeStore;

    @Value("${auth.jwt.secret:hmdp-secret-2026}")
    private String jwtSecret;

    @Value("${auth.jwt.ttl-minutes:120}")
    private long jwtTtlMinutes;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("Invalid phone number");
        }

        String code = RandomUtil.randomNumbers(6);
        if (useMemoryCodeStore()) {
            localCodeStore.store(phone, code, LOGIN_CODE_TTL);
        } else {
            stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        }
        log.debug("send sms code: {}", code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("Invalid phone number");
        }

        String cacheCode = useMemoryCodeStore()
                ? localCodeStore.get(phone)
                : stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail("Invalid verification code");
        }

        User user = query().eq("phone", phone).one();
        if (user == null) {
            user = createUserWithPhone(phone);
        }

        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        if (isJwtMode()) {
            Map<String, Object> claims = new HashMap<>();
            claims.put("id", userDTO.getId());
            claims.put("nickName", userDTO.getNickName());
            claims.put("icon", userDTO.getIcon());
            String token = JwtUtils.createToken(claims, jwtSecret, TimeUnit.MINUTES.toSeconds(jwtTtlMinutes));
            return Result.ok(token);
        }

        String token = UUID.randomUUID().toString();
        Map<String, Object> userMap = BeanUtil.beanToMap(
                userDTO,
                new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString())
        );

        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
        return Result.ok(token);
    }

    @Override
    public Result sign() {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("Unauthorized");
        }

        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + user.getId() + keySuffix;

        int dayOfMonth = now.getDayOfMonth();
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("Unauthorized");
        }

        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + user.getId() + keySuffix;

        int dayOfMonth = now.getDayOfMonth();
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                        .valueAt(0)
        );

        if (result == null || result.isEmpty()) {
            return Result.ok(0);
        }

        Long num = result.get(0);
        if (num == null || num == 0) {
            return Result.ok(0);
        }

        int count = 0;
        while ((num & 1) == 1) {
            count++;
            num >>>= 1;
        }
        return Result.ok(count);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }

    private boolean useMemoryCodeStore() {
        return "memory".equalsIgnoreCase(codeStore) || isJwtMode();
    }

    private boolean isJwtMode() {
        return "jwt".equalsIgnoreCase(authMode);
    }
}
