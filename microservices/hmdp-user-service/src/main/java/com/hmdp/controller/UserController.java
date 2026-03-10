package com.hmdp.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.entity.UserInfo;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;

@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Value("${auth.mode:jwt}")
    private String authMode;

    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        return userService.sendCode(phone, session);
    }

    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session) {
        return userService.login(loginForm, session);
    }

    @PostMapping("/logout")
    public Result logout(HttpServletRequest request) {
        if (isJwtMode()) {
            return Result.ok();
        }
        String token = request.getHeader("Authorization");
        if (StrUtil.isBlank(token)) {
            token = request.getHeader("authorization");
        }
        if (StrUtil.isBlank(token)) {
            return Result.ok();
        }
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        stringRedisTemplate.delete(LOGIN_USER_KEY + token);
        return Result.ok();
    }

    @GetMapping("/me")
    public Result me() {
        return Result.ok(UserHolder.getUser());
    }

    @PostMapping("/sign")
    public Result sign() {
        return userService.sign();
    }

    @GetMapping("/sign/count")
    public Result signCount() {
        return userService.signCount();
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId) {
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        return Result.ok(info);
    }

    @GetMapping("/basic/{id}")
    public UserDTO basic(@PathVariable("id") Long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            return null;
        }
        return BeanUtil.copyProperties(user, UserDTO.class);
    }

    @PostMapping("/basic/list")
    public List<UserDTO> basicList(@RequestBody List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<User> users = userService.listByIds(ids);
        return users.stream().map(u -> BeanUtil.copyProperties(u, UserDTO.class)).collect(Collectors.toList());
    }

    private boolean isJwtMode() {
        return "jwt".equalsIgnoreCase(authMode);
    }
}
