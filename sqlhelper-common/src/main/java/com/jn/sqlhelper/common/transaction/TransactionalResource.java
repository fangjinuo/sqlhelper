/*
 * Copyright 2021 the original author or authors.
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

package com.jn.sqlhelper.common.transaction;

import com.jn.langx.Named;

import java.io.Closeable;
import java.io.IOException;
import java.sql.SQLException;

/**
 * JDBC Connection, MyBatis SqlSession, Hibernate Session 等等
 */
public interface TransactionalResource extends Named {

    void commit(boolean force) throws SQLException;

    void rollback() throws SQLException;

    boolean isClosed();

    void close() throws SQLException;
}
