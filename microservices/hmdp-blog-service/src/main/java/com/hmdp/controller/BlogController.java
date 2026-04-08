package com.hmdp.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.client.AiClient;
import com.hmdp.client.FollowClient;
import com.hmdp.client.UserClient;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.dto.ai.AiModerationCheckRequest;
import com.hmdp.dto.ai.AiModerationCheckResponse;
import com.hmdp.entity.Blog;
import com.hmdp.service.IBlogService;
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

    private static final String BLOG_PENDING_KEY_PREFIX = "ai:moderation:blog:";

    @Resource
    private IBlogService blogService;

    @Resource
    private UserClient userClient;

    @Resource
    private FollowClient followClient;

    @Resource
    private AiClient aiClient;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("Unauthorized");
        }

        AiModerationCheckResponse moderation = reviewBlogIfPossible(blog);
        if (isBlocked(moderation)) {
            return Result.fail("Content blocked: " + moderation.getReason());
        }

        blog.setUserId(user.getId());
        boolean success = blogService.save(blog);
        if (!success) {
            return Result.fail("Create blog failed");
        }

        boolean pending = isPending(moderation);
        if (pending) {
            stringRedisTemplate.opsForValue().set(BLOG_PENDING_KEY_PREFIX + blog.getId(), JSONUtil.toJsonStr(moderation));
        } else {
            pushBlogToFollowers(user.getId(), blog.getId());
        }
        return Result.ok(blog.getId());
    }

    @GetMapping("/{id}")
    public Result queryBlogById(@PathVariable("id") Long id) {
        Blog blog = blogService.getById(id);
        if (blog == null) {
            return Result.fail("Blog not found");
        }
        if (isPendingReview(id) && !canViewPending(blog.getUserId())) {
            return Result.fail("Blog pending review");
        }
        fillBlogUser(blog);
        fillBlogIsLike(blog);
        return Result.ok(blog);
    }

    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("Unauthorized");
        }
        Long userId = user.getId();
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
        List<UserDTO> users = userClient.listByIds(ids);
        if (users == null) {
            users = Collections.emptyList();
        }
        Map<Long, UserDTO> dtoMap = new HashMap<Long, UserDTO>(users.size());
        for (UserDTO user : users) {
            dtoMap.put(user.getId(), user);
        }

        List<UserDTO> result = new ArrayList<UserDTO>(ids.size());
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
                .page(new Page<Blog>(current, MAX_PAGE_SIZE));
        List<Blog> records = canViewPending(userId) ? page.getRecords() : filterPendingBlogs(page.getRecords());
        records.forEach(this::fillBlogUser);
        records.forEach(this::fillBlogIsLike);
        return Result.ok(records);
    }

    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("Unauthorized");
        }
        Page<Blog> page = blogService.query()
                .eq("user_id", user.getId())
                .page(new Page<Blog>(current, MAX_PAGE_SIZE));

        List<Blog> records = page.getRecords();
        records.forEach(this::fillBlogUser);
        records.forEach(this::fillBlogIsLike);
        return Result.ok(records);
    }

    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        Page<Blog> page = blogService.query()
                .orderByDesc("liked")
                .page(new Page<Blog>(current, MAX_PAGE_SIZE));

        List<Blog> records = filterPendingBlogs(page.getRecords());
        records.forEach(this::fillBlogUser);
        records.forEach(this::fillBlogIsLike);
        return Result.ok(records);
    }

    @GetMapping("/of/follow")
    public Result queryBlogOfFollow(
            @RequestParam("lastId") Long lastId,
            @RequestParam(value = "offset", defaultValue = "0") Integer offset
    ) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("Unauthorized");
        }
        Long userId = user.getId();
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

        List<Long> ids = new ArrayList<Long>(typedTuples.size());
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
        blogs = filterPendingBlogs(blogs);
        blogs.forEach(this::fillBlogUser);
        blogs.forEach(this::fillBlogIsLike);

        ScrollResult result = new ScrollResult();
        result.setList(blogs);
        result.setMinTime(minTime);
        result.setOffset(os);
        return Result.ok(result);
    }

    @PostMapping("/internal/by-shop-ids")
    public List<Blog> listByShopIds(@RequestBody List<Long> shopIds) {
        if (shopIds == null || shopIds.isEmpty()) {
            return Collections.emptyList();
        }
        return filterPendingBlogs(blogService.query().in("shop_id", shopIds).orderByDesc("liked").orderByDesc("create_time").list());
    }

    @PostMapping("/internal/by-ids")
    public List<Blog> listByIds(@RequestBody List<Long> blogIds) {
        if (blogIds == null || blogIds.isEmpty()) {
            return Collections.emptyList();
        }
        String idStr = StrUtil.join(",", blogIds);
        return filterPendingBlogs(blogService.query().in("id", blogIds).last("order by field(id," + idStr + ")").list());
    }

    @GetMapping("/internal/hot")
    public List<Blog> listHotInternal(@RequestParam(value = "limit", defaultValue = "20") Integer limit) {
        int safeLimit = limit == null || limit <= 0 ? 20 : Math.min(limit, 100);
        return filterPendingBlogs(blogService.query().orderByDesc("liked").last("limit " + safeLimit).list());
    }

    private void pushBlogToFollowers(Long authorId, Long blogId) {
        List<Long> followers = followClient.queryFollowers(authorId);
        if (followers == null || followers.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Long followerId : followers) {
            String key = FEED_KEY + followerId;
            stringRedisTemplate.opsForZSet().add(key, blogId.toString(), now);
        }
    }

    private AiModerationCheckResponse reviewBlogIfPossible(Blog blog) {
        try {
            AiModerationCheckRequest request = new AiModerationCheckRequest();
            request.setType("blog");
            request.setImages(blog.getImages());
            request.setContent(StrUtil.blankToDefault(blog.getTitle(), "") + "\n" + StrUtil.blankToDefault(blog.getContent(), ""));
            return aiClient.check(request);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isBlocked(AiModerationCheckResponse moderation) {
        return moderation != null && "high".equalsIgnoreCase(moderation.getRiskLevel());
    }

    private boolean isPending(AiModerationCheckResponse moderation) {
        return moderation != null && "medium".equalsIgnoreCase(moderation.getRiskLevel());
    }

    private boolean isPendingReview(Long blogId) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(BLOG_PENDING_KEY_PREFIX + blogId));
    }

    private boolean canViewPending(Long authorId) {
        UserDTO current = UserHolder.getUser();
        return current != null && authorId != null && authorId.equals(current.getId());
    }

    private List<Blog> filterPendingBlogs(List<Blog> blogs) {
        if (blogs == null || blogs.isEmpty()) {
            return Collections.emptyList();
        }
        return blogs.stream().filter(blog -> !isPendingReview(blog.getId())).collect(Collectors.toList());
    }

    private void fillBlogUser(Blog blog) {
        UserDTO user = userClient.getUser(blog.getUserId());
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
