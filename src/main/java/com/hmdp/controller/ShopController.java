package com.hmdp.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.SystemConstants;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

@RestController
@RequestMapping("/shop")
public class ShopController {

    @Resource
    public IShopService shopService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @GetMapping("/{id}")
    public Result queryShopByIdWithMutex(@PathVariable("id") Long id) {
        String key = CACHE_SHOP_KEY + id;
        String lockKey = LOCK_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        if (StrUtil.isNotBlank(shopJson)) {
            return Result.ok(JSONUtil.toBean(shopJson, Shop.class));
        }
        if (shopJson != null) {
            return Result.fail("Shop not found");
        }

        Shop shop;
        boolean isLock = false;
        try {
            isLock = Boolean.TRUE.equals(stringRedisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS));
            if (!isLock) {
                Thread.sleep(50);
                return queryShopByIdWithMutex(id);
            }

            shop = shopService.getById(id);
            if (shop == null) {
                stringRedisTemplate.opsForValue().set(key, "", 30, TimeUnit.MINUTES);
                return Result.fail("Shop not found");
            }
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), 30, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            if (isLock) {
                stringRedisTemplate.delete(lockKey);
            }
        }

        return Result.ok(shop);
    }

    @PostMapping
    public Result saveShop(@RequestBody Shop shop) {
        shopService.save(shop);
        saveShopGeo(shop);
        return Result.ok(shop.getId());
    }

    @Transactional
    @PutMapping
    public Result updateShop(@RequestBody Shop shop) {
        if (shop.getId() == null) {
            return Result.fail("Shop id is required");
        }

        Shop oldShop = shopService.getById(shop.getId());
        if (oldShop == null) {
            return Result.fail("Shop not found");
        }

        shopService.updateById(shop);
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());

        if (oldShop.getTypeId() != null) {
            stringRedisTemplate.opsForGeo().remove(SHOP_GEO_KEY + oldShop.getTypeId(), oldShop.getId().toString());
        }
        Shop merged = mergeShopForGeo(oldShop, shop);
        saveShopGeo(merged);
        return Result.ok();
    }

    @GetMapping("/of/type")
    public Result queryShopByType(
            @RequestParam("typeId") Integer typeId,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "x", required = false) Double x,
            @RequestParam(value = "y", required = false) Double y
    ) {
        if (x == null || y == null) {
            Page<Shop> page = shopService.query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }

        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
        String key = SHOP_GEO_KEY + typeId;

        ensureGeoDataLoaded(typeId.longValue(), key);

        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().radius(
                key,
                new Circle(new Point(x, y), new Distance(5000)),
                RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                        .includeDistance()
                        .sortAscending()
                        .limit(end)
        );

        if (results == null) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (list.size() <= from) {
            return Result.ok(Collections.emptyList());
        }

        List<Long> ids = new ArrayList<>(list.size() - from);
        Map<String, Distance> distanceMap = new HashMap<>(list.size() - from);
        list.stream().skip(from).forEach(result -> {
            String shopId = result.getContent().getName();
            ids.add(Long.valueOf(shopId));
            distanceMap.put(shopId, result.getDistance());
        });

        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = shopService.query()
                .in("id", ids)
                .last("order by field(id," + idStr + ")")
                .list();

        for (Shop shop : shops) {
            Distance distance = distanceMap.get(shop.getId().toString());
            if (distance != null) {
                shop.setDistance(distance.getValue());
            }
        }
        return Result.ok(shops);
    }

    @GetMapping("/of/name")
    public Result queryShopByName(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        Page<Shop> page = shopService.query()
                .like(StrUtil.isNotBlank(name), "name", name)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        return Result.ok(page.getRecords());
    }

    private void ensureGeoDataLoaded(Long typeId, String key) {
        Long size = stringRedisTemplate.opsForZSet().size(key);
        if (size != null && size > 0) {
            return;
        }
        List<Shop> shops = shopService.query().eq("type_id", typeId).list();
        for (Shop shop : shops) {
            saveShopGeo(shop);
        }
    }

    private void saveShopGeo(Shop shop) {
        if (shop.getId() == null || shop.getTypeId() == null || shop.getX() == null || shop.getY() == null) {
            return;
        }
        String key = SHOP_GEO_KEY + shop.getTypeId();
        stringRedisTemplate.opsForGeo().add(key, new Point(shop.getX(), shop.getY()), shop.getId().toString());
    }

    private Shop mergeShopForGeo(Shop oldShop, Shop newShop) {
        Shop merged = new Shop();
        merged.setId(oldShop.getId());
        merged.setTypeId(newShop.getTypeId() != null ? newShop.getTypeId() : oldShop.getTypeId());
        merged.setX(newShop.getX() != null ? newShop.getX() : oldShop.getX());
        merged.setY(newShop.getY() != null ? newShop.getY() : oldShop.getY());
        return merged;
    }
}