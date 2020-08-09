/**
 * Copyright 2010-2019 the original author or authors.
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
package org.mybatis.spring.mapper;

import static org.springframework.util.Assert.notNull;

import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.session.Configuration;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.support.SqlSessionDaoSupport;
import org.springframework.beans.factory.FactoryBean;

/**
 * 将@Mapper注解的.java 接口文件使用MapperFactoryBean包裹
 * 支持注入MyBatis映射器接口的BeanFactory。
 * 可以使用SqlSessionFactory 或 预配置的SqlSessionTemplate设置它。
 *
 * 嵌套关系：
 *    1、一个mapper接口对应一个MapperFactoryBean实例对象
 *          通过MapperFactoryBean.getObject()方法获取该接口的代理对象。
 *    2、每个MapperFactoryBean实例对象都是SqlSessionDaoSupport的子类，包含SqlSessionTemplate对象。
 *    3、SqlSessionTemplate对象中包含SqlSessionFactory对象。
 *    4、SqlSessionFactory对象由SqlSessionFactoryBean对象产生，并且在生成时，实例化了一个全局的Configuration对象
 *    5、Configuration对象中实例化了一个MapperRegistry对象，用来注册mapper对象
 *    6、MapperRegistry对象中实例化了一个Map对象，用来保存接口及其代理工厂实例：
 *          Map<Class<?>, MapperProxyFactory<?>> knownMappers
 *
 * 示例配置:
 * <pre class="code">
 *   <bean id="baseMapper" class="org.mybatis.spring.mapper.MapperFactoryBean" abstract="true" lazy-init="true">
 *     <property name="sqlSessionFactory" ref="sqlSessionFactory" />
 *   </bean>
 *
 *   <bean id="oneMapper" parent="baseMapper">
 *     <property name="mapperInterface" value="my.package.MyMapperInterface" />
 *   </bean>
 *
 *   <bean id="anotherMapper" parent="baseMapper">
 *     <property name="mapperInterface" value="my.package.MyAnotherMapperInterface" />
 *   </bean>
 * }
 * </pre>
 * 注意，该工厂只能注入接口，而不能注入具体的类。
 *
 * @author Eduardo Macarron
 *
 * @see SqlSessionTemplate
 */
public class MapperFactoryBean<T> extends SqlSessionDaoSupport implements FactoryBean<T> {

  private Class<T> mapperInterface;

  private boolean addToConfig = true;

  public MapperFactoryBean() {
    // intentionally empty
  }

  public MapperFactoryBean(Class<T> mapperInterface) {
    this.mapperInterface = mapperInterface;
  }

  /**
   * {@inheritDoc} 重写父类SqlSessionDaoSupport中的超父类DaoSupport中的checkDaoConfig()方法
   *        1、DaoSupport 实现了InitializingBean接口，
   *        2、在MapperFactoryBean实例化时会调用父类的afterPropertiesSet()方法
   *        3、在父类的afterPropertiesSet()方法中调用子类的checkDaoConfig()方法
   *        4、在该类的checkDaoConfig()中调用configuration.addMapper(this.mapperInterface)
   *        5、configuration实例化了一个MapperRegistry注册器，将接口类加入到knownMappers中
   */
  @Override
  protected void checkDaoConfig() {
    super.checkDaoConfig();

    notNull(this.mapperInterface, "Property 'mapperInterface' is required");

    Configuration configuration = getSqlSession().getConfiguration();
    if (this.addToConfig && !configuration.hasMapper(this.mapperInterface)) {
      try {
        configuration.addMapper(this.mapperInterface);
      } catch (Exception e) {
        logger.error("Error while adding the mapper '" + this.mapperInterface + "' to configuration.", e);
        throw new IllegalArgumentException(e);
      } finally {
        ErrorContext.instance().reset();
      }
    }
  }

  /**
   * {@inheritDoc} 实现FactoryBean接口的getObejct(),返回接口的代理对象
   */
  @Override
  public T getObject() throws Exception {
    /**
     * getSqlSession()：返回SqlSession对象
     */
    return getSqlSession().getMapper(this.mapperInterface);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Class<T> getObjectType() {
    return this.mapperInterface;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isSingleton() {
    return true;
  }

  // ------------- 调整器 --------------

  /**
   * 设置MyBatis映射器的映射器接口
   *
   * @param mapperInterface
   *          接口的类
   */
  public void setMapperInterface(Class<T> mapperInterface) {
    this.mapperInterface = mapperInterface;
  }

  /**
   * 返回MyBatis映射器的映射器接口
   *
   * @return 接口的类
   */
  public Class<T> getMapperInterface() {
    return mapperInterface;
  }

  /**
   * 如果addToConfig为false，映射器将不会被添加到MyBatis。
   * 这意味着它必须包含在mybatis-config.xml中。
   * 如果这是真的，mapper将被添加到MyBatis，如果它还没有被注册。
   * 默认情况下，addToConfig为true。
   *
   * @param addToConfig
   *         一个是否将mapper添加到MyBatis的标志
   */
  public void setAddToConfig(boolean addToConfig) {
    this.addToConfig = addToConfig;
  }

  /**
   * 返回添加到MyBatis配置中的标志。
   *
   * @return 如果mapper未被注册，将被添加到MyBatis，为真。
   */
  public boolean isAddToConfig() {
    return addToConfig;
  }
}
