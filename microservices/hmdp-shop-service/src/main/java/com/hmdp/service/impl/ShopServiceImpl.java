package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void saveShop2Redis(Long id, Long expireSeconds) {
        Shop shop = getById(id);
        if (shop == null) {
            return;
        }
        stringRedisTemplate.opsForValue().set(
                CACHE_SHOP_KEY + id,
                JSONUtil.toJsonStr(shop),
                expireSeconds,
                TimeUnit.SECONDS
        );
    }
}
