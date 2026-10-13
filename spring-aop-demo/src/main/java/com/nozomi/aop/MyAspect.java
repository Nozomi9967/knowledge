package com.nozomi.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;


@Aspect
@Component
public class MyAspect {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    /*
    *  定义pointcut 切点
    * */
    @Pointcut(value = "execution(* com.nozomi.aop.controller.*.*(..))")
    public void pointCut(){}

    /*
    *  定义advice 通知
    * */
    @Around(value = "pointCut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable{
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getName();
        ObjectMapper m = new ObjectMapper();
        Object[] args = joinPoint.getArgs();
        logger.info("类{}调用方法{},参数:{}",className,methodName, m.writeValueAsString(args));
        Object result = joinPoint.proceed();
        logger.info("类{}调用方法{},参数:{},返回结果:{}",className,methodName, m.writeValueAsString(args),m.writeValueAsString(result));
        return result;
    }

}
