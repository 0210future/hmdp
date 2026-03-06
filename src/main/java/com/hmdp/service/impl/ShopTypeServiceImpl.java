package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    private static final String SHOP_TYPE_KEY = "cache:shop:type:list";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        String cache = stringRedisTemplate.opsForValue().get(SHOP_TYPE_KEY);
        if (cache != null && !cache.isEmpty()) {
            List<ShopType> cached = JSONUtil.toList(cache, ShopType.class);
            return Result.ok(cached);
        }

        List<ShopType> typeList = query().orderByAsc("sort").list();
        if (typeList == null || typeList.isEmpty()) {
            return Result.ok(typeList);
        }

        stringRedisTemplate.opsForValue().set(SHOP_TYPE_KEY, JSONUtil.toJsonStr(typeList), 30, TimeUnit.MINUTES);
        return Result.ok(typeList);
    }
}