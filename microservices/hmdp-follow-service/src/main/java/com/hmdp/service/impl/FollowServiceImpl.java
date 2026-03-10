package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.client.UserClient;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    private static final String FOLLOW_KEY_PREFIX = "follows:";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UserClient userClient;

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("Unauthorized");
        }
        Long userId = user.getId();
        String key = FOLLOW_KEY_PREFIX + userId;

        if (Boolean.TRUE.equals(isFollow)) {
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean success = save(follow);
            if (success) {
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            }
        } else {
            LambdaQueryWrapper<Follow> wrapper = new LambdaQueryWrapper<Follow>()
                    .eq(Follow::getUserId, userId)
                    .eq(Follow::getFollowUserId, followUserId);
            boolean success = remove(wrapper);
            if (success) {
                stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("Unauthorized");
        }
        Long userId = user.getId();
        Long count = query()
                .eq("user_id", userId)
                .eq("follow_user_id", followUserId)
                .count();
        return Result.ok(count != null && count > 0);
    }

    @Override
    public Result followCommons(Long otherUserId) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("Unauthorized");
        }
        Long userId = user.getId();
        String key1 = FOLLOW_KEY_PREFIX + userId;
        String key2 = FOLLOW_KEY_PREFIX + otherUserId;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
        if (intersect == null || intersect.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> users = userClient.listByIds(ids);
        if (users == null) {
            users = Collections.emptyList();
        }
        return Result.ok(users);
    }

    @Override
    public List<Long> queryFollowers(Long followUserId) {
        return query().eq("follow_user_id", followUserId).list().stream()
                .map(Follow::getUserId)
                .collect(Collectors.toList());
    }
}


