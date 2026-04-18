package com.hmdp.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.ai.AiProperties;
import com.hmdp.client.BlogClient;
import com.hmdp.client.ShopClient;
import com.hmdp.client.VoucherClient;
import com.hmdp.dto.ai.AiBlogAssistRequest;
import com.hmdp.dto.ai.AiBlogAssistResponse;
import com.hmdp.dto.ai.AiFeedItem;
import com.hmdp.dto.ai.AiFeedRerankRequest;
import com.hmdp.dto.ai.AiFeedRerankResponse;
import com.hmdp.dto.ai.AiModerationCheckRequest;
import com.hmdp.dto.ai.AiModerationCheckResponse;
import com.hmdp.dto.ai.AiSearchFilter;
import com.hmdp.dto.ai.AiSearchRequest;
import com.hmdp.dto.ai.AiSearchResponse;
import com.hmdp.dto.ai.AiSearchShopVO;
import com.hmdp.dto.ai.AiShopSummaryResponse;
import com.hmdp.dto.ai.AiVoucherAssistRequest;
import com.hmdp.dto.ai.AiVoucherAssistResponse;
import com.hmdp.model.BlogRecord;
import com.hmdp.model.ShopRecord;
import com.hmdp.model.ShopTypeRecord;
import com.hmdp.model.VoucherRecord;
import com.hmdp.util.AiIntentParser;
import com.hmdp.util.ModerationRuleEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AiService {

    private static final String SHOP_SUMMARY_KEY_PREFIX = "ai:shop:summary:";

    @Resource
    private ShopClient shopClient;

    @Resource
    private BlogClient blogClient;

    @Resource
    private VoucherClient voucherClient;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private LlmGateway llmGateway;

    @Resource
    private AiProperties aiProperties;

    public AiSearchResponse search(AiSearchRequest request) {
        AiSearchRequest safeRequest = request == null ? new AiSearchRequest() : request;
        List<ShopRecord> shops = safeListShops();
        List<ShopTypeRecord> shopTypes = safeListTypes();
        // 第一阶段先走“规则解析 + 结构化过滤 + 轻量重排”，保证可解释和稳定落地。
        AiSearchFilter filter = AiIntentParser.parse(safeRequest.getQuery(), shopTypes);
        List<ShopRecord> filtered = new ArrayList<ShopRecord>();
        for (ShopRecord shop : shops) {
            Double distance = resolveDistance(shop, safeRequest.getX(), safeRequest.getY());
            if (distance != null) {
                shop.setDistance(distance);
            }
            if (!matchesType(shop, filter) || !matchesPrice(shop, filter) || !matchesDistance(distance, filter) || !matchesOpenHours(shop, filter)) {
                continue;
            }
            filtered.add(shop);
        }

        Map<Long, ShopTypeRecord> typeMap = shopTypes.stream().collect(Collectors.toMap(ShopTypeRecord::getId, item -> item, (a, b) -> a));
        Map<Long, List<BlogRecord>> blogMap = groupBlogsByShopIds(filtered.stream().map(ShopRecord::getId).collect(Collectors.toList()));
        List<ScoredShop> scoredShops = new ArrayList<ScoredShop>();
        for (ShopRecord shop : filtered) {
            // 博客文本不直接做全文检索，而是提炼场景标签参与重排。
            List<String> tags = deriveTags(shop, blogMap.get(shop.getId()));
            double score = calculateSearchScore(shop, tags, filter);
            scoredShops.add(new ScoredShop(shop, typeMap.get(shop.getTypeId()), tags, score, buildShopReason(shop, typeMap.get(shop.getTypeId()), tags, filter)));
        }
        Collections.sort(scoredShops, new Comparator<ScoredShop>() {
            @Override
            public int compare(ScoredShop a, ScoredShop b) {
                return Double.compare(b.score, a.score);
            }
        });

        int page = safePositive(safeRequest.getPage(), 1);
        int pageSize = safePositive(safeRequest.getPageSize(), 10);
        int fromIndex = Math.min((page - 1) * pageSize, scoredShops.size());
        int toIndex = Math.min(fromIndex + pageSize, scoredShops.size());
        List<AiSearchShopVO> result = new ArrayList<AiSearchShopVO>();
        for (ScoredShop scoredShop : scoredShops.subList(fromIndex, toIndex)) {
            AiSearchShopVO vo = new AiSearchShopVO();
            vo.setId(scoredShop.shop.getId());
            vo.setName(scoredShop.shop.getName());
            vo.setTypeId(scoredShop.shop.getTypeId());
            vo.setTypeName(scoredShop.type == null ? null : scoredShop.type.getName());
            vo.setArea(scoredShop.shop.getArea());
            vo.setAddress(scoredShop.shop.getAddress());
            vo.setAvgPrice(scoredShop.shop.getAvgPrice());
            vo.setSold(scoredShop.shop.getSold());
            vo.setComments(scoredShop.shop.getComments());
            vo.setScore(displayScore(scoredShop.shop));
            vo.setOpenHours(scoredShop.shop.getOpenHours());
            vo.setDistance(scoredShop.shop.getDistance());
            vo.setMatchedTags(scoredShop.tags);
            vo.setMatchReason(scoredShop.reason);
            result.add(vo);
        }

        AiSearchResponse response = new AiSearchResponse();
        response.setRewrittenQuery(filter.getRewrittenQuery());
        response.setFilters(filter);
        response.setShops(result);
        response.setAnswer(buildSearchAnswer(filter, result));
        return response;
    }

    public AiBlogAssistResponse assistBlog(AiBlogAssistRequest request) {
        AiBlogAssistRequest safeRequest = request == null ? new AiBlogAssistRequest() : request;
        ShopRecord shop = safeRequest.getShopId() == null ? null : safeGetShop(safeRequest.getShopId());
        AiShopSummaryResponse summary = shop == null ? null : getShopSummary(shop.getId());
        List<String> highlights = buildHighlights(shop, summary);
        String rewrittenContent = buildRewrittenContent(shop, summary, safeRequest, highlights);
        Optional<String> llmRewrite = llmGateway.complete(
                "你是点评平台的文案助手，请根据真实商户信息润色探店文案。",
                "商户信息：" + JSONUtil.toJsonStr(shop) + "\n风格：" + StrUtil.blankToDefault(safeRequest.getStyle(), "种草")
                        + "\n用户草稿：" + StrUtil.blankToDefault(safeRequest.getContent(), "")
        );
        if (llmRewrite.isPresent()) {
            rewrittenContent = llmRewrite.get();
        }

        AiBlogAssistResponse response = new AiBlogAssistResponse();
        response.setTitleSuggestions(buildTitleSuggestions(shop, summary));
        response.setHighlights(highlights);
        response.setTags(summary == null ? Collections.<String>emptyList() : summary.getTags());
        response.setSummary(summary == null ? buildSummaryText(shop, highlights) : summary.getSummary());
        response.setRewrittenContent(rewrittenContent);
        return response;
    }

    public AiShopSummaryResponse getShopSummary(Long shopId) {
        String cacheKey = SHOP_SUMMARY_KEY_PREFIX + shopId;
        String cache = stringRedisTemplate.opsForValue().get(cacheKey);
        if (StrUtil.isNotBlank(cache)) {
            return JSONUtil.toBean(cache, AiShopSummaryResponse.class);
        }
        // 摘要优先读缓存，避免详情页重复打开时反复聚合博客内容。
        ShopRecord shop = safeGetShop(shopId);
        if (shop == null) {
            return null;
        }
        Map<Long, List<BlogRecord>> blogMap = groupBlogsByShopIds(Collections.singletonList(shopId));
        AiShopSummaryResponse response = buildShopSummary(shop, blogMap.get(shopId));
        cacheShopSummary(response);
        return response;
    }

    public AiModerationCheckResponse checkModeration(AiModerationCheckRequest request) {
        return ModerationRuleEngine.check(request);
    }

    public AiFeedRerankResponse rerankFeed(AiFeedRerankRequest request) {
        AiFeedRerankRequest safeRequest = request == null ? new AiFeedRerankRequest() : request;
        // 当前是轻量实验版排序：热度、距离、场景标签三类信号混排，不依赖训练模型。
        List<BlogRecord> blogs = loadFeedCandidates(safeRequest);
        Map<Long, ShopRecord> shopMap = safeListShops().stream().collect(Collectors.toMap(ShopRecord::getId, item -> item, (a, b) -> a));
        List<String> desiredTags = AiIntentParser.parse(StrUtil.blankToDefault(safeRequest.getQuery(), ""), safeListTypes()).getScenes();
        List<AiFeedItem> items = new ArrayList<AiFeedItem>();
        for (BlogRecord blog : blogs) {
            ShopRecord shop = shopMap.get(blog.getShopId());
            if (shop == null) {
                continue;
            }
            List<String> tags = deriveTags(shop, Collections.singletonList(blog));
            double score = defaultInt(blog.getLiked()) * 1.2D + defaultInt(blog.getComments()) * 1.5D + displayScore(shop) * 10D;
            Double distance = resolveDistance(shop, safeRequest.getX(), safeRequest.getY());
            if (distance != null) {
                score += Math.max(0D, 5000D - distance) / 500D;
            }
            for (String tag : desiredTags) {
                if (tags.contains(tag)) {
                    score += 8D;
                }
            }
            AiFeedItem item = new AiFeedItem();
            item.setBlogId(blog.getId());
            item.setShopId(blog.getShopId());
            item.setTitle(StrUtil.blankToDefault(blog.getTitle(), shop.getName() + "探店笔记"));
            item.setSummary(buildFeedSummary(blog, shop, tags));
            item.setReason(buildFeedReason(distance, tags));
            item.setScore(score);
            items.add(item);
        }
        Collections.sort(items, new Comparator<AiFeedItem>() {
            @Override
            public int compare(AiFeedItem a, AiFeedItem b) {
                return Double.compare(defaultDouble(b.getScore()), defaultDouble(a.getScore()));
            }
        });
        int pageSize = safePositive(safeRequest.getPageSize(), 10);
        if (items.size() > pageSize) {
            items = new ArrayList<AiFeedItem>(items.subList(0, pageSize));
        }
        AiFeedRerankResponse response = new AiFeedRerankResponse();
        response.setItems(items);
        response.setStrategy("hotness + distance + tag-match");
        return response;
    }

    public AiVoucherAssistResponse assistVoucher(AiVoucherAssistRequest request) {
        AiVoucherAssistRequest safeRequest = request == null ? new AiVoucherAssistRequest() : request;
        ShopRecord shop = safeRequest.getShopId() == null ? null : safeGetShop(safeRequest.getShopId());
        AiShopSummaryResponse summary = shop == null ? null : getShopSummary(shop.getId());
        List<VoucherRecord> vouchers = safeRequest.getShopId() == null ? Collections.<VoucherRecord>emptyList() : safeListVouchers(safeRequest.getShopId());
        String goal = StrUtil.blankToDefault(safeRequest.getGoal(), "提升到店转化");

        AiVoucherAssistResponse response = new AiVoucherAssistResponse();
        response.setTitle(StrUtil.blankToDefault(safeRequest.getTitle(), buildVoucherTitle(shop, goal)));
        response.setSubTitle(StrUtil.blankToDefault(safeRequest.getSubTitle(), buildVoucherSubtitle(summary, goal)));
        response.setRules(StrUtil.blankToDefault(safeRequest.getRules(), buildVoucherRules(vouchers)));
        response.setHighlights(buildVoucherHighlights(shop, summary, vouchers));
        response.setSuggestion("建议围绕\"" + goal + "\"突出" + StrUtil.blankToDefault(first(summary == null ? null : summary.getTags()), "人气口碑") + "，并保留清晰使用门槛。");
        return response;
    }

    @Async("aiExecutor")
    @EventListener(ApplicationReadyEvent.class)
    public void warmupShopSummaries() {
        if (!aiProperties.getSummary().isWarmupEnabled()) {
            return;
        }
        try {
            // 启动后异步预热摘要，减少首个详情页请求的等待时间。
            List<ShopRecord> shops = safeListShops();
            Map<Long, List<BlogRecord>> blogMap = groupBlogsByShopIds(shops.stream().map(ShopRecord::getId).collect(Collectors.toList()));
            for (ShopRecord shop : shops) {
                cacheShopSummary(buildShopSummary(shop, blogMap.get(shop.getId())));
            }
            log.info("AI summary warmup finished, count={}", shops.size());
        } catch (Exception e) {
            log.warn("AI summary warmup failed", e);
        }
    }

    private AiShopSummaryResponse buildShopSummary(ShopRecord shop, List<BlogRecord> blogs) {
        List<String> tags = deriveTags(shop, blogs);
        List<String> audiences = deriveAudiences(tags, shop);
        List<String> warnings = deriveWarnings(tags, shop, blogs);
        AiShopSummaryResponse response = new AiShopSummaryResponse();
        response.setShopId(shop.getId());
        response.setTags(tags);
        response.setAudiences(audiences);
        response.setWarnings(warnings);
        response.setSummary(buildShopSummarySentence(shop, tags, audiences, warnings));
        response.setGeneratedAt(LocalDateTime.now());
        return response;
    }

    private void cacheShopSummary(AiShopSummaryResponse response) {
        if (response == null || response.getShopId() == null) {
            return;
        }
        stringRedisTemplate.opsForValue().set(SHOP_SUMMARY_KEY_PREFIX + response.getShopId(), JSONUtil.toJsonStr(response), aiProperties.getSummary().getTtlMinutes(), TimeUnit.MINUTES);
    }

    private Map<Long, List<BlogRecord>> groupBlogsByShopIds(List<Long> shopIds) {
        if (shopIds == null || shopIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            List<BlogRecord> blogs = blogClient.listByShopIds(shopIds);
            if (blogs == null) {
                return Collections.emptyMap();
            }
            return blogs.stream().collect(Collectors.groupingBy(BlogRecord::getShopId));
        } catch (Exception e) {
            log.warn("Load blogs by shop ids failed", e);
            return Collections.emptyMap();
        }
    }

    private List<BlogRecord> loadFeedCandidates(AiFeedRerankRequest request) {
        try {
            if (CollUtil.isNotEmpty(request.getBlogIds())) {
                List<BlogRecord> blogs = blogClient.listByIds(request.getBlogIds());
                return blogs == null ? Collections.<BlogRecord>emptyList() : blogs;
            }
            List<BlogRecord> blogs = blogClient.listHot(safePositive(request.getPageSize(), 10) * 2);
            return blogs == null ? Collections.<BlogRecord>emptyList() : blogs;
        } catch (Exception e) {
            log.warn("Load feed candidates failed", e);
            return Collections.emptyList();
        }
    }

    private List<String> buildHighlights(ShopRecord shop, AiShopSummaryResponse summary) {
        List<String> highlights = new ArrayList<String>();
        if (shop != null) {
            highlights.add(shop.getName() + "位于" + StrUtil.blankToDefault(shop.getArea(), "热门商圈"));
            if (shop.getAvgPrice() != null) {
                highlights.add("人均约" + shop.getAvgPrice() + "元");
            }
            if (StrUtil.isNotBlank(shop.getOpenHours())) {
                highlights.add("营业时间" + shop.getOpenHours());
            }
        }
        if (summary != null && CollUtil.isNotEmpty(summary.getTags())) {
            highlights.addAll(summary.getTags());
        }
        return deduplicate(highlights, 5);
    }

    private List<String> buildTitleSuggestions(ShopRecord shop, AiShopSummaryResponse summary) {
        String shopName = shop == null ? "这家店" : shop.getName();
        String audience = first(summary == null ? null : summary.getAudiences());
        return deduplicate(Arrays.asList(
                shopName + "值不值得去？我的一次真实体验",
                shopName + "探店实录：适合" + StrUtil.blankToDefault(audience, "普通聚会") + "吗？",
                "去了" + shopName + "之后，我把重点都整理好了"
        ), 3);
    }

    private String buildRewrittenContent(ShopRecord shop, AiShopSummaryResponse summary, AiBlogAssistRequest request, List<String> highlights) {
        String style = StrUtil.blankToDefault(request.getStyle(), "种草");
        String original = StrUtil.blankToDefault(request.getContent(), "");
        String intro = shop == null ? "这次体验整体还不错。" : "这次去" + shop.getName() + "，整体感受挺鲜明。";
        String middle = highlights.isEmpty() ? "" : "最有记忆点的是：" + String.join("、", highlights) + "。";
        String close = "如果你最近也在找类似氛围的店，可以把它加入待去清单。";
        if ("客观评测".equals(style)) {
            close = "如果你更看重信息透明和决策效率，这家店的参考点会比较清晰。";
        } else if ("朋友圈短文案".equals(style)) {
            close = "一句话总结：这次没白来，下次还会想约朋友再来一次。";
        }
        if (StrUtil.isNotBlank(original)) {
            return intro + middle + "我自己的原始感受是：" + original + " " + close;
        }
        return intro + middle + close;
    }

    private String buildSummaryText(ShopRecord shop, List<String> highlights) {
        if (shop == null) {
            return "这是一条根据现有草稿整理出的探店摘要。";
        }
        return shop.getName() + "整体偏向" + String.join("、", highlights.isEmpty() ? Collections.singletonList("真实体验") : highlights) + "。";
    }

    private List<String> deriveTags(ShopRecord shop, List<BlogRecord> blogs) {
        Set<String> tags = new LinkedHashSet<String>();
        String text = buildBlogText(blogs);
        if (containsAny(text, "浪漫", "约会", "情侣", "氛围", "花园")) {
            tags.add("适合约会");
        }
        if (containsAny(text, "聚餐", "朋友", "多人", "团建") || isType(shop, 2L, 9L)) {
            tags.add("适合聚餐");
        }
        if (containsAny(text, "夜宵", "深夜", "凌晨") || closesLate(shop)) {
            tags.add("适合夜宵");
        }
        if (containsAny(text, "环境", "装修", "氛围", "干净", "高级")) {
            tags.add("环境好");
        }
        if (containsAny(text, "拍照", "出片", "打卡", "好看")) {
            tags.add("出片");
        }
        if ((shop.getAvgPrice() != null && shop.getAvgPrice() <= 90) || containsAny(text, "平价", "实惠", "性价比")) {
            tags.add("性价比高");
        }
        if (containsAny(text, "排队", "等位")) {
            tags.add("排队久");
        }
        if (containsAny(text, "辣", "麻辣")) {
            tags.add("口味偏辣");
        }
        if (closesLate(shop)) {
            tags.add("营业到很晚");
        }
        return new ArrayList<String>(tags);
    }

    private List<String> deriveAudiences(List<String> tags, ShopRecord shop) {
        List<String> audiences = new ArrayList<String>();
        if (tags.contains("适合约会")) {
            audiences.add("情侣约会");
        }
        if (tags.contains("适合聚餐")) {
            audiences.add("朋友聚会");
        }
        if (tags.contains("适合夜宵")) {
            audiences.add("晚间觅食人群");
        }
        if (shop != null && Long.valueOf(2L).equals(shop.getTypeId())) {
            audiences.add("唱歌聚会");
        }
        if (audiences.isEmpty()) {
            audiences.add("附近到店用户");
        }
        return deduplicate(audiences, 3);
    }

    private List<String> deriveWarnings(List<String> tags, ShopRecord shop, List<BlogRecord> blogs) {
        List<String> warnings = new ArrayList<String>();
        if (tags.contains("排队久")) {
            warnings.add("高峰期可能需要等位");
        }
        if (shop != null && shop.getAvgPrice() != null && shop.getAvgPrice() >= 200) {
            warnings.add("客单价相对较高");
        }
        if (blogs == null || blogs.isEmpty()) {
            warnings.add("探店样本较少");
        }
        return warnings;
    }

    private String buildShopSummarySentence(ShopRecord shop, List<String> tags, List<String> audiences, List<String> warnings) {
        StringBuilder builder = new StringBuilder();
        builder.append(shop.getName()).append("位于").append(StrUtil.blankToDefault(shop.getArea(), "热门商圈"));
        if (shop.getAvgPrice() != null) {
            builder.append("，人均约").append(shop.getAvgPrice()).append("元");
        }
        builder.append("，评分约").append(displayScore(shop)).append("。");
        if (CollUtil.isNotEmpty(tags)) {
            builder.append("亮点包括").append(String.join("、", tags)).append("。");
        }
        if (CollUtil.isNotEmpty(audiences)) {
            builder.append("更适合").append(String.join("、", audiences)).append("。");
        }
        if (CollUtil.isNotEmpty(warnings)) {
            builder.append("需要留意").append(String.join("、", warnings)).append("。");
        }
        return builder.toString();
    }

    private double calculateSearchScore(ShopRecord shop, List<String> tags, AiSearchFilter filter) {
        double score = defaultInt(shop.getSold()) * 0.002D + defaultInt(shop.getComments()) * 0.004D + displayScore(shop) * 10D;
        if (shop.getAvgPrice() != null) {
            score -= shop.getAvgPrice() * 0.01D;
        }
        if (shop.getDistance() != null) {
            score += Math.max(0D, 5000D - shop.getDistance()) / 800D;
        }
        for (String scene : filter.getScenes()) {
            if (tags.contains(scene)) {
                score += 12D;
            }
        }
        return score;
    }

    private String buildShopReason(ShopRecord shop, ShopTypeRecord type, List<String> tags, AiSearchFilter filter) {
        List<String> reasons = new ArrayList<String>();
        if (type != null) {
            reasons.add(type.getName() + "类型匹配");
        }
        if (shop.getAvgPrice() != null && filter.getMaxAvgPrice() != null) {
            reasons.add("人均" + shop.getAvgPrice() + "元");
        }
        if (shop.getDistance() != null) {
            reasons.add("距离约" + shop.getDistance().intValue() + "米");
        }
        if (CollUtil.isNotEmpty(tags)) {
            reasons.add("亮点：" + String.join("、", deduplicate(tags, 3)));
        }
        return String.join("，", reasons);
    }

    private String buildSearchAnswer(AiSearchFilter filter, List<AiSearchShopVO> shops) {
        if (shops.isEmpty()) {
            return "我按当前条件筛选后没有找到特别合适的店，可以放宽预算、距离或营业时段再试试。";
        }
        Optional<String> llm = llmGateway.complete(
                "你是点评平台导购助手，请根据候选店铺给出简洁推荐理由。",
                "用户需求：" + filter.getRewrittenQuery() + "\n候选店铺：" + JSONUtil.toJsonStr(shops)
        );
        if (llm.isPresent()) {
            return llm.get();
        }
        AiSearchShopVO first = shops.get(0);
        List<String> others = shops.stream().skip(1).limit(2).map(AiSearchShopVO::getName).collect(Collectors.toList());
        return "优先推荐" + first.getName() + "，因为它在当前条件下匹配度最高；另外也可以关注" + (others.isEmpty() ? "同类其他高分店铺" : String.join("、", others)) + "。";
    }

    private String buildFeedSummary(BlogRecord blog, ShopRecord shop, List<String> tags) {
        String title = StrUtil.blankToDefault(blog.getTitle(), shop.getName());
        String content = StrUtil.sub(StrUtil.blankToDefault(blog.getContent(), ""), 0, 40);
        if (tags.isEmpty()) {
            return title + "。 " + content;
        }
        return title + "，关键词：" + String.join("、", deduplicate(tags, 2)) + "。 " + content;
    }

    private String buildFeedReason(Double distance, List<String> tags) {
        List<String> reasons = new ArrayList<String>();
        reasons.add("热度较高");
        if (distance != null) {
            reasons.add("距离约" + distance.intValue() + "米");
        }
        if (CollUtil.isNotEmpty(tags)) {
            reasons.add("标签命中" + String.join("、", deduplicate(tags, 2)));
        }
        return String.join("，", reasons);
    }

    private String buildVoucherTitle(ShopRecord shop, String goal) {
        String shopName = shop == null ? "本店" : shop.getName();
        if (goal.contains("拉新")) {
            return "新客到店专享券 | " + shopName;
        }
        if (goal.contains("复购")) {
            return "老客回流福利券 | " + shopName;
        }
        return "限时到店福利券 | " + shopName;
    }

    private String buildVoucherSubtitle(AiShopSummaryResponse summary, String goal) {
        return "围绕" + StrUtil.blankToDefault(first(summary == null ? null : summary.getTags()), "人气口碑") + "做转化，目标偏向" + goal;
    }

    private String buildVoucherRules(List<VoucherRecord> vouchers) {
        if (vouchers == null || vouchers.isEmpty()) {
            return "建议设置明确门槛、限定有效期，并说明是否可叠加使用。";
        }
        return StrUtil.blankToDefault(vouchers.get(0).getRules(), "全场通用\n请提前确认营业时间\n不可与其他优惠叠加");
    }

    private List<String> buildVoucherHighlights(ShopRecord shop, AiShopSummaryResponse summary, List<VoucherRecord> vouchers) {
        List<String> highlights = new ArrayList<String>();
        if (shop != null && shop.getAvgPrice() != null) {
            highlights.add("围绕人均" + shop.getAvgPrice() + "元设计档位");
        }
        if (summary != null && CollUtil.isNotEmpty(summary.getTags())) {
            highlights.add("突出" + String.join("、", deduplicate(summary.getTags(), 2)));
        }
        if (vouchers != null && !vouchers.isEmpty()) {
            highlights.add("参考店内已有" + vouchers.size() + "个券模板");
        }
        return highlights;
    }

    private List<ShopRecord> safeListShops() {
        try {
            List<ShopRecord> result = shopClient.listAll();
            return result == null ? Collections.<ShopRecord>emptyList() : result;
        } catch (Exception e) {
            log.warn("Load shops failed", e);
            return Collections.emptyList();
        }
    }

    private List<ShopTypeRecord> safeListTypes() {
        try {
            List<ShopTypeRecord> result = shopClient.listTypes();
            return result == null ? Collections.<ShopTypeRecord>emptyList() : result;
        } catch (Exception e) {
            log.warn("Load shop types failed", e);
            return Collections.emptyList();
        }
    }

    private ShopRecord safeGetShop(Long shopId) {
        try {
            return shopClient.getShop(shopId);
        } catch (Exception e) {
            log.warn("Load shop failed, shopId={}", shopId, e);
            return null;
        }
    }

    private List<VoucherRecord> safeListVouchers(Long shopId) {
        try {
            List<VoucherRecord> vouchers = voucherClient.listByShopId(shopId);
            return vouchers == null ? Collections.<VoucherRecord>emptyList() : vouchers;
        } catch (Exception e) {
            log.warn("Load vouchers failed", e);
            return Collections.emptyList();
        }
    }

    private boolean matchesType(ShopRecord shop, AiSearchFilter filter) {
        return filter.getTypeId() == null || filter.getTypeId().equals(shop.getTypeId());
    }

    private boolean matchesPrice(ShopRecord shop, AiSearchFilter filter) {
        return filter.getMaxAvgPrice() == null || shop.getAvgPrice() == null || shop.getAvgPrice() <= filter.getMaxAvgPrice();
    }

    private boolean matchesDistance(Double distance, AiSearchFilter filter) {
        return filter.getMaxDistanceMeters() == null || distance == null || distance <= filter.getMaxDistanceMeters();
    }

    private boolean matchesOpenHours(ShopRecord shop, AiSearchFilter filter) {
        return filter.getOpenAfterHour() == null || isOpenAfterHour(shop.getOpenHours(), filter.getOpenAfterHour());
    }

    private boolean isOpenAfterHour(String openHours, Integer targetHour) {
        if (StrUtil.isBlank(openHours) || targetHour == null) {
            return true;
        }
        String[] sections = openHours.split(",");
        for (String section : sections) {
            String[] parts = section.trim().split("-");
            if (parts.length != 2) {
                continue;
            }
            int startHour = parseHour(parts[0]);
            int endHour = parseHour(parts[1]);
            if (endHour < startHour) {
                endHour += 24;
            }
            int checkHour = targetHour;
            if (checkHour < startHour) {
                checkHour += 24;
            }
            if (endHour >= checkHour) {
                return true;
            }
        }
        return false;
    }

    private int parseHour(String time) {
        String[] parts = time.trim().split(":");
        if (parts.length == 0) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[0].trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private boolean closesLate(ShopRecord shop) {
        return shop != null && isOpenAfterHour(shop.getOpenHours(), 22);
    }

    private boolean isType(ShopRecord shop, Long... typeIds) {
        if (shop == null || shop.getTypeId() == null) {
            return false;
        }
        for (Long typeId : typeIds) {
            if (shop.getTypeId().equals(typeId)) {
                return true;
            }
        }
        return false;
    }

    private Double resolveDistance(ShopRecord shop, Double x, Double y) {
        if (shop == null || shop.getX() == null || shop.getY() == null || x == null || y == null) {
            return null;
        }
        double earthRadius = 6371000D;
        double dLat = Math.toRadians(shop.getY() - y);
        double dLon = Math.toRadians(shop.getX() - x);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(y)) * Math.cos(Math.toRadians(shop.getY()))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadius * c;
    }

    private String buildBlogText(List<BlogRecord> blogs) {
        if (blogs == null || blogs.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (BlogRecord blog : blogs) {
            builder.append(StrUtil.blankToDefault(blog.getTitle(), "")).append(' ');
            builder.append(StrUtil.blankToDefault(blog.getContent(), "")).append(' ');
        }
        return builder.toString();
    }

    private boolean containsAny(String content, String... keywords) {
        for (String keyword : keywords) {
            if (content.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private List<String> deduplicate(List<String> source, int limit) {
        Set<String> unique = new LinkedHashSet<String>();
        if (source != null) {
            for (String item : source) {
                if (StrUtil.isNotBlank(item)) {
                    unique.add(item);
                }
                if (unique.size() >= limit) {
                    break;
                }
            }
        }
        return new ArrayList<String>(unique);
    }

    private int safePositive(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private double defaultDouble(Double value) {
        return value == null ? 0D : value;
    }

    private double displayScore(ShopRecord shop) {
        return shop == null || shop.getScore() == null ? 0D : shop.getScore() / 10.0D;
    }

    private String first(List<String> values) {
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private static class ScoredShop {
        private final ShopRecord shop;
        private final ShopTypeRecord type;
        private final List<String> tags;
        private final double score;
        private final String reason;

        private ScoredShop(ShopRecord shop, ShopTypeRecord type, List<String> tags, double score, String reason) {
            this.shop = shop;
            this.type = type;
            this.tags = tags;
            this.score = score;
            this.reason = reason;
        }
    }
}
