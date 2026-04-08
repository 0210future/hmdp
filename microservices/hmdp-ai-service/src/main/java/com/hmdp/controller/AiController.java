package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.dto.ai.AiBlogAssistRequest;
import com.hmdp.dto.ai.AiFeedRerankRequest;
import com.hmdp.dto.ai.AiModerationCheckRequest;
import com.hmdp.dto.ai.AiSearchRequest;
import com.hmdp.dto.ai.AiVoucherAssistRequest;
import com.hmdp.service.AiService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/ai")
public class AiController {

    @Resource
    private AiService aiService;

    @PostMapping("/search")
    public Result search(@RequestBody AiSearchRequest request) {
        return Result.ok(aiService.search(request));
    }

    @PostMapping("/blog/assist")
    public Result blogAssist(@RequestBody AiBlogAssistRequest request) {
        return Result.ok(aiService.assistBlog(request));
    }

    @GetMapping("/shop/{shopId}/summary")
    public Result shopSummary(@PathVariable("shopId") Long shopId) {
        return Result.ok(aiService.getShopSummary(shopId));
    }

    @PostMapping("/moderation/check")
    public Result moderation(@RequestBody AiModerationCheckRequest request) {
        return Result.ok(aiService.checkModeration(request));
    }

    @PostMapping("/feed/rerank")
    public Result rerankFeed(@RequestBody AiFeedRerankRequest request) {
        return Result.ok(aiService.rerankFeed(request));
    }

    @PostMapping("/voucher/assist")
    public Result assistVoucher(@RequestBody AiVoucherAssistRequest request) {
        return Result.ok(aiService.assistVoucher(request));
    }
}
