package com.neighbormarket.integration;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.sql.Connection;
import java.util.concurrent.atomic.AtomicInteger;

@Intercepts(@Signature(
        type = StatementHandler.class,
        method = "prepare",
        args = {Connection.class, Integer.class}
))
class SqlStatementCounter implements Interceptor {
    private final AtomicInteger preparedStatements = new AtomicInteger();

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        preparedStatements.incrementAndGet();
        return invocation.proceed();
    }

    int count() {
        return preparedStatements.get();
    }

    void reset() {
        preparedStatements.set(0);
    }

    @TestConfiguration
    static class Configuration {
        @Bean
        SqlStatementCounter sqlStatementCounter() {
            return new SqlStatementCounter();
        }
    }
}
