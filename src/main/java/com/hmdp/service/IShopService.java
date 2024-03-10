package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

  Result queryById(Long id);

  Result update(Shop shop);


  Shop queryWithPassThrough(Long id);

  Shop queryWithMutex(Long id);

  void saveShop2Redis(Long id,Long expirSeconds);

  Shop queryWithLogicalExpire(Long id);


  Result queryShopByType(Integer typeId,Integer current,Double x, Double y);
}
