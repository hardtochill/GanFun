package com.sky.service;

import com.sky.result.Result;
import com.sky.vo.OrderReportVO;
import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;

import java.time.LocalDate;

public interface ReportService {
    /**
     * 营业额统计
     * @param beginDate
     * @param endDate
     * @return
     */
    TurnoverReportVO getturnoverStatistics(LocalDate beginDate, LocalDate endDate);

    /**
     * 用户数量统计
     * @param beginDate
     * @param endDate
     * @return
     */
    UserReportVO getuserStatistics(LocalDate beginDate, LocalDate endDate);

    /**
     * 订单统计
     * @param beginDate
     * @param beginDate
     * @return
     */
    OrderReportVO ordersStatistics(LocalDate beginDate, LocalDate endDate);
}
