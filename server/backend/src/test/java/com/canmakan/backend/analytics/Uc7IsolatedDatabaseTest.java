package com.canmakan.backend.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.test.context.ContextConfiguration;

@DisplayName("UC7: isolated database fail-closed guard")
class Uc7IsolatedDatabaseTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "canmakan", "CANMAKAN_UC7_TEST", "canmakan_uc7_test_backup"})
    @DisplayName("rejects every effective database name except the exact protected schema")
    void rejectsUnsafeDatabaseBeforeAnyInitializationSql(String effectiveDatabase) throws Exception {
        DataSource dataSource = org.mockito.Mockito.mock(DataSource.class);
        Connection connection = org.mockito.Mockito.mock(Connection.class);
        Statement statement = org.mockito.Mockito.mock(Statement.class);
        ResultSet resultSet = org.mockito.Mockito.mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT DATABASE()")).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString(1)).thenReturn(effectiveDatabase);

        assertThatThrownBy(() -> Uc7IsolatedDatabase.initialize(dataSource))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Refusing UC7 database initialization");

        verify(statement).executeQuery("SELECT DATABASE()");
        verify(statement, never()).execute(org.mockito.ArgumentMatchers.anyString());
        verify(statement, never()).executeUpdate(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("pins the JDBC URL and lifecycle hook to the protected database")
    void pinsDatabaseNameAndPreContextInitializerLifecycle() {
        assertThat(Uc7IsolatedDatabase.DATASOURCE_URL_PROPERTY)
                .contains("/canmakan_uc7_test?")
                .doesNotContain("${MYSQL_DB");
        assertThat(ApplicationContextInitializer.class)
                .isAssignableFrom(Uc7IsolatedDatabase.class);
        assertThat(Uc7IsolatedDatabase.DISABLE_AUTOMATIC_SQL_INIT_PROPERTY)
                .isEqualTo("spring.sql.init.mode=never");
        assertThat(Uc7IsolatedDatabase.DISABLE_HIBERNATE_DDL_PROPERTY)
                .isEqualTo("spring.jpa.hibernate.ddl-auto=none");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "com.canmakan.backend.analytics.ConsumerTrendsSeedContractTest",
        "com.canmakan.backend.analytics.repository.ScanAnalyticsRepositoryTest",
        "com.canmakan.backend.analytics.service.ConsumerTrendsServiceIntegrationTest",
        "com.canmakan.backend.admin.AdminConsumerTrendsRuntimeIntegrationTest"
    })
    @DisplayName("every MySQL-backed UC7 test installs the guard before context refresh")
    void everyMysqlTestRegistersProtectedInitialization(String testClassName) throws Exception {
        Class<?> testClass = Class.forName(testClassName);
        ContextConfiguration contextConfiguration =
                testClass.getAnnotation(ContextConfiguration.class);
        SpringBootTest springBootTest = testClass.getAnnotation(SpringBootTest.class);

        assertThat(contextConfiguration).isNotNull();
        assertThat(contextConfiguration.initializers()).contains(Uc7IsolatedDatabase.class);
        assertThat(springBootTest).isNotNull();
        assertThat(springBootTest.properties()).contains(
                Uc7IsolatedDatabase.DATASOURCE_URL_PROPERTY,
                Uc7IsolatedDatabase.DISABLE_AUTOMATIC_SQL_INIT_PROPERTY,
                Uc7IsolatedDatabase.DISABLE_HIBERNATE_DDL_PROPERTY
        );
    }
}
