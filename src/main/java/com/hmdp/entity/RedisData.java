package com.hmdp.entity;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class RedisData <T>{
  private LocalDateTime expireTime;
  private  T data;
}