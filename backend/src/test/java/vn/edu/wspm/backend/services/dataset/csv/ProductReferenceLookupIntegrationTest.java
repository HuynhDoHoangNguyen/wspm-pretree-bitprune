package vn.edu.wspm.backend.services.dataset.csv;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import vn.edu.wspm.backend.repositories.ProductRepository;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Real JPQL and Spring read-only lookup on disposable MySQL; never loads app/dev configuration. */
@SpringJUnitConfig(ProductReferenceLookupIntegrationTest.Config.class)
@Testcontainers(disabledWithoutDocker = true)
class ProductReferenceLookupIntegrationTest {
    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("wdm_business_test")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci");

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        }
        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan("vn.edu.wspm.backend.entities");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none"));
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }
        @Bean ProductRepository productRepository(EntityManagerFactory factory) {
            return new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(factory))
                    .getRepository(ProductRepository.class);
        }
        @Bean ProductReferenceLookup productReferenceLookup(ProductRepository repository) {
            return new RepositoryProductReferenceLookup(repository);
        }
    }

    @Autowired ProductRepository repository;
    @Autowired ProductReferenceLookup lookup;

    @BeforeAll
    static void createCatalogFromV2(@Autowired JdbcTemplate jdbc) throws Exception {
        String schema = new ClassPathResource("db/migration/V2__complete_laptop_combo_schema.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        var matcher = Pattern.compile("(?ms)^CREATE TABLE `(?:CATEGORY|BRAND|PRODUCT)` .*?;").matcher(schema);
        int definitions = 0;
        while (matcher.find()) {
            jdbc.execute(matcher.group());
            definitions++;
        }
        assertEquals(3, definitions);
        jdbc.update("INSERT INTO CATEGORY(category_id,name,slug) VALUES ('category','Test','test')");
        jdbc.update("""
                INSERT INTO PRODUCT(product_id,sku,name,slug,category_id,current_price,status)
                VALUES ('Product-A','sku-a','Current name','a','category',999.99,'DISCONTINUED'),
                       ('café','sku-b','Another name','b','category',999.99,'DRAFT')
                """);
    }

    @Test
    void removesActualCaseAndAccentInsensitiveMatches() {
        var candidates = repository.findReferenceCandidates(List.of("product-a", "cafe"));
        assertEquals(Set.of("Product-A", "café"), Set.copyOf(candidates));
        assertTrue(lookup.findExisting(Set.of("product-a", "cafe")).isEmpty());
    }

    @Test
    void allowsExistingProductsRegardlessOfCatalogStatusAndHistoricalValues() {
        var ids = lookup.findExisting(Set.of("Product-A", "café", "missing"));
        assertEquals(Set.of("Product-A", "café"), ids);
        var source = new ParsedCsvRecord(2, 2, 2, List.of("order", "1", "external-customer",
                "2026-11-01T00:00:00.000000Z", "COMPLETED", "Product-A", "OLD-SKU",
                "Historical name", "1", "100.00", "0.00", "100.00", "VND"));
        var result = new OrderCsvBusinessValidator().validate(source, ids);
        assertTrue(result.valid());
        assertEquals("Historical name", result.row().productNameSnapshot());
        assertEquals("100.00", result.row().lineTotal().toPlainString());
    }
}
