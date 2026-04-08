package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.client.AiClient;
import com.hmdp.client.UserClient;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.dto.ai.AiModerationCheckRequest;
import com.hmdp.dto.ai.AiModerationCheckResponse;
import com.hmdp.entity.BlogComments;
import com.hmdp.mapper.BlogCommentsMapper;
import com.hmdp.service.IBlogCommentsService;
import com.hmdp.service.IBlogService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements IBlogCommentsService {

    private static final String COMMENT_PENDING_KEY_PREFIX = "ai:moderation:comment:";

    @Resource
    private UserClient userClient;

    @Resource
    private AiClient aiClient;

    @Resource
    private IBlogService blogService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result saveComment(BlogComments comment) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("Unauthorized");
        }

        AiModerationCheckResponse moderation = reviewCommentIfPossible(comment);
        if (moderation != null && "high".equalsIgnoreCase(moderation.getRiskLevel())) {
            return Result.fail("Content blocked: " + moderation.getReason());
        }

        comment.setUserId(user.getId());
        if (comment.getParentId() == null) {
            comment.setParentId(0L);
        }
        if (comment.getAnswerId() == null) {
            comment.setAnswerId(0L);
        }
        comment.setStatus(moderation != null && "medium".equalsIgnoreCase(moderation.getRiskLevel()));

        boolean success = save(comment);
        if (!success) {
            return Result.fail("Create comment failed");
        }
        if (Boolean.TRUE.equals(comment.getStatus()) && moderation != null) {
            stringRedisTemplate.opsForValue().set(COMMENT_PENDING_KEY_PREFIX + comment.getId(), JSONUtil.toJsonStr(moderation));
        }
        blogService.update().setSql("comments = comments + 1").eq("id", comment.getBlogId()).update();
        return Result.ok(comment.getId());
    }

    @Override
    public Result queryCommentsByBlogId(Long blogId, Integer current) {
        Page<BlogComments> page = query()
                .eq("blog_id", blogId)
                .eq("status", 0)
                .orderByDesc("create_time")
                .page(new Page<BlogComments>(current, SystemConstants.MAX_PAGE_SIZE));

        List<BlogComments> records = page.getRecords();
        for (BlogComments comment : records) {
            UserDTO user = userClient.getUser(comment.getUserId());
            if (user != null) {
                comment.setName(user.getNickName());
                comment.setIcon(user.getIcon());
            }
        }
        return Result.ok(records);
    }

    private AiModerationCheckResponse reviewCommentIfPossible(BlogComments comment) {
        try {
            AiModerationCheckRequest request = new AiModerationCheckRequest();
            request.setType("comment");
            request.setContent(comment.getContent());
            return aiClient.check(request);
        } catch (Exception e) {
            return null;
        }
    }
}
