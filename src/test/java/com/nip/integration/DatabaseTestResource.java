package com.nip.integration;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

public class DatabaseTestResource implements QuarkusTestResourceLifecycleManager {

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(
        DockerImageName.parse("mysql:8.0")
    )
        .withDatabaseName("test_db")
        .withUsername("test")
        .withPassword("test");

    @Override
    public Map<String, String> start() {
        MYSQL.start();
        
        return Map.of(
            "quarkus.datasource.jdbc.url", MYSQL.getJdbcUrl(),
            "quarkus.datasource.username", MYSQL.getUsername(),
            "quarkus.datasource.password", MYSQL.getPassword(),
            "quarkus.hibernate-orm.database.generation", "drop-and-create"
        );
    }

    @Override
    public void stop() {
        if (MYSQL != null) {
            MYSQL.stop();
        }
    }
}
