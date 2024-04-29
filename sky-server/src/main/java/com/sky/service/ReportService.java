package com.sky.service;

import com.sky.result.Result;
import com.sky.vo.OrderReportVO;
import com.sky.vo.SalesTop10ReportVO;
import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
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

    /**
     * 查询销量top10
     * @param beginDate
     * @param endDate
     * @return
     */
    SalesTop10ReportVO getSalesTop10(LocalDate beginDate, LocalDate endDate);

    /**
     * 导出运营数据Excel报表
     * @param response
     */
    void exportBusinessData(HttpServletResponse response) throws IOException;
}
