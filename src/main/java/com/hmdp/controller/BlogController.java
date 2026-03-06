package com.hmdp.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.FEED_KEY;
import static com.hmdp.utils.SystemConstants.DEFAULT_PAGE_SIZE;
import static com.hmdp.utils.SystemConstants.MAX_PAGE_SIZE;

@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private IBlogService blogService;

    @Resource
    private IUserService userService;

    @Resource
    private IFollowService followService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        boolean success = blogService.save(blog);
        if (!success) {
            return Result.fail("Create blog failed");
        }

        List<Follow> followers = followService.query()
                .eq("follow_user_id", user.getId())
                .list();
        long now = System.currentTimeMillis();
        for (Follow follower : followers) {
            String key = FEED_KEY + follower.getUserId();
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), now);
        }
        return Result.ok(blog.getId());
    }

    @GetMapping("/{id}")
    public Result queryBlogById(@PathVariable("id") Long id) {
        Blog blog = blogService.getById(id);
        if (blog == null) {
            return Result.fail("Blog not found");
        }
        fillBlogUser(blog);
        fillBlogIsLike(blog);
        return Result.ok(blog);
    }

    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        Long userId = UserHolder.getUser().getId();
        String key = BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());

        boolean success;
        if (score == null) {
            success = blogService.update().setSql("liked = liked + 1").eq("id", id).update();
            if (success) {
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        } else {
            success = blogService.update().setSql("liked = liked - 1").eq("id", id).gt("liked", 0).update();
            if (success) {
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        return success ? Result.ok() : Result.fail("Operation failed");
    }

    @GetMapping("/likes/{id}")
    public Result queryBlogLikes(@PathVariable("id") Long id) {
        String key = BLOG_LIKED_KEY + id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().reverseRange(key, 0, 4);
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        List<User> users = userService.listByIds(ids);
        Map<Long, UserDTO> dtoMap = new HashMap<>(users.size());
        for (User user : users) {
            dtoMap.put(user.getId(), BeanUtil.copyProperties(user, UserDTO.class));
        }

        List<UserDTO> result = new ArrayList<>(ids.size());
        for (Long uid : ids) {
            UserDTO dto = dtoMap.get(uid);
            if (dto != null) {
                result.add(dto);
            }
        }
        return Result.ok(result);
    }

    @GetMapping("/of/user")
    public Result queryBlogByUser(
            @RequestParam("id") Long userId,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        Page<Blog> page = blogService.query()
                .eq("user_id", userId)
                .page(new Page<>(current, MAX_PAGE_SIZE));
        List<Blog> records = page.getRecords();
        records.forEach(this::fillBlogUser);
        records.forEach(this::fillBlogIsLike);
        return Result.ok(records);
    }

    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        UserDTO user = UserHolder.getUser();
        Page<Blog> page = blogService.query()
                .eq("user_id", user.getId())
                .page(new Page<>(current, MAX_PAGE_SIZE));

        List<Blog> records = page.getRecords();
        records.forEach(this::fillBlogUser);
        records.forEach(this::fillBlogIsLike);
        return Result.ok(records);
    }

    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        Page<Blog> page = blogService.query()
                .orderByDesc("liked")
                .page(new Page<>(current, MAX_PAGE_SIZE));

        List<Blog> records = page.getRecords();
        records.forEach(this::fillBlogUser);
        records.forEach(this::fillBlogIsLike);
        return Result.ok(records);
    }

    @GetMapping("/of/follow")
    public Result queryBlogOfFollow(
            @RequestParam("lastId") Long lastId,
            @RequestParam(value = "offset", defaultValue = "0") Integer offset
    ) {
        Long userId = UserHolder.getUser().getId();
        String key = FEED_KEY + userId;

        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, lastId, offset, DEFAULT_PAGE_SIZE);

        if (typedTuples == null || typedTuples.isEmpty()) {
            ScrollResult empty = new ScrollResult();
            empty.setList(Collections.emptyList());
            empty.setMinTime(lastId);
            empty.setOffset(offset);
            return Result.ok(empty);
        }

        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0L;
        int os = 0;
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            if (tuple.getValue() == null || tuple.getScore() == null) {
                continue;
            }
            ids.add(Long.valueOf(tuple.getValue()));
            long time = tuple.getScore().longValue();
            if (time == minTime) {
                os++;
            } else {
                minTime = time;
                os = 1;
            }
        }

        if (ids.isEmpty()) {
            ScrollResult empty = new ScrollResult();
            empty.setList(Collections.emptyList());
            empty.setMinTime(lastId);
            empty.setOffset(offset);
            return Result.ok(empty);
        }

        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = blogService.query()
                .in("id", ids)
                .last("order by field(id," + idStr + ")")
                .list();
        blogs.forEach(this::fillBlogUser);
        blogs.forEach(this::fillBlogIsLike);

        ScrollResult result = new ScrollResult();
        result.setList(blogs);
        result.setMinTime(minTime);
        result.setOffset(os);
        return Result.ok(result);
    }

    private void fillBlogUser(Blog blog) {
        User user = userService.getById(blog.getUserId());
        if (user == null) {
            return;
        }
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    private void fillBlogIsLike(Blog blog) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            blog.setIsLike(false);
            return;
        }
        String key = BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, user.getId().toString());
        blog.setIsLike(score != null);
    }
}