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
import org.springframework.core.env.Profiles;

import com.nestorria.server.common.datasource.DataSourceType;
import com.nestorria.server.common.datasource.DynamicDataSource;

import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class DataSourceConfig {

    @Bean
    public DataSource primaryDataSource(Environment env) {
        boolean isProd = env.acceptsProfiles(Profiles.of("prod"));

        String url = isProd
            ? env.resolveRequiredPlaceholders("${DB_URI}")
            : env.resolvePlaceholders("${DB_URI:${DB_URL:jdbc:postgresql://localhost:5432/nestorria}}");
        String username = isProd
            ? env.resolveRequiredPlaceholders("${DB_USERNAME}")
            : env.resolvePlaceholders("${DB_USERNAME:postgres}");
        String password = isProd
            ? env.resolveRequiredPlaceholders("${DB_PASSWORD}")
            : env.resolvePlaceholders("${DB_PASSWORD:postgres}");

        if (!isProd) {
            log.warn("Usando credenciales de desarrollo para DataSource. " +
                "En producción, configura DB_URI, DB_USERNAME y DB_PASSWORD.");
        }

        return DataSourceBuilder.create()
            .url(url)
            .username(username)
            .password(password)
            .build();
    }

    @Bean
    @ConditionalOnExpression("! '${app.db.replica-url:}'.isEmpty()")
    public DataSource replicaDataSource(Environment env) {
        String url = env.resolveRequiredPlaceholders("${app.db.replica-url}");
        String username = env.resolveRequiredPlaceholders("${app.db.replica-username}");
        String password = env.resolveRequiredPlaceholders("${app.db.replica-password}");

        if (url.isBlank() || username.isBlank() || password.isBlank()) {
            throw new IllegalArgumentException(
                "Replica datasource requiere app.db.replica-url, " +
                "app.db.replica-username y app.db.replica-password no vacíos");
        }

        return DataSourceBuilder.create()
            .url(url)
            .username(username)
            .password(password)
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
