package com.hmdp.controller;


import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.SystemConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/shop")
public class ShopController {

    @Resource
    public IShopService shopService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 根据商铺id查询商铺信息--缓存穿透--互斥锁解决
     * @param id 商铺id
     * @return 商铺信息
     */
    @GetMapping("/{id}")
    public Result queryShopByIdWithMutex(@PathVariable("id") Long id) {
        // 查询商铺详情先从缓存中获取
        String key = CACHE_SHOP_KEY +":"+ id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 判断商铺是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            // 存在，直接返回
            return Result.ok(JSONUtil.toBean(shopJson, Shop.class));
        }
        if(shopJson != null){
            return Result.fail("商铺信息不存在");
        }
        Shop shop = null;
        // 不存在，查询数据库
        try {
            // 加锁
            boolean isLock = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
            if (!isLock) {
                // 加锁失败，休眠并重试
                Thread.sleep(50);
                return queryShopByIdWithMutex(id);
            }
            shop = shopService.getById(id);
            // 判断商铺是否存在
            if (shop == null) {
                //將空值写入redis
                stringRedisTemplate.opsForValue().set(key, "", 30, TimeUnit.MINUTES);
                // 不存在，返回错误信息
                return Result.fail("商铺不存在！");
            }
            // 存在，写入缓存
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), 30, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
            finally {
                // 释放锁
                stringRedisTemplate.delete(key);
            }

        // 返回数据
        return Result.ok(shop);
    }
  /*  @GetMapping("/{id}")
    @Cacheable(value = "shopCache", key = "#id", unless = "#result == null")
    public Result queryShopById(@PathVariable("id") Long id) {
        // 查询数据库
        Shop shop = shopService.getById(id);
        // 判断商铺是否存在
        if (shop == null) {
            // 不存在，返回错误信息
            return Result.fail("商铺不存在！");
        }
        // 返回数据
        return Result.ok(shop);
    }*/


    /**
     * 新增商铺信息
     * @param shop 商铺数据
     * @return 商铺id
     */
    @PostMapping
    public Result saveShop(@RequestBody Shop shop) {
        // 写入数据库
        shopService.save(shop);
        // 返回店铺id
        return Result.ok(shop.getId());
    }

    /**
     * 更新商铺信息
     * @param shop 商铺数据
     * @return 无
     */
    @Transactional
    @PutMapping
    public Result updateShop(@RequestBody Shop shop) {
        // 先判断商铺是否存在
        if (shop.getId() == null) {
            return Result.fail("商铺id不能为空！");
        }
        // 写入数据库
        shopService.updateById(shop);
        // 删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }

    /**
     * 根据商铺类型分页查询商铺信息
     * @param typeId 商铺类型
     * @param current 页码
     * @return 商铺列表
     */
    @GetMapping("/of/type")
    public Result queryShopByType(
            @RequestParam("typeId") Integer typeId,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        // 根据类型分页查询
        Page<Shop> page = shopService.query()
                .eq("type_id", typeId)
                .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
        // 返回数据
        return Result.ok(page.getRecords());
    }

    /**
     * 根据商铺名称关键字分页查询商铺信息
     * @param name 商铺名称关键字
     * @param current 页码
     * @return 商铺列表
     */
    @GetMapping("/of/name")
    public Result queryShopByName(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        // 根据类型分页查询
        Page<Shop> page = shopService.query()
                .like(StrUtil.isNotBlank(name), "name", name)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 返回数据
        return Result.ok(page.getRecords());
    }
}
