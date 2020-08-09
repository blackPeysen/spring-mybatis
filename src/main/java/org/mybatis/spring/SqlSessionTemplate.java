/**
 * Copyright 2010-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.mybatis.spring;

import static java.lang.reflect.Proxy.newProxyInstance;

import static org.apache.ibatis.reflection.ExceptionUtil.unwrapThrowable;
import static org.mybatis.spring.SqlSessionUtils.closeSqlSession;
import static org.mybatis.spring.SqlSessionUtils.getSqlSession;
import static org.mybatis.spring.SqlSessionUtils.isSqlSessionTransactional;
import static org.springframework.util.Assert.notNull;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.executor.BatchResult;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.dao.support.PersistenceExceptionTranslator;

/**
 * 线程安全，Spring管理{@code SqlSession}，它与Spring事务管理一起工作，以确保实际使用的SqlSession是与当前Spring事务相关联的那个。
 * 此外，它管理会话生命周期，包括根据Spring事务*配置在必要时关闭、提交或回滚会话。
 * <p>
 * 模板需要一个SqlSessionFactory来创建SqlSessions，作为构造函数参数传递。
 * 它还可以被构造来指示要使用的执行器类型，如果没有，则将使用在session工厂中定义的默认执行器类型。
 * <p>
 * 默认情况下，该模板使用{@code MyBatisExceptionTranslator}将MyBatis PersistenceExceptions转换为未检查的DataAccessExceptions。
 * <p>
 * 因为SqlSessionTemplate是线程安全的，所以一个实例可以被所有dao共享;
 * 这样做还可以节省一些内存。这个模式可以在Spring配置文件中使用，如下所示:
 * <pre class="code">
 * {@code
 * <bean id="sqlSessionTemplate" class="org.mybatis.spring.SqlSessionTemplate">
 *   <constructor-arg ref="sqlSessionFactory" />
 * </bean>
 * }
 * </pre>
 *
 * @author Putthiphong Boonphong
 * @author Hunter Presnall
 * @author Eduardo Macarron
 *
 * @see SqlSessionFactory
 * @see MyBatisExceptionTranslator
 */
public class SqlSessionTemplate implements SqlSession, DisposableBean {

  private final SqlSessionFactory sqlSessionFactory;

  private final ExecutorType executorType;

  private final SqlSession sqlSessionProxy;

  private final PersistenceExceptionTranslator exceptionTranslator;

  /**
   * 使用提供的{@code SqlSessionFactory}作为参数构造一个Spring托管SqlSession。
   *
   * @param sqlSessionFactory：SqlSession的工厂
   */
  public SqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
    this(sqlSessionFactory, sqlSessionFactory.getConfiguration().getDefaultExecutorType());
  }

  /**
   * 使用{@code SqlSessionFactory}作为参数构造一个Spring托管的SqlSession，
   * 并且一旦构造了{@code SqlSessionTemplate}，就不能更改给定的{@code ExecutorType} {@code ExecutorType}。
   *
   * @param sqlSessionFactory：SqlSession的工厂
   * @param executorType：会话中的执行器类型
   */
  public SqlSessionTemplate(SqlSessionFactory sqlSessionFactory, ExecutorType executorType) {
    this(sqlSessionFactory, executorType,
        new MyBatisExceptionTranslator(sqlSessionFactory.getConfiguration().getEnvironment().getDataSource(), true));
  }

  /**
   * 使用给定的{@code SqlSessionFactory}和{@code ExecutorType}构造一个Spring托管的{@code SqlSession}。
   * 自定义{@code SQLExceptionTranslator}可以作为参数提供任何{@code PersistenceException} MyBatis抛出的可以
   * 自定义翻译{@code RuntimeException}, {@code SQLExceptionTranslator}也可以零,因此不例外翻译将和MyBatis将抛出异常
   *
   * @param sqlSessionFactory
   *          a factory of SqlSession
   * @param executorType
   *          an executor type on session
   * @param exceptionTranslator
   *          a translator of exception
   */
  public SqlSessionTemplate(SqlSessionFactory sqlSessionFactory, ExecutorType executorType,
      PersistenceExceptionTranslator exceptionTranslator) {

    notNull(sqlSessionFactory, "Property 'sqlSessionFactory' is required");
    notNull(executorType, "Property 'executorType' is required");

    this.sqlSessionFactory = sqlSessionFactory;
    this.executorType = executorType;
    this.exceptionTranslator = exceptionTranslator;
    this.sqlSessionProxy = (SqlSession) newProxyInstance(SqlSessionFactory.class.getClassLoader(),
        new Class[] { SqlSession.class }, new SqlSessionInterceptor());
  }

  public SqlSessionFactory getSqlSessionFactory() {
    return this.sqlSessionFactory;
  }

  public ExecutorType getExecutorType() {
    return this.executorType;
  }

  public PersistenceExceptionTranslator getPersistenceExceptionTranslator() {
    return this.exceptionTranslator;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <T> T selectOne(String statement) {
    return this.sqlSessionProxy.selectOne(statement);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <T> T selectOne(String statement, Object parameter) {
    return this.sqlSessionProxy.selectOne(statement, parameter);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <K, V> Map<K, V> selectMap(String statement, String mapKey) {
    return this.sqlSessionProxy.selectMap(statement, mapKey);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <K, V> Map<K, V> selectMap(String statement, Object parameter, String mapKey) {
    return this.sqlSessionProxy.selectMap(statement, parameter, mapKey);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <K, V> Map<K, V> selectMap(String statement, Object parameter, String mapKey, RowBounds rowBounds) {
    return this.sqlSessionProxy.selectMap(statement, parameter, mapKey, rowBounds);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <T> Cursor<T> selectCursor(String statement) {
    return this.sqlSessionProxy.selectCursor(statement);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <T> Cursor<T> selectCursor(String statement, Object parameter) {
    return this.sqlSessionProxy.selectCursor(statement, parameter);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <T> Cursor<T> selectCursor(String statement, Object parameter, RowBounds rowBounds) {
    return this.sqlSessionProxy.selectCursor(statement, parameter, rowBounds);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <E> List<E> selectList(String statement) {
    return this.sqlSessionProxy.selectList(statement);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <E> List<E> selectList(String statement, Object parameter) {
    return this.sqlSessionProxy.selectList(statement, parameter);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <E> List<E> selectList(String statement, Object parameter, RowBounds rowBounds) {
    return this.sqlSessionProxy.selectList(statement, parameter, rowBounds);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void select(String statement, ResultHandler handler) {
    this.sqlSessionProxy.select(statement, handler);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void select(String statement, Object parameter, ResultHandler handler) {
    this.sqlSessionProxy.select(statement, parameter, handler);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void select(String statement, Object parameter, RowBounds rowBounds, ResultHandler handler) {
    this.sqlSessionProxy.select(statement, parameter, rowBounds, handler);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int insert(String statement) {
    return this.sqlSessionProxy.insert(statement);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int insert(String statement, Object parameter) {
    return this.sqlSessionProxy.insert(statement, parameter);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int update(String statement) {
    return this.sqlSessionProxy.update(statement);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int update(String statement, Object parameter) {
    return this.sqlSessionProxy.update(statement, parameter);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int delete(String statement) {
    return this.sqlSessionProxy.delete(statement);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int delete(String statement, Object parameter) {
    return this.sqlSessionProxy.delete(statement, parameter);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <T> T getMapper(Class<T> type) {
    return getConfiguration().getMapper(type, this);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void commit() {
    throw new UnsupportedOperationException("Manual commit is not allowed over a Spring managed SqlSession");
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void commit(boolean force) {
    throw new UnsupportedOperationException("Manual commit is not allowed over a Spring managed SqlSession");
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void rollback() {
    throw new UnsupportedOperationException("Manual rollback is not allowed over a Spring managed SqlSession");
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void rollback(boolean force) {
    throw new UnsupportedOperationException("Manual rollback is not allowed over a Spring managed SqlSession");
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void close() {
    throw new UnsupportedOperationException("Manual close is not allowed over a Spring managed SqlSession");
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void clearCache() {
    this.sqlSessionProxy.clearCache();
  }

  /**
   * {@inheritDoc}
   *
   */
  @Override
  public Configuration getConfiguration() {
    return this.sqlSessionFactory.getConfiguration();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Connection getConnection() {
    return this.sqlSessionProxy.getConnection();
  }

  /**
   * {@inheritDoc}
   *
   * @since 1.0.2
   *
   */
  @Override
  public List<BatchResult> flushStatements() {
    return this.sqlSessionProxy.flushStatements();
  }

  /**
   * Allow gently dispose bean:
   *
   * <pre>
   * {@code
   *
   * <bean id="sqlSession" class="org.mybatis.spring.SqlSessionTemplate">
   *  <constructor-arg index="0" ref="sqlSessionFactory" />
   * </bean>
   * }
   * </pre>
   *
   * The implementation of {@link DisposableBean} forces spring context to use {@link DisposableBean#destroy()} method
   * instead of {@link SqlSessionTemplate#close()} to shutdown gently.
   *
   * @see SqlSessionTemplate#close()
   * @see "org.springframework.beans.factory.support.DisposableBeanAdapter#inferDestroyMethodIfNecessary(Object, RootBeanDefinition)"
   * @see "org.springframework.beans.factory.support.DisposableBeanAdapter#CLOSE_METHOD_NAME"
   */
  @Override
  public void destroy() throws Exception {
    // This method forces spring disposer to avoid call of SqlSessionTemplate.close() which gives
    // UnsupportedOperationException
  }

  /**
   * Proxy needed to route MyBatis method calls to the proper SqlSession got from Spring's Transaction Manager It also
   * unwraps exceptions thrown by {@code Method#invoke(Object, Object...)} to pass a {@code PersistenceException} to the
   * {@code PersistenceExceptionTranslator}.
   */
  private class SqlSessionInterceptor implements InvocationHandler {
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      SqlSession sqlSession = getSqlSession(SqlSessionTemplate.this.sqlSessionFactory,
          SqlSessionTemplate.this.executorType, SqlSessionTemplate.this.exceptionTranslator);
      try {
        Object result = method.invoke(sqlSession, args);
        if (!isSqlSessionTransactional(sqlSession, SqlSessionTemplate.this.sqlSessionFactory)) {
          // force commit even on non-dirty sessions because some databases require
          // a commit/rollback before calling close()
          sqlSession.commit(true);
        }
        return result;
      } catch (Throwable t) {
        Throwable unwrapped = unwrapThrowable(t);
        if (SqlSessionTemplate.this.exceptionTranslator != null && unwrapped instanceof PersistenceException) {
          // release the connection to avoid a deadlock if the translator is no loaded. See issue #22
          closeSqlSession(sqlSession, SqlSessionTemplate.this.sqlSessionFactory);
          sqlSession = null;
          Throwable translated = SqlSessionTemplate.this.exceptionTranslator
              .translateExceptionIfPossible((PersistenceException) unwrapped);
          if (translated != null) {
            unwrapped = translated;
          }
        }
        throw unwrapped;
      } finally {
        if (sqlSession != null) {
          closeSqlSession(sqlSession, SqlSessionTemplate.this.sqlSessionFactory);
        }
      }
    }
  }

}
