package com.sky.service;

import com.sky.result.Result;
import com.sky.vo.TurnoverReportVO;

import java.time.LocalDate;

public interface ReportService {
    /**
     * 营业额统计
     * @param beginDate
     * @param endDate
     * @return
     */
    TurnoverReportVO getturnoverStatistics(LocalDate beginDate, LocalDate endDate);
}
