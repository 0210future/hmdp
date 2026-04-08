package com.hmdp.util;

import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.ai.AiSearchFilter;
import com.hmdp.model.ShopTypeRecord;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AiIntentParser {

    private static final Pattern PRICE_PATTERN = Pattern.compile("(?:人均|预算|价格|消费)?\\s*(\\d{2,4})\\s*(?:元)?\\s*(?:以内|以下|内)");
    private static final Pattern KM_PATTERN = Pattern.compile("(\\d{1,2})\\s*(?:公里|km)");
    private static final Pattern M_PATTERN = Pattern.compile("(\\d{2,5})\\s*(?:米|m)");
    private static final Pattern HOUR_DIGIT_PATTERN = Pattern.compile("(?:晚上|夜里|夜间|凌晨)?\\s*(\\d{1,2})\\s*点");
    private static final Map<String, String[]> TYPE_SYNONYMS = new LinkedHashMap<String, String[]>();

    static {
        TYPE_SYNONYMS.put("美食", new String[]{"美食", "火锅", "烧烤", "餐厅", "咖啡", "奶茶", "甜品", "夜宵", "吃饭", "小吃"});
        TYPE_SYNONYMS.put("KTV", new String[]{"ktv", "唱歌", "麦霸"});
        TYPE_SYNONYMS.put("丽人·美发", new String[]{"美发", "理发", "发型", "剪头"});
        TYPE_SYNONYMS.put("健身运动", new String[]{"健身", "运动", "瑜伽", "私教"});
        TYPE_SYNONYMS.put("按摩·足疗", new String[]{"按摩", "足疗", "推拿"});
        TYPE_SYNONYMS.put("美容SPA", new String[]{"spa", "美容", "护肤"});
        TYPE_SYNONYMS.put("亲子游乐", new String[]{"亲子", "遛娃", "儿童"});
        TYPE_SYNONYMS.put("酒吧", new String[]{"酒吧", "清吧", "夜生活", "喝酒"});
        TYPE_SYNONYMS.put("轰趴馆", new String[]{"轰趴", "派对", "团建"});
        TYPE_SYNONYMS.put("美睫·美甲", new String[]{"美甲", "美睫"});
    }

    private AiIntentParser() {
    }

    public static AiSearchFilter parse(String query, List<ShopTypeRecord> shopTypes) {
        String safeQuery = StrUtil.blankToDefault(query, "").trim();
        AiSearchFilter filter = new AiSearchFilter();
        filter.setRewrittenQuery(rewriteQuery(safeQuery));
        filter.setMaxAvgPrice(parsePrice(safeQuery));
        filter.setMaxDistanceMeters(parseDistance(safeQuery));
        filter.setOpenAfterHour(parseHour(safeQuery));
        filter.setScenes(parseScenes(safeQuery));

        ShopTypeRecord matchedType = matchType(safeQuery, shopTypes);
        if (matchedType != null) {
            filter.setTypeId(matchedType.getId());
            filter.setTypeName(matchedType.getName());
        }
        return filter;
    }

    private static String rewriteQuery(String query) {
        if (StrUtil.isBlank(query)) {
            return "附近热门店铺";
        }
        String rewritten = query.replace("帮我找", "").replace("推荐", "").replace("一下", "").replace("一家", "").replace("一些", "").trim();
        return StrUtil.blankToDefault(rewritten, query);
    }

    private static Integer parsePrice(String query) {
        Matcher matcher = PRICE_PATTERN.matcher(query);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    private static Integer parseDistance(String query) {
        Matcher kmMatcher = KM_PATTERN.matcher(query);
        if (kmMatcher.find()) {
            return Integer.valueOf(kmMatcher.group(1)) * 1000;
        }
        Matcher mMatcher = M_PATTERN.matcher(query);
        if (mMatcher.find()) {
            return Integer.valueOf(mMatcher.group(1));
        }
        if (query.contains("附近") || query.contains("周边")) {
            return 5000;
        }
        return null;
    }

    private static Integer parseHour(String query) {
        Matcher digitMatcher = HOUR_DIGIT_PATTERN.matcher(query);
        if (digitMatcher.find()) {
            int hour = Integer.parseInt(digitMatcher.group(1));
            if ((query.contains("晚上") || query.contains("夜里") || query.contains("夜间")) && hour < 12) {
                return hour + 12;
            }
            return hour;
        }
        if (query.contains("十一点")) {
            return 23;
        }
        if (query.contains("十点")) {
            return 22;
        }
        if (query.contains("夜宵") || query.contains("很晚") || query.contains("深夜")) {
            return 22;
        }
        return null;
    }

    private static List<String> parseScenes(String query) {
        List<String> scenes = new ArrayList<String>();
        if (query.contains("约会") || query.contains("情侣")) {
            scenes.add("适合约会");
        }
        if (query.contains("聚餐") || query.contains("团建") || query.contains("朋友")) {
            scenes.add("适合聚餐");
        }
        if (query.contains("夜宵") || query.contains("深夜")) {
            scenes.add("适合夜宵");
        }
        if (query.contains("拍照") || query.contains("出片") || query.contains("打卡")) {
            scenes.add("出片");
        }
        if (query.contains("性价比") || query.contains("平价") || query.contains("便宜")) {
            scenes.add("性价比高");
        }
        if (query.contains("辣")) {
            scenes.add("口味偏辣");
        }
        return scenes;
    }

    private static ShopTypeRecord matchType(String query, List<ShopTypeRecord> shopTypes) {
        if (shopTypes == null) {
            return null;
        }
        String lowerQuery = StrUtil.nullToEmpty(query).toLowerCase();
        for (ShopTypeRecord shopType : shopTypes) {
            if (shopType == null || StrUtil.isBlank(shopType.getName())) {
                continue;
            }
            if (lowerQuery.contains(shopType.getName().toLowerCase())) {
                return shopType;
            }
            String[] aliases = TYPE_SYNONYMS.get(shopType.getName());
            if (aliases == null) {
                continue;
            }
            for (String alias : aliases) {
                if (lowerQuery.contains(alias.toLowerCase())) {
                    return shopType;
                }
            }
        }
        return null;
    }
}
