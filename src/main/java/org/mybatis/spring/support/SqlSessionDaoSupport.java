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
package org.mybatis.spring.support;

import static org.springframework.util.Assert.notNull;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.dao.support.DaoSupport;

/**
 * MyBatis SqlSession数据访问对象的方便超类。
 * 它允许您访问模板，然后使用该模板执行SQL方法。
 * 这个类需要一个SqlSessionTemplate或SqlSessionFactory。
 * 如果两者都设置了，那么SqlSessionFactory将被忽略。
 *
 * @author Putthiphong Boonphong
 * @author Eduardo Macarron
 *
 * @see #setSqlSessionFactory
 * @see #setSqlSessionTemplate
 * @see SqlSessionTemplate
 */
public abstract class SqlSessionDaoSupport extends DaoSupport {

  private SqlSessionTemplate sqlSessionTemplate;

  /**
   * 将MyBatis SqlSessionFactory设置为由这个DAO使用。
   * 将为给定的SqlSessionFactory自动创建SqlSessionTemplate。
   *
   * @param sqlSessionFactory：SqlSession的工厂
   */
  public void setSqlSessionFactory(SqlSessionFactory sqlSessionFactory) {
    if (this.sqlSessionTemplate == null || sqlSessionFactory != this.sqlSessionTemplate.getSqlSessionFactory()) {
      this.sqlSessionTemplate = createSqlSessionTemplate(sqlSessionFactory);
    }
  }

  /**
   * 为给定的SqlSessionFactory创建一个SqlSessionTemplate。
   * 只有在使用SqlSessionFactory引用填充DAO时才调用!
   * 可以在子类中重写，以提供具有不同配置的SqlSessionTemplate实例，或自定义的SqlSessionTemplate子类。
   *
   * @param sqlSessionFactory
   *          MyBatis SqlSessionFactory用于创建SqlSessionTemplate
   * @return 新的SqlSessionTemplate实例
   * @see #setSqlSessionFactory
   */
  @SuppressWarnings("WeakerAccess")
  protected SqlSessionTemplate createSqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
    return new SqlSessionTemplate(sqlSessionFactory);
  }

  /**
   * 返回此DAO使用的MyBatis SqlSessionFactory。
   * @return SqlSession的工厂
   */
  public final SqlSessionFactory getSqlSessionFactory() {
    return (this.sqlSessionTemplate != null ? this.sqlSessionTemplate.getSqlSessionFactory() : null);
  }

  /**
   * 显式地为这个DAO设置SqlSessionTemplate，作为指定SqlSessionFactory的替代方法。
   *
   * @param sqlSessionTemplate：SqlSession的模板
   * @see #setSqlSessionFactory
   */
  public void setSqlSessionTemplate(SqlSessionTemplate sqlSessionTemplate) {
    this.sqlSessionTemplate = sqlSessionTemplate;
  }

  /**
   * 用户应该使用这个方法来获取一个SqlSession来调用它的语句方法，因为SqlSession是由spring管理的。
   * 用户不应该提交/回滚/关闭它，因为它将自动完成。
   *
   * @return 管理线程安全的SqlSession
   */
  public SqlSession getSqlSession() {
    return this.sqlSessionTemplate;
  }

  /**
   * 返回此DAO的SqlSessionTemplate，使用SessionFactory预初始化或显式设置。
   * <b>注意:返回的SqlSessionTemplate是一个共享实例。
   * 您可以内省它的配置，但不能修改配置(除了在{@link #initDao}实现中)。
   * 考虑通过{@code new SqlSessionTemplate(getSqlSessionFactory())}创建一个定制的 SqlSessionTemplate实例，
   * 在这种情况下，允许您定制结果实例上的设置。
   *
   * @return SqlSession的模板
   */
  public SqlSessionTemplate getSqlSessionTemplate() {
    return this.sqlSessionTemplate;
  }

  /**
   * {@inheritDoc} 检查sqlSessionTemplate属性不为空
   */
  @Override
  protected void checkDaoConfig() {
    notNull(this.sqlSessionTemplate, "Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required");
  }

}
