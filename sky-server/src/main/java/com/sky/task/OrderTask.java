package com.sky.task;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

//订单定时任务类
@Component
@Slf4j
public class OrderTask {
    @Autowired
    private OrderMapper orderMapper;

    //处理超时未支付订单：支付时间15分钟内未支付。每分钟检查一次
    @Scheduled(cron = "0 * * * * ?")
    public void processTimeoutOrder(){
        log.info("定时处理超时订单：{}", LocalDateTime.now());
        //——1.取出超时订单：处于待付款状态，且下单时间<(现在时间-15分钟)
        //查询时间为当前时间-15分钟
        LocalDateTime time = LocalDateTime.now().plusMinutes(-15);
        List<Orders> ordersList = orderMapper.getByStatusAndOrderTimeLT(Orders.PENDING_PAYMENT, time);
        //——2.修改订单状态：已取消
        for (Orders orders : ordersList) {
            orders.setStatus(Orders.CANCELLED);
            orders.setCancelTime(LocalDateTime.now());
            orders.setCancelReason("订单超时，自动取消");
            orderMapper.update(orders);
        }
    }
    //处理派送超时订单：下单时间为上一个工作日且处于派送中的订单。每天的凌晨一点检查一次
    @Scheduled(cron = "0 0 1 * * ?")
    public void processDeliveryOrder(){
        log.info("定时处理处于派送中的订单：{}",LocalDateTime.now());
        //——1.取出处于派送中的订单，且下单时间为上一个工作日
        LocalDateTime time = LocalDateTime.now().plusHours(-1);//凌晨1点触发，减1个小时就是12点，也就是上一个工作日
        List<Orders> ordersList = orderMapper.getByStatusAndOrderTimeLT(Orders.DELIVERY_IN_PROGRESS, time);
        //——2.修改订单状态：已完成
        for (Orders orders : ordersList) {
            //造成派送超时原因是顾客未点击收到订单，因此修改状态为已完成
            orders.setStatus(Orders.COMPLETED);
            orderMapper.update(orders);
        }
    }
}
