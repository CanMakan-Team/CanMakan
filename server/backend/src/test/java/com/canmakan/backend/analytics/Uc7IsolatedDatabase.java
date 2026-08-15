package com.canmakan.backend.analytics;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/** Fail-closed initializer for the destructive UC7 MySQL integration-test schema. */
public final class Uc7IsolatedDatabase
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    public static final String DATABASE_NAME = "canmakan_uc7_test";
    public static final String DATASOURCE_URL_PROPERTY =
            "spring.datasource.url=jdbc:mysql://${MYSQL_HOST:localhost}:${MYSQL_PORT:3306}/"
                    + DATABASE_NAME
                    + "?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true"
                    + "&serverTimezone=UTC&allowMultiQueries=true";
    public static final String DISABLE_AUTOMATIC_SQL_INIT_PROPERTY = "spring.sql.init.mode=never";
    public static final String DISABLE_HIBERNATE_DDL_PROPERTY = "spring.jpa.hibernate.ddl-auto=none";

    private static final List<String> SCRIPTS = List.of(
            "00_schema.sql",
            "01_products.sql",
            "01c_recommendation_substitute_tags.sql",
            "01e_fish_sauce_product_updates.sql",
            "08_popularity_tags.sql",
            "02_ingredients.sql",
            "03_product_ingredients.sql",
            "04_roles_users.sql",
            "05_household_dietary_data.sql",
            "02b_ingredient_restrictions.sql",
            "06_scans_and_ai_logs.sql",
            "10_recommendation_logs.sql",
            "07_subscriptions_usage.sql"
    );

    public Uc7IsolatedDatabase() {
    }

    /** Runs before application beans, including any startup bean that reads seeded tables. */
    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        Environment environment = applicationContext.getEnvironment();
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(environment.getProperty(
                "spring.datasource.driver-class-name",
                "com.mysql.cj.jdbc.Driver"
        ));
        dataSource.setUrl(environment.getRequiredProperty("spring.datasource.url"));
        dataSource.setUsername(environment.getProperty("spring.datasource.username", "root"));
        dataSource.setPassword(environment.getProperty("spring.datasource.password", ""));
        initialize(dataSource);
    }

    /** Verifies the live JDBC target before any destructive SQL, then initializes test data. */
    public static void initialize(DataSource dataSource) {
        String actualDatabase = currentDatabase(dataSource);
        if (!DATABASE_NAME.equals(actualDatabase)) {
            throw new IllegalStateException(
                    "Refusing UC7 database initialization outside " + DATABASE_NAME
                            + "; connected to " + actualDatabase
            );
        }

        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.setContinueOnError(false);
        populator.setSqlScriptEncoding("UTF-8");
        SCRIPTS.stream()
                .map(ClassPathResource::new)
                .forEach(populator::addScript);
        populator.execute(dataSource);
    }

    public static void assertConnectedToTestDatabase(DataSource dataSource) {
        String actualDatabase = currentDatabase(dataSource);
        if (!DATABASE_NAME.equals(actualDatabase)) {
            throw new IllegalStateException(
                    "UC7 integration test escaped its isolated database: " + actualDatabase
            );
        }
    }

    private static String currentDatabase(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT DATABASE()")) {
            if (!result.next()) {
                throw new IllegalStateException("MySQL did not report the connected database");
            }
            return result.getString(1);
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not verify the UC7 integration-test database", exception);
        }
    }
}
