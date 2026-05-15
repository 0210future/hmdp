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

/**
 * AI 模块统一入口，提供搜索、文案辅助、摘要、审核与轻量重排能力。
 */
@RestController
@RequestMapping("/ai")
public class AiController {

    @Resource
    private AiService aiService;

    /**
     * AI 搜索：根据用户自然语言查询意图筛选并重排店铺。
     */
    @PostMapping("/search")
    public Result search(@RequestBody AiSearchRequest request) {
        return Result.ok(aiService.search(request));
    }

    /**
     * 博客写作辅助：生成标题建议、亮点摘要和润色后的探店文案。
     */
    @PostMapping("/blog/assist")
    public Result blogAssist(@RequestBody AiBlogAssistRequest request) {
        return Result.ok(aiService.assistBlog(request));
    }

    /**
     * 店铺摘要：聚合店铺与博客数据，生成标签、适合人群和风险提示。
     */
    @GetMapping("/shop/{shopId}/summary")
    public Result shopSummary(@PathVariable("shopId") Long shopId) {
        return Result.ok(aiService.getShopSummary(shopId));
    }

    /**
     * 内容审核：返回风险等级、标签和建议动作。
     */
    @PostMapping("/moderation/check")
    public Result moderation(@RequestBody AiModerationCheckRequest request) {
        return Result.ok(aiService.checkModeration(request));
    }

    /**
     * 信息流轻量重排：基于热度、距离和标签命中进行排序。
     */
    @PostMapping("/feed/rerank")
    public Result rerankFeed(@RequestBody AiFeedRerankRequest request) {
        return Result.ok(aiService.rerankFeed(request));
    }

    /**
     * 优惠券辅助：生成券标题、副标题、规则建议和运营亮点。
     */
    @PostMapping("/voucher/assist")
    public Result assistVoucher(@RequestBody AiVoucherAssistRequest request) {
        return Result.ok(aiService.assistVoucher(request));
    }
}
