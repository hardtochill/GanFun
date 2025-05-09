package com.sky.controller.admin;

import com.sky.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

@Api(tags = "店铺管理接口")
@RestController("adminShopController")//设置bean的名称，防止与用户端产生冲突
@RequestMapping("/admin/shop")
@Slf4j
public class ShopController {
    public static final String key = "SHOP_STATUS";
    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 获取店铺营业状态
     * @return
     */
    @GetMapping("/status")
    @ApiOperation("获取店铺营业状态")
    public Result<Integer> getStatus(){
        Integer status = (Integer) redisTemplate.opsForValue().get(key);
        log.info("获取店铺营业状态：{}",status==1?"营业中":"打样中");
        return Result.success(status);
    }

    /**
     * 设置店铺营业状态
     * @param status
     * @return
     */
    @PutMapping("/{status}")
    @ApiOperation("设置店铺营业状态")
    public Result setStatus(@PathVariable Integer status){
        log.info("设置店铺营业状态：{}",status==1?"营业中":"打样中");
        redisTemplate.opsForValue().set(key,status);
        return Result.success();
    }
}
