/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at  http://www.gnu.org/licenses/lgpl-2.0.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jn.sqlhelper.mybatis.spring.boot.autoconfigure;

import com.jn.langx.util.Emptys;
import com.jn.langx.util.Preconditions;
import com.jn.langx.util.collection.Collects;
import com.jn.langx.util.function.Consumer;
import com.jn.langx.util.function.Predicate;
import com.jn.langx.util.reflect.Reflects;
import com.jn.sqlhelper.datasource.DataSourceRegistry;
import com.jn.sqlhelper.datasource.NamedDataSource;
import com.jn.sqlhelper.datasource.key.MethodInvocationDataSourceKeySelector;
import com.jn.sqlhelper.datasource.supports.spring.boot.DynamicDataSourcesAutoConfiguration;
import com.jn.sqlhelper.mybatis.spring.session.factory.dynamicdatasource.DelegatingSqlSessionFactory;
import com.jn.sqlhelper.mybatis.spring.session.factory.dynamicdatasource.DynamicSqlSessionFactory;
import com.jn.sqlhelper.mybatis.spring.session.factory.dynamicdatasource.DynamicSqlSessionTemplate;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.type.TypeHandler;
import org.mybatis.spring.MyBatisExceptionTranslator;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.mybatis.spring.boot.autoconfigure.MybatisProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ListFactoryBean;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.dao.support.PersistenceExceptionTranslator;

import javax.sql.DataSource;
import java.util.List;

@ConditionalOnProperty(name = "sqlhelper.dynamic-datasource.enabled", havingValue = "true", matchIfMissing = false)
@ConditionalOnClass({DynamicDataSourcesAutoConfiguration.class, SqlSessionFactory.class, SqlSessionFactoryBean.class, DynamicSqlSessionFactory.class})
@ConditionalOnBean(name = "dataSourcesFactoryBean")
@EnableConfigurationProperties(MybatisProperties.class)
@AutoConfigureBefore({MybatisAutoConfiguration.class})
@AutoConfigureAfter(DynamicDataSourcesAutoConfiguration.class)
@Configuration
public class DynamicSqlSessionTemplateAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(DynamicSqlSessionTemplateAutoConfiguration.class);

    @Bean("sqlSessionFactory")
    public DynamicSqlSessionFactory dynamicSqlSessionFactory(
            final ObjectProvider<DataSourceRegistry> registryProvider,
            @Qualifier("dataSourcesFactoryBean")
                    ListFactoryBean dataSourcesFactoryBean,
            final MybatisProperties properties,
            final ObjectProvider<Interceptor[]> interceptorsProvider,
            final ObjectProvider<TypeHandler[]> typeHandlerProvider,
            final ObjectProvider<LanguageDriver[]> languageDriverProvider,
            final ResourceLoader resourceLoader,
            final ObjectProvider<DatabaseIdProvider> databaseIdProvider,
            final ObjectProvider<List<ConfigurationCustomizer>> configurationCustomizersProvider) throws BeanCreationException {
        List<DataSource> dataSources = null;
        try {
            List ds = dataSourcesFactoryBean.getObject();
            dataSources = (List<DataSource>) ds;
        } catch (Throwable ex) {
            logger.error(ex.getMessage(), ex);
        }
        if (Emptys.isNotEmpty(dataSources)) {
            try {
                registryProvider.getObject();
            } catch (BeansException ex) {
                logger.error("Please check whether the sqlhelper-datasource.jar in the classpath or not");
                throw ex;
            }

            List<ConfigurationCustomizer> customizers = configurationCustomizersProvider.getIfAvailable();
            final ConfigurationCustomizer transactionFactoryCustomizer = Collects.findFirst(customizers, new Predicate<ConfigurationCustomizer>() {
                @Override
                public boolean test(ConfigurationCustomizer customizer) {
                    return customizer instanceof DynamicDataSourceTransactionFactoryCustomizer;
                }
            });

            final DynamicSqlSessionFactory dynamicSqlSessionFactory = new DynamicSqlSessionFactory();
            Collects.forEach(dataSources, new Consumer<DataSource>() {
                @Override
                public void accept(DataSource dataSource) {
                    NamedDataSource namedDataSource = registryProvider.getObject().wrap(dataSource);
                    try {
                        logger.info("===[SQLHelper & MyBatis]=== Create mybatis SqlSessionFactory instance for datasource {}", namedDataSource.getDataSourceKey());
                        SqlSessionFactory delegate = createSqlSessionFactory(dataSource, properties, interceptorsProvider, typeHandlerProvider, languageDriverProvider, resourceLoader, databaseIdProvider, configurationCustomizersProvider);
                        if (delegate != null) {
                            if (transactionFactoryCustomizer != null) {
                                transactionFactoryCustomizer.customize(delegate.getConfiguration());
                            }

                            DelegatingSqlSessionFactory sqlSessionFactory = new DelegatingSqlSessionFactory();
                            sqlSessionFactory.setDelegate(delegate);
                            PersistenceExceptionTranslator translator = new MyBatisExceptionTranslator(delegate.getConfiguration().getEnvironment().getDataSource(), true);
                            sqlSessionFactory.setPersistenceExceptionTranslator(translator);
                            dynamicSqlSessionFactory.addSqlSessionFactory(namedDataSource.getDataSourceKey(), sqlSessionFactory);
                        }
                    } catch (Throwable ex) {
                        logger.error("Error occur when create SqlSessionFactory for datasource {}, error: {}", namedDataSource.getDataSourceKey(), ex.getMessage(), ex);
                    }
                }
            });
            return dynamicSqlSessionFactory;
        } else {
            throw new BeanCreationException("Can't find any jdbc datasource");
        }

    }

    private SqlSessionFactory createSqlSessionFactory(DataSource dataSource,
                                                      MybatisProperties properties,
                                                      ObjectProvider<Interceptor[]> interceptorsProvider,
                                                      ObjectProvider<TypeHandler[]> typeHandlerProvider,
                                                      ObjectProvider<LanguageDriver[]> languageDriverProvider,
                                                      ResourceLoader resourceLoader,
                                                      ObjectProvider<DatabaseIdProvider> databaseIdProviderObjectProvider,
                                                      ObjectProvider<List<ConfigurationCustomizer>> configurationCustomizersProvider) throws Exception {
        // mybatis-spring-boot-starter 在不同版本，构造器会有变化

        MybatisAutoConfiguration mybatisAutoConfiguration = null;
        // 1.3.0 ~ 2.0.1 （1.3.0 之前的版本没有去关注）
        // MybatisProperties properties,
        // ObjectProvider<Interceptor[]> interceptorsProvider,
        // ResourceLoader resourceLoader,
        // ObjectProvider<DatabaseIdProvider> databaseIdProvider,
        // ObjectProvider<List<ConfigurationCustomizer>> configurationCustomizersProvider

        mybatisAutoConfiguration = Reflects.newInstance(MybatisAutoConfiguration.class,
                new Class[]{MybatisProperties.class,
                        ObjectProvider.class,
                        ResourceLoader.class,
                        ObjectProvider.class,
                        ObjectProvider.class
                },
                new Object[]{
                        properties,
                        interceptorsProvider,
                        resourceLoader,
                        databaseIdProviderObjectProvider,
                        configurationCustomizersProvider
                });


        // 2.1.0 ~ 2.14
        // MybatisProperties properties,
        // ObjectProvider<Interceptor[]> interceptorsProvider,
        // ObjectProvider<TypeHandler[]> typeHandlersProvider,
        // ObjectProvider<LanguageDriver[]> languageDriversProvider,
        // ResourceLoader resourceLoader,
        // ObjectProvider<DatabaseIdProvider> databaseIdProvider,
        // ObjectProvider<List<ConfigurationCustomizer>> configurationCustomizersProvider
        if (mybatisAutoConfiguration == null) {
            mybatisAutoConfiguration = Reflects.newInstance(MybatisAutoConfiguration.class,
                    new Class[]{
                            MybatisProperties.class,
                            ObjectProvider.class,
                            ObjectProvider.class,
                            ObjectProvider.class,
                            ResourceLoader.class,
                            ObjectProvider.class,
                            ObjectProvider.class
                    },
                    new Object[]{
                            properties,
                            interceptorsProvider,
                            typeHandlerProvider,
                            languageDriverProvider,
                            resourceLoader,
                            databaseIdProviderObjectProvider,
                            configurationCustomizersProvider
                    });
        }

        Preconditions.checkNotNull(mybatisAutoConfiguration, "the mybatis autoconfiguration is null");
        mybatisAutoConfiguration.afterPropertiesSet();
        return mybatisAutoConfiguration.sqlSessionFactory(dataSource);
    }

    @Bean
    public SqlSessionTemplate sqlSessionTemplate(
            MybatisProperties mybatisProperties,
            SqlSessionFactory sessionFactory,
            MethodInvocationDataSourceKeySelector selector) {
        DynamicSqlSessionTemplate template = new DynamicSqlSessionTemplate(sessionFactory, mybatisProperties.getExecutorType());
        template.setSelector(selector);
        return template;
    }
}
