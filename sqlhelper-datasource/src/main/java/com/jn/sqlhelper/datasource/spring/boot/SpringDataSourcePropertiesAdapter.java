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

package com.jn.sqlhelper.datasource.spring.boot;


import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;

public class SpringDataSourcePropertiesAdapter {
    public static com.jn.sqlhelper.datasource.definition.DataSourceProperties adapt(DataSourceProperties properties) {
        com.jn.sqlhelper.datasource.definition.DataSourceProperties dataSourceProperties = new com.jn.sqlhelper.datasource.definition.DataSourceProperties();
        dataSourceProperties.setUsername(properties.getUsername());
        dataSourceProperties.setPassword(properties.getPassword());
        dataSourceProperties.setDriverClassName(properties.getDriverClassName());
        dataSourceProperties.setName(properties.getName());
        dataSourceProperties.setUrl(properties.getUrl());
        return dataSourceProperties;
    }
}