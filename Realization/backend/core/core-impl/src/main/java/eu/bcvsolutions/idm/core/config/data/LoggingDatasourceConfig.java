package eu.bcvsolutions.idm.core.config.data;

import com.zaxxer.hikari.HikariDataSource;
import eu.bcvsolutions.idm.core.api.repository.ExtendedJpaRepositoryFactoryBean;
import eu.bcvsolutions.idm.core.audit.repository.IdmLoggingEventExceptionRepository;
import eu.bcvsolutions.idm.core.audit.repository.IdmLoggingEventPropertyRepository;
import eu.bcvsolutions.idm.core.audit.repository.IdmLoggingEventRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
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

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.util.Arrays;
import java.util.HashMap;
import java.util.stream.StreamSupport;

@Configuration
@EnableJpaRepositories(
        repositoryFactoryBeanClass = ExtendedJpaRepositoryFactoryBean.class,
        basePackages = {"eu.bcvsolutions.idm"},
        entityManagerFactoryRef = "loggingEntityManagerFactory",
        transactionManagerRef = "loggingTransactionManager",

        includeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                        classes = {
                                IdmLoggingEventRepository.class,
                                IdmLoggingEventExceptionRepository.class,
                                IdmLoggingEventPropertyRepository.class
                        }
                )
        })
@PropertySource({ "classpath:application.properties", "classpath:application-${spring.profiles.active}.properties"})
public class LoggingDatasourceConfig {

    @Bean
    @ConfigurationProperties(prefix = "spring.logging-datasource")
    public DataSourceProperties loggingDataSourceProperties() {
        return new DataSourceProperties();
    }


    @Bean("loggingDatasource")
    //@ConditionalOnClass(HikariDataSource.class)
    //@ConditionalOnProperty(name = "spring.datasource.type", havingValue = "com.zaxxer.hikari.HikariDataSource")
    //@ConfigurationProperties(prefix = "spring.logging-datasource")
    public DataSource loggingDatasource() {
        return loggingDataSourceProperties().initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean("loggingEntityManagerFactory")
    //@ConditionalOnClass(HikariDataSource.class)
    //@ConditionalOnProperty(name = "spring.datasource.type", havingValue = "com.zaxxer.hikari.HikariDataSource")
    LocalContainerEntityManagerFactoryBean loggingEntityManagerFactory(@Qualifier("loggingDatasource") DataSource datasource,
                                                                       Environment env) {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(datasource);
        em.setPackagesToScan("eu.bcvsolutions.idm");
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        em.setJpaVendorAdapter(vendorAdapter);
        HashMap<String, Object> properties = new HashMap<>();
        MutablePropertySources propSrcs = ((AbstractEnvironment) env).getPropertySources();
        StreamSupport.stream(propSrcs.spliterator(), false)
                .filter(ps -> ps instanceof EnumerablePropertySource)
                .map(ps -> ((EnumerablePropertySource<?>) ps).getPropertyNames())
                .flatMap(Arrays::stream)
                .filter(propName -> propName.startsWith("hibernate") || propName.startsWith("spring.jpa.properties"))
                .forEach(propName -> properties.put(
                        propName
                                .replace("spring.jpa.properties.", "")
                                //.replace("org.hibernate", "hibernate")
                        ,
                        env.getProperty(propName)));
        em.setJpaPropertyMap(properties);
        return em;

    }

    @Bean("loggingTransactionManager")
    public PlatformTransactionManager transactionManager(@Qualifier("loggingEntityManagerFactory") LocalContainerEntityManagerFactoryBean em) {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(em.getObject());
        return transactionManager;
    }

}
