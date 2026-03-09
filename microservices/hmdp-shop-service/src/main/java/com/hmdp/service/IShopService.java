package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.entity.Shop;

public interface IShopService extends IService<Shop> {
    void saveShop2Redis(Long id, Long expireSeconds);
}
