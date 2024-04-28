package com.sky.service.impl;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.result.Result;
import com.sky.service.ReportService;
import com.sky.vo.TurnoverReportVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ReportServiceImpl implements ReportService {
    @Autowired
    private OrderMapper orderMapper;
    /**
     * 营业额统计
     * @param beginDate
     * @param endDate
     * @return
     */
    public TurnoverReportVO getturnoverStatistics(LocalDate beginDate, LocalDate endDate){
        //——1.获取String dateList
        //创建集合用于存储从beginDate到endDate的每一天
        List<LocalDate> dateList = new ArrayList<>();
        //从beginDate开始，逐日递增存入集合
        while(!beginDate.equals(endDate)){//要用equals，不能用==
            dateList.add(beginDate);
            //注意plusDays()是通过返回值作用的，只执行beginDate.plusDays(1)并不会让beginDate加一天
            beginDate = beginDate.plusDays(1);
        }
        //把endDate也存入
        dateList.add(beginDate);
        //将dateList转成String字符串，每一天以逗号分隔
        String dateListStr = StringUtils.join(dateList, ",");
        //——2.获取String turnoverList
        //创建集合用于存储从beginDate到endDate的每一天的营业额
        List<Double> turnoverList = new ArrayList<>();
        //遍历范围内的每一天，统计每天的营业额
        for (LocalDate localDate : dateList) {
            //LocalDateTime.of()可以将LocalDate和LocalTime拼接成一个LocalDateTime，获得当天的最早时间和最晚时间
            LocalDateTime beginDateTime = LocalDateTime.of(localDate, LocalTime.MIN);
            LocalDateTime endDateTime = LocalDateTime.of(localDate, LocalTime.MAX);
            //将查找条件封装到一个map集合中，时间范围和订单状态(只有已完成的订单才统计)
            Map map = new HashMap();
            map.put("beginDateTime",beginDateTime);
            map.put("endDateTime",endDateTime);
            map.put("status", Orders.COMPLETED);
            //根据集合条件统计订单金额
            Double amount = orderMapper.sumByMap(map);
            //如果当天没有订单，则要将amount置为0，否则amount以null的形式加入统计
            amount = amount == null ? 0.0 : amount;
            turnoverList.add(amount);
        }
        //将turnoverList转成String字符串，每一天的金额以逗号分隔
        String turnoverListStr = StringUtils.join(turnoverList, ",");
        //链式构造视图对象返回
        return TurnoverReportVO.builder()
                .dateList(dateListStr)
                .turnoverList(turnoverListStr)
                .build();
    }
}
