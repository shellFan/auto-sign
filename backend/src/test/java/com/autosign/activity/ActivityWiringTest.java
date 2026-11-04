package com.autosign.activity;

import com.autosign.AutoSignApplication;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.mapper.ClassPathMapperScanner;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.context.support.GenericApplicationContext;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ActivityWiringTest {
    @Test void applicationMapperScanActuallyRegistersTheNewQueueMapper() {
        MapperScan scan = AutoSignApplication.class.getAnnotation(MapperScan.class);
        Set<String> packages = new LinkedHashSet<>(Arrays.asList(scan.value()));
        packages.addAll(Arrays.asList(scan.basePackages()));
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:mapper-wiring;MODE=MySQL");
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration(
                new Environment("test", new SpringManagedTransactionFactory(), dataSource));
        SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(configuration);
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean("sqlSessionFactory", SqlSessionFactory.class, () -> factory);
            ClassPathMapperScanner scanner = new ClassPathMapperScanner(context);
            scanner.setAnnotationClass(org.apache.ibatis.annotations.Mapper.class);
            scanner.registerFilters();
            scanner.scan(packages.toArray(new String[0]));
            context.refresh();
            assertNotNull(context.getBean(ActivityMapper.class));
        }
    }
}
