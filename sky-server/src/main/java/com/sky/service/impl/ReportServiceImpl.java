package com.sky.service.impl;

import com.sky.dto.GoodsSalesDTO;
import com.sky.entity.Orders;
import com.sky.entity.User;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.result.Result;
import com.sky.service.ReportService;
import com.sky.service.WorkspaceService;
import com.sky.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ReportServiceImpl implements ReportService {
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WorkspaceService workspaceService;
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
    /**
     * 用户数量统计
     * @param beginDate
     * @param endDate
     * @return
     */
    public UserReportVO getuserStatistics(LocalDate beginDate, LocalDate endDate){
        //——1.获取日期
        //获取日期集合
        List<LocalDate> dateList = new ArrayList<>();
        while(!beginDate.equals(endDate)){
            dateList.add(beginDate);
            ///****再次注意：beginDate.plusDays(1)是返回一个在beginDate基础上加一天的LodalDate，beginDate本身并不会加一天****
            beginDate = beginDate.plusDays(1);
        }
        dateList.add(beginDate);
        //拼接成字符串
        String dateListStr = StringUtils.join(dateList,",");
        //——2.获取用户总量、新增用户
        //获取用户总量列表和新增用户列表
        List<Integer> totalUserList = new ArrayList<>();
        List<Integer> newUserList = new ArrayList<>();
        for (LocalDate localDate : dateList) {
            //设置每天条件日期
            LocalDateTime beginDateTime = LocalDateTime.of(localDate, LocalTime.MIN);
            LocalDateTime endDateTime = LocalDateTime.of(localDate, LocalTime.MAX);
            //根据集合动态查找user表，用户总量是统计截止到当前日期的用户数，新增用户量是统计在当天时间内创建的用户数
            Map map = new HashMap();
            //用于查找截止至当前日期的用户总数
            map.put("endDateTime",endDateTime);
            //聚合函数count()只会返回非null整型数，因此无需做为空判断
            Integer totalUserNum = userMapper.countByMap(map);
            totalUserList.add(totalUserNum);
            //用于查找新增用户数
            map.put("beginDateTime",beginDateTime);
            Integer newUserNum = userMapper.countByMap(map);
            newUserList.add(newUserNum);
        }
        //拼接成字符串
        String totalUserListStr = StringUtils.join(totalUserList,",");
        String newUserListStr = StringUtils.join(newUserList,",");
        return UserReportVO.builder()
                .dateList(dateListStr)
                .totalUserList(totalUserListStr)
                .newUserList(newUserListStr)
                .build();
    }
    /**
     * 订单统计
     * @param beginDate
     * @param beginDate
     * @return
     */
    public OrderReportVO ordersStatistics(LocalDate beginDate, LocalDate endDate){
        //——1.日期
        List<LocalDate> dateList = new ArrayList<>();
        while(!beginDate.equals(endDate)){
            dateList.add(beginDate);
            beginDate = beginDate.plusDays(1);
        }
        dateList.add(beginDate);
        String dateListStr = StringUtils.join(dateList, ",");
        List<Integer> orderCountList = new ArrayList<>();
        List<Integer> validOrderCountList = new ArrayList<>();
        //——2.每日订单数、每日有效订单数
        for (LocalDate localDate : dateList) {
            LocalDateTime beginDateTime = LocalDateTime.of(localDate, LocalTime.MIN);
            LocalDateTime endDateTime = LocalDateTime.of(localDate, LocalTime.MAX);
            //这里由于一次循环内要两次调用Mapper接口统计订单数量，因此把这个操作摘出去作为一个方法，降低代码的耦合度
            //每日订单数
            Integer orderCountNum = getOrderCount(beginDateTime,endDateTime,null);
            orderCountList.add(orderCountNum);
            //每日有效订单数
            Integer validOrderCountNum = getOrderCount(beginDateTime,endDateTime,Orders.COMPLETED);
            validOrderCountList.add(validOrderCountNum);
        }
        String orderCountListStr = StringUtils.join(orderCountList, ",");
        String valisOrderCountListStr = StringUtils.join(validOrderCountList, ",");
        //——3.订单总数、有效订单数
        //订单总数和有效订单数可以在循环里面顺便完成，但遵循一个循环只完成一件事情的原则，把这个操作摘出来
        //使用stream流完成
        Integer totalOrderCount = orderCountList.stream().reduce(Integer::sum).get();
        Integer validOrderCount = validOrderCountList.stream().reduce(Integer::sum).get();
        //订单完成率
        Double orderCompletionRate = 0.0;
        if(totalOrderCount!=0){
            orderCompletionRate = validOrderCount.doubleValue() / totalOrderCount;
        }
        return OrderReportVO.builder()
                .dateList(dateListStr)
                .orderCountList(orderCountListStr)
                .validOrderCountList(valisOrderCountListStr)
                .totalOrderCount(totalOrderCount)
                .validOrderCount(validOrderCount)
                .orderCompletionRate(orderCompletionRate)
                .build();
    }

    /**
     * 根据条件统计订单数量
     * @param beginDateTime
     * @param endDateTime
     * @param status
     * @return
     */
    public Integer getOrderCount(LocalDateTime beginDateTime,LocalDateTime endDateTime,Integer status){
        Map map = new HashMap();
        map.put("beginDateTime",beginDateTime);
        map.put("endDateTime",endDateTime);
        map.put("status",status);
        return orderMapper.countByMap(map);
    }
    /**
     * 查询销量top10
     * @param beginDate
     * @param endDate
     * @return
     */
    public SalesTop10ReportVO getSalesTop10(LocalDate beginDate, LocalDate endDate){
        //——1.日期
        LocalDateTime beginDateTime = LocalDateTime.of(beginDate, LocalTime.MIN);
        LocalDateTime endDateTime = LocalDateTime.of(endDate, LocalTime.MAX);
        //——2.获取GoodsSalesDTO集合
        List<GoodsSalesDTO> goodsSalesDTOList = orderMapper.getSalesTop10(beginDateTime,endDateTime);
        //——3.获取nameList：使用stream流
        List<String> nameList = goodsSalesDTOList.stream().map(GoodsSalesDTO::getName).collect(Collectors.toList());
        //——4.获取numberList：使用stream流
        List<Integer> numberList = goodsSalesDTOList.stream().map(GoodsSalesDTO::getNumber).collect(Collectors.toList());
        //——5.拼接字符串
        String nameListStr = StringUtils.join(nameList, ",");
        String numberListStr = StringUtils.join(numberList, ",");
        return SalesTop10ReportVO.builder()
                .nameList(nameListStr)
                .numberList(numberListStr)
                .build();
    }
    /**
     * 导出运营数据Excel报表
     * @param response
     */
    public void exportBusinessData(HttpServletResponse response){
        //——1.查询数据库获取数据
        //(1)设置时间，近30天：起始时间为当前日期-30，终止时间为当前日期-1(因为当天可能还未结束营业)
        LocalDate beginDate = LocalDate.now().minusDays(30);
        LocalDate endDate = LocalDate.now().minusDays(1);
        //(2)调用其他service的方法查询数据，在service层调用其他service也是一种常见做法
        BusinessDataVO businessData = workspaceService.getBusinessData(LocalDateTime.of(beginDate, LocalTime.MIN), LocalDateTime.of(endDate, LocalTime.MAX));
        //——2.将数据写入excel模板表中
        //(1)通过反射获取到模板文件的输出流
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("template/运营数据报表模板.xlsx");
        try {
            //(2)创建该模板文件在java中的excel对象并获取页对象
            XSSFWorkbook excel = new XSSFWorkbook(inputStream);
            XSSFSheet sheet = excel.getSheet("Sheet1");
            //(3)对应写入数据
            //——概览数据：近30天
            //第二行
            sheet.getRow(1).getCell(1).setCellValue("时间："+beginDate+" 至 "+endDate);
            //第四行
            XSSFRow row = sheet.getRow(3);
            row.getCell(2).setCellValue(businessData.getTurnover());
            row.getCell(4).setCellValue(businessData.getOrderCompletionRate());
            row.getCell(6).setCellValue(businessData.getNewUsers());
            //第五行
            row = sheet.getRow(4);
            row.getCell(2).setCellValue(businessData.getValidOrderCount());
            row.getCell(4).setCellValue(businessData.getUnitPrice());
            //——明细数据：每一天
            for(int i=0;i<30;i++){
                LocalDate date = beginDate.plusDays(i);
                businessData = workspaceService.getBusinessData(LocalDateTime.of(date,LocalTime.MIN),LocalDateTime.of(date, LocalTime.MAX));
                row = sheet.getRow(7+i);
                row.getCell(1).setCellValue(date.toString());
                row.getCell(2).setCellValue(businessData.getTurnover());
                row.getCell(3).setCellValue(businessData.getValidOrderCount());
                row.getCell(4).setCellValue(businessData.getOrderCompletionRate());
                row.getCell(5).setCellValue(businessData.getUnitPrice());
                row.getCell(6).setCellValue(businessData.getNewUsers());
            }

            //——3.将excel写入HttpServletResponse输出流中，发送给浏览器
            ServletOutputStream outputStream = response.getOutputStream();
            excel.write(outputStream);
            //——4.关闭资源
            excel.close();
            inputStream.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}
