package com.sky.handler;

import com.sky.constant.MessageConstant;
import com.sky.exception.BaseException;
import com.sky.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLIntegrityConstraintViolationException;

/**
 * 全局异常处理器，处理项目中抛出的业务异常
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 捕获业务异常
     * @param ex
     * @return
     */
    @ExceptionHandler
    public Result exceptionHandler(BaseException ex){
        log.error("异常信息：{}", ex.getMessage());
        return Result.error(ex.getMessage());
    }
    /**
     * SQLIntegrityConstraintViolationException
     * 捕获新增员工异常异常
     */
    @ExceptionHandler
    public Result exceptionHandler(SQLIntegrityConstraintViolationException ex){
        String error = ex.getMessage();
        log.error("异常信息：{}",error);
        if(error.contains("Duplicate entry")){//用户名已存在
            //Duplicate entry '123456' for key 'employee.idx_username'
            //切分出用户名
            String[] strs = error.split(" ");
            String str = strs[2] + MessageConstant.ALREADY_EXISTED;
            return Result.error(str);
        }else{
            return Result.error(MessageConstant.UNKNOWN_ERROR);
        }
    }
}
