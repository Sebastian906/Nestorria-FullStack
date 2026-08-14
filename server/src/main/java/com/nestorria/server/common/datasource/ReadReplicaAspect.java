package com.nestorria.server.common.datasource;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(-1) // Before @Transactional
public class ReadReplicaAspect {

    @Around("@annotation(com.nestorria.server.common.datasource.ReadFromReplica)")
    public Object routeToReplica(ProceedingJoinPoint joinPoint) throws Throwable {
        DataSourceContextHolder.set(DataSourceType.REPLICA);
        try {
            return joinPoint.proceed();
        } finally {
            DataSourceContextHolder.clear();
        }
    }
}
