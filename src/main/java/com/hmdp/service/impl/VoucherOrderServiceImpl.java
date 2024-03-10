package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import java.time.LocalDateTime;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

  @Autowired
  private RedisIdWorker redisIdWorker;

  @Autowired
  private ISeckillVoucherService seckillVoucherService;

  @Autowired
  private StringRedisTemplate stringRedisTemplate;

  @Override
  public Result seckillVoucher(Long voucherId) {
    LambdaQueryWrapper<SeckillVoucher> queryWrapper = new LambdaQueryWrapper<>();
    //1. 查询优惠券
    queryWrapper.eq(SeckillVoucher::getVoucherId, voucherId);
    SeckillVoucher seckillVoucher = seckillVoucherService.getOne(queryWrapper);
    //2. 判断秒杀时间是否开始
    if (LocalDateTime.now().isBefore(seckillVoucher.getBeginTime())) {
      return Result.fail("秒杀还未开始，请耐心等待");
    }
    //3. 判断秒杀时间是否结束
    if (LocalDateTime.now().isAfter(seckillVoucher.getEndTime())) {
      return Result.fail("秒杀已经结束！");
    }
    //4. 判断库存是否充足
    if (seckillVoucher.getStock() < 1) {
      return Result.fail("优惠券已被抢光了哦，下次记得手速快点");
    }
    Long userId = UserHolder.getUser().getId();
    //创建锁对象
    SimpleRedisLock redisLock = new SimpleRedisLock("order:" + userId,stringRedisTemplate);
    //获取锁对象
    boolean isLock = redisLock.tryLock(120);
    //加锁失败  存在多线程抢卷
    if(!isLock){
      return Result.fail("不允许抢多张优惠卷");
    }
 /*   synchronized (userId.toString().intern()) {
      IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
      return createVoucherOrder(voucherId);
    }*/
    try{
      //获取代理对象
      IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
      return proxy.createVoucherOrder(voucherId);
    } finally {
      //释放锁
      redisLock.unlock();
    }
  }

  @Transactional
  public Result createVoucherOrder(Long voucherId) {
    // 一人一单逻辑
    Long userId = UserHolder.getUser().getId();
    synchronized (userId.toString().intern()) {
      int count = query().eq("voucher_id", voucherId).eq("user_id", userId).count();
      if (count > 0) {
        return Result.fail("你已经抢过优惠券了哦");
      }
      //5. 扣减库存
      boolean success = seckillVoucherService.update()
          .setSql("stock = stock - 1")
          .eq("voucher_id", voucherId)
          .gt("stock", 0)
          .update();
      if (!success) {
        return Result.fail("库存不足");
      }
      //6. 创建订单
      VoucherOrder voucherOrder = new VoucherOrder();
      //6.1 设置订单id
      long orderId = redisIdWorker.nextId("order");
      //6.2 设置用户id
      Long id = UserHolder.getUser().getId();
      //6.3 设置代金券id
      voucherOrder.setVoucherId(voucherId);
      voucherOrder.setId(orderId);
      voucherOrder.setUserId(id);
      //7. 将订单数据保存到表中
      save(voucherOrder);
      //8. 返回订单id
      return Result.ok(orderId);
    }
    //执行到这里，锁已经被释放了，但是可能当前事务还未提交，如果此时有线程进来，不能确保事务不出问题
  }


}
