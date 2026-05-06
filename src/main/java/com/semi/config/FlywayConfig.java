package com.semi.config;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.output.MigrateResult;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Slf4j
@Configuration
public class FlywayConfig {

    @Bean
    public Flyway flyway(DataSource dataSource) {
        log.info("[Flyway] migration start - classpath:db/migration");

        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                
                // [ ]TODO 배포시 4줄 주석처리 할 것, 개발용 임시 설정, 다른 작업자가 리페어등을 실행시 에러 방지.
                .baselineOnMigrate(true)  // 기존 DB가 있으면 baseline부터 시작
                .baselineVersion("0")    // 기준 버전을 낮게 설정
                .validateOnMigrate(false) // 체크섬 검사 끄기
                .outOfOrder(true)   // 순서 꼬임 허용
                .locations("classpath:db/migration")
                .load();

        try {
            MigrateResult result = flyway.migrate();
            log.info("[Flyway] migration complete - executed: {}, target version: {}",
                    result.migrationsExecuted, result.targetSchemaVersion);
        } catch (FlywayException e) {
            log.error("[Flyway] migration failed - stopping server.", e);
            throw e;
        }

        return flyway;
    }
}
