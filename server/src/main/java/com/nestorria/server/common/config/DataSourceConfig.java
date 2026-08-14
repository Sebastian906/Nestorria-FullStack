package com.nestorria.server.common.config;

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

import com.nestorria.server.common.datasource.DataSourceType;
import com.nestorria.server.common.datasource.DynamicDataSource;

@Configuration
public class DataSourceConfig {

    @Bean
    public DataSource primaryDataSource(Environment env) {
        return DataSourceBuilder.create()
            .url(env.resolvePlaceholders("${DB_URI:${DB_URL:jdbc:postgresql://localhost:5432/nestorria}}"))
            .username(env.resolvePlaceholders("${DB_USERNAME:postgres}"))
            .password(env.resolvePlaceholders("${DB_PASSWORD:postgres}"))
            .build();
    }

    @Bean
    @ConditionalOnExpression("! '${app.db.replica-url:}'.isEmpty()")
    public DataSource replicaDataSource(Environment env) {
        return DataSourceBuilder.create()
            .url(env.resolvePlaceholders("${app.db.replica-url}"))
            .username(env.resolvePlaceholders("${app.db.replica-username}"))
            .password(env.resolvePlaceholders("${app.db.replica-password}"))
            .build();
    }

    @Bean
    @Primary
    @ConditionalOnExpression("! '${app.db.replica-url:}'.isEmpty()")
    public DataSource dynamicDataSource(
            @Qualifier("primaryDataSource") DataSource primary,
            @Qualifier("replicaDataSource") DataSource replica) {

        DynamicDataSource dynamic = new DynamicDataSource();

        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put(DataSourceType.PRIMARY, primary);
        targetDataSources.put(DataSourceType.REPLICA, replica);

        dynamic.setTargetDataSources(targetDataSources);
        dynamic.setDefaultTargetDataSource(primary);
        dynamic.afterPropertiesSet();

        return dynamic;
    }
}
