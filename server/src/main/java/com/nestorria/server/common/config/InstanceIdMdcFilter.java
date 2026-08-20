package com.nestorria.server.common.config;

import java.io.IOException;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InstanceIdMdcFilter extends OncePerRequestFilter {

    private final InstanceIdProvider instanceIdProvider;

    public InstanceIdMdcFilter(InstanceIdProvider instanceIdProvider) {
        this.instanceIdProvider = instanceIdProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        MDC.put("instanceId", instanceIdProvider.get());
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("instanceId");
        }
    }
}
