package eu.bcvsolutions.idm.core.config.data;

import com.zaxxer.hikari.HikariDataSource;
import eu.bcvsolutions.idm.core.api.repository.ExtendedJpaRepositoryFactoryBean;
import eu.bcvsolutions.idm.core.audit.repository.IdmLoggingEventExceptionRepository;
import eu.bcvsolutions.idm.core.audit.repository.IdmLoggingEventPropertyRepository;
import eu.bcvsolutions.idm.core.audit.repository.IdmLoggingEventRepository;
import org.apache.commons.beanutils.PropertyUtils;
import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.ImplicitNamingStrategyJpaCompliantImpl;
import org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.boot.orm.jpa.hibernate.SpringImplicitNamingStrategy;
import org.springframework.boot.orm.jpa.hibernate.SpringPhysicalNamingStrategy;
import org.springframework.context.annotation.*;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.util.StringUtils;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceContext;
import javax.sql.DataSource;
import java.util.Arrays;
import java.util.HashMap;
import java.util.stream.StreamSupport;

@Configuration
@EnableJpaRepositories(
        repositoryFactoryBeanClass = ExtendedJpaRepositoryFactoryBean.class,
        entityManagerFactoryRef = "entityManager",
        transactionManagerRef = "transactionManager",
        basePackages = {"eu.bcvsolutions.idm"},
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                        classes = {
                                IdmLoggingEventRepository.class,
                                IdmLoggingEventExceptionRepository.class,
                                IdmLoggingEventPropertyRepository.class
                        }
                )
        })
@PropertySource({ "classpath:application.properties", "classpath:application-${spring.profiles.active}.properties"})
@EnableTransactionManagement
public class DatasourceConfig {

    @Bean
    @ConfigurationProperties(prefix = "spring.datasource")
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    //@ConditionalOnClass(HikariDataSource.class)
    //@ConditionalOnProperty(name = "spring.datasource.type", havingValue = "com.zaxxer.hikari.HikariDataSource")
    //@ConfigurationProperties(prefix = "spring.datasource")
    public DataSource dataSource() {
        return dataSourceProperties().initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean("entityManager")
    @Primary
        //@ConditionalOnClass(HikariDataSource.class)
        //@ConditionalOnProperty(name = "spring.datasource.type", havingValue = "com.zaxxer.hikari.HikariDataSource")
    LocalContainerEntityManagerFactoryBean entityManager(@Qualifier("dataSource") DataSource datasource,
                                                                       Environment env) {

        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(datasource);
        em.setPackagesToScan("eu.bcvsolutions.idm");
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        em.setJpaVendorAdapter(vendorAdapter);
        HashMap<String, Object> properties = new HashMap<>();

        // fill jpa and hibernate properties
        MutablePropertySources propSrcs = ((AbstractEnvironment) env).getPropertySources();
        StreamSupport.stream(propSrcs.spliterator(), false)
                .filter(ps -> ps instanceof EnumerablePropertySource)
                .map(ps -> ((EnumerablePropertySource<?>) ps).getPropertyNames())
                .flatMap(Arrays::stream)
                .filter(propName -> propName.startsWith("hibernate") || propName.startsWith("spring.jpa.properties"))
                .forEach(propName -> properties.put(
                        propName
                                .replace("spring.jpa.properties.", "")
                        ,
                        env.getProperty(propName)));

        em.setJpaPropertyMap(properties);
        return em;
    }

    @Bean("transactionManager")
    @Primary
    public PlatformTransactionManager transactionManager(@Qualifier("entityManager") LocalContainerEntityManagerFactoryBean em) {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(em.getObject());
        return transactionManager;
    }

    @Bean
    public SpringImplicitNamingStrategy implicitNamingStrategy() {
        return new SpringImplicitNamingStrategy();
    }

    @Bean
    public SpringPhysicalNamingStrategy physicalNamingStrategy() {
        return new SpringPhysicalNamingStrategy() {
            @Override
            public Identifier toPhysicalColumnName(Identifier name, JdbcEnvironment jdbcEnvironment) {
                return super.toPhysicalColumnName(name, jdbcEnvironment);
            }
        };
    }

}
