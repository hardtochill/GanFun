package com.sky.aspect;

import com.sky.annotation.AutoFill;
import com.sky.constant.AutoFillConstant;
import com.sky.context.BaseContext;
import com.sky.enumeration.OperationType;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

/**
 * 实现公共字段自动填充
 */
@Aspect
@Component
@Slf4j
public class AutoFillAspect {
    //切入点表达式,前者锁定包和类，后者锁定具体方法，缩小范围，提高效率
    @Pointcut("execution(* com.sky.mapper.*.*(..)) && @annotation(com.sky.annotation.AutoFill)")
    public void pointcut(){}

    @Before("pointcut()")
    public void autoFill(JoinPoint joinPoint) throws Exception {
        log.info("自动填充公共字段");
        //获取方法上的注解，判断是新增还是修改
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();//获取方法签名
        Method method = methodSignature.getMethod();//获取到方法对象
        AutoFill autoFill = method.getAnnotation(AutoFill.class);//获取方法上的指定注解
        OperationType operationType = autoFill.value();//获取数据库操作类型
        //获取方法形参对象，默认把封装实体类对象放在形参首位
        Object[] args = joinPoint.getArgs();
        if(args==null||args.length==0){return;}//防止为空
        Object obj = args[0];//获取到实体类对象
        //先获得要填充的值
        LocalDateTime localDateTime = LocalDateTime.now();
        Long id = BaseContext.getCurrentId();

        //对不同操作类型执行不同填充
        if(operationType==OperationType.INSERT){//新增
            //因为obj是Object类，没有对应的设置方法，因此还需通过反射获取到对于设置方法
            Method setcreateTime = obj.getClass().getDeclaredMethod(AutoFillConstant.SET_CREATE_TIME, LocalDateTime.class);
            Method setcreateUser = obj.getClass().getDeclaredMethod(AutoFillConstant.SET_CREATE_USER, Long.class);
            Method setupdateTime = obj.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_TIME, LocalDateTime.class);
            Method setupdateUser = obj.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_USER, Long.class);
            //再通过反射执行方法，填充公共字段
            setcreateTime.invoke(obj,localDateTime);
            setcreateUser.invoke(obj,id);
            setupdateTime.invoke(obj,localDateTime);
            setupdateUser.invoke(obj,id);
        }else{//修改
            Method setupdateTime = obj.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_TIME, LocalDateTime.class);
            Method setupdateUser = obj.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_USER, Long.class);
            setupdateTime.invoke(obj,localDateTime);
            setupdateUser.invoke(obj,id);
        }
    }

}
