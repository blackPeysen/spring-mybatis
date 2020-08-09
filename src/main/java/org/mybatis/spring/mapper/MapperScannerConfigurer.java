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
package org.mybatis.spring.mapper;

import static org.springframework.util.Assert.notNull;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Optional;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.PropertyResourceConfigurer;
import org.springframework.beans.factory.config.TypedStringValue;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * BeanDefinitionRegistryPostProcessor，从一个基本包开始递归搜索接口并将它们注册为{@code MapperFactoryBean}。
 * 注意，只有具有至少一个方法的接口才会被注册，具体类将被忽略。
 *
 * 这个类在1.0.1版本之前是{code BeanFactoryPostProcessor}。
 *    在1.0.2中改为{@code BeanDefinitionRegistryPostProcessor}。
 *    参见https://jira.springsource.org/browse/SPR-8269获得详细信息。
 *
 * 属性可以包含多个包名，用逗号或分号分隔。
 * 该类支持筛选通过指定标记接口或注释创建的映射器。
 *    属性指定要搜索的注释。
 *    属性指定要搜索的父接口。
 *    如果指定了这两个属性，则为匹配或条件的接口添加映射器。
 *    默认情况下，这两个属性是null，因此给定的{@code basePackage}中的所有接口都被添加为映射器。
 *
 * 这个配置器为它创建的所有bean启用自动装配，这样它们就可以通过正确的{@code SqlSessionFactory}或{@code SqlSessionTemplate}自动自动装配。
 *    但是，如果应用程序中有多个{@code SqlSessionFactory}，则不能使用自动装配。
 *    在这种情况下，必须通过bean名属性显式指定一个{@code SqlSessionFactory}或一个{@code SqlSessionTemplate}来使用。
 *    使用Bean名称而不是实际的对象，因为Spring在处理这个类之后才初始化属性占位符。
 *
 * 传入一个可能需要占位符(比如DB用户密码)的实际对象将会失败。
 *    使用bean名称将实际的对象创建延迟到启动过程的后期，在所有占位符替换完成之后。
 *    但是，注意，这个配置器支持其拥有属性的属性占位符。
 *    basePackage和bean名称属性都支持${property}样式替换。
 * Configuration sample:
 * <pre class="code">
 * {@code
 *   <bean class="org.mybatis.spring.mapper.MapperScannerConfigurer">
 *       <property name="basePackage" value="org.mybatis.spring.sample.mapper" />
 *       <!-- optional unless there are multiple session factories defined -->
 *       <property name="sqlSessionFactoryBeanName" value="sqlSessionFactory" />
 *   </bean>
 * }
 * </pre>
 *
 * @author Hunter Presnall
 * @author Eduardo Macarron
 *
 * @see MapperFactoryBean
 * @see ClassPathMapperScanner
 */
public class MapperScannerConfigurer
    implements BeanDefinitionRegistryPostProcessor, InitializingBean, ApplicationContextAware, BeanNameAware {

  // 搜索Mapper映射器的基本包
  private String basePackage;

  private boolean addToConfig = true;

  // 是否懒加载
  private String lazyInitialization;

  private SqlSessionFactory sqlSessionFactory;

  private SqlSessionTemplate sqlSessionTemplate;

  private String sqlSessionFactoryBeanName;

  private String sqlSessionTemplateBeanName;

  private Class<? extends Annotation> annotationClass;

  private Class<?> markerInterface;

  private Class<? extends MapperFactoryBean> mapperFactoryBeanClass;

  // spring 上下文
  private ApplicationContext applicationContext;

  // 创建该bean的默认名称
  private String beanName;

  // 是否执行属性占位符处理
  private boolean processPropertyPlaceHolders;

  private BeanNameGenerator nameGenerator;

  private String defaultScope;

  /**
   * 此属性允许您为mapper接口文件设置基本包。
   * 您可以使用分号或逗号作为分隔符来设置多个包。
   * 从指定的包开始递归地搜索映射器。
   *
   * @param basePackage
   */
  public void setBasePackage(String basePackage) {
    this.basePackage = basePackage;
  }

  /**
   * 与{@code MapperFactoryBean#setAddToConfig(boolean)}相同。
   *
   * @param addToConfig 一个是否将mapper添加到MyBatis的标志
   * @see MapperFactoryBean#setAddToConfig(boolean)
   */
  public void setAddToConfig(boolean addToConfig) {
    this.addToConfig = addToConfig;
  }

  /**
   * 设置是否启用映射器bean的延迟初始化。
   * 默认值是{@code false}。
   *
   * @param lazyInitialization
   *          Set the @{code true} to enable
   * @since 2.0.2
   */
  public void setLazyInitialization(String lazyInitialization) {
    this.lazyInitialization = lazyInitialization;
  }

  /**
   * 此属性指定扫描程序将搜索的注释。
   * 扫描器将注册基包中同样具有指定注释的所有接口。
   * 注意，这可以与markerInterface结合使用。
   *
   * @param annotationClass 注解类
   */
  public void setAnnotationClass(Class<? extends Annotation> annotationClass) {
    this.annotationClass = annotationClass;
  }

  /**
   * 此属性指定扫描程序将搜索的父对象。
   * 扫描器将注册基包中同样将指定接口类作为父类的所有接口。
   * 注意，这可以与annotationClass结合使用。
   *
   * @param superClass 父类
   */
  public void setMarkerInterface(Class<?> superClass) {
    this.markerInterface = superClass;
  }

  /**
   * 指定在spring上下文中有多于一个的情况下使用哪个{@code SqlSessionTemplate}。
   * 通常只有当你有多个数据源时才需要。
   * @deprecated 使用{@link #setSqlSessionTemplateBeanName(String)}代替
   *
   * @param sqlSessionTemplate SqlSession的模板
   *
   */
  @Deprecated
  public void setSqlSessionTemplate(SqlSessionTemplate sqlSessionTemplate) {
    this.sqlSessionTemplate = sqlSessionTemplate;
  }

  /**
   * 指定在spring上下文中有多于一个的情况下使用哪个{@code SqlSessionTemplate}。
   * 通常只有当你有多个数据源时才需要。
   *
   * 注意，使用的是bean名称，而不是bean引用。这是因为扫描程序在启动进程的早期加载，现在构建mybatis对象实例还为时过早。
   * @since 1.1.0
   *
   * @param sqlSessionTemplateName
   *          {@code SqlSessionTemplate}的Bean名称
   */
  public void setSqlSessionTemplateBeanName(String sqlSessionTemplateName) {
    this.sqlSessionTemplateBeanName = sqlSessionTemplateName;
  }

  /**
   * 指定在spring上下文中有多于一个的情况下使用哪个{@code SqlSessionFactory}。
   * 通常只有当你有多个数据源时才需要。
   * <p>
   *
   * @deprecated 使用{@link #setSqlSessionFactoryBeanName(String)}代替。
   *
   * @param sqlSessionFactory
   *          SqlSession的工厂
   */
  @Deprecated
  public void setSqlSessionFactory(SqlSessionFactory sqlSessionFactory) {
    this.sqlSessionFactory = sqlSessionFactory;
  }

  /**
   * 指定在spring上下文中有多于一个的情况下使用哪个{@code SqlSessionFactory}。
   * 通常只有当你有多个数据源时才需要。
   *
   * 注意，使用的是bean名称，而不是bean引用。这是因为扫描程序在启动进程的早期加载，现在构建mybatis对象实例还为时过早。
   * @since 1.1.0
   *
   * @param sqlSessionFactoryName
   *          {@code SqlSessionFactory}的Bean名称
   */
  public void setSqlSessionFactoryBeanName(String sqlSessionFactoryName) {
    this.sqlSessionFactoryBeanName = sqlSessionFactoryName;
  }

  /**
   * 指定是否执行属性占位符处理的标志。
   * 默认值是{@literal false}。这意味着不执行属性占位符处理。
   * @since 1.1.1
   *
   * @param processPropertyPlaceHolders
   *         是否执行属性占位符处理的标志
   */
  public void setProcessPropertyPlaceHolders(boolean processPropertyPlaceHolders) {
    this.processPropertyPlaceHolders = processPropertyPlaceHolders;
  }

  /**
   * 返回mybatis代理作为spring bean的{@link MapperFactoryBean}的类。
   *
   * @param mapperFactoryBeanClass
   *          MapperFactoryBean的类
   * @since 2.0.1
   */
  public void setMapperFactoryBeanClass(Class<? extends MapperFactoryBean> mapperFactoryBeanClass) {
    this.mapperFactoryBeanClass = mapperFactoryBeanClass;
  }

  /**
   * {@inheritDoc} ApplicationContextAware 接口的实现方法
   */
  @Override
  public void setApplicationContext(ApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
  }

  /**
   * {@inheritDoc} BeanNameAware接口的实现方法
   */
  @Override
  public void setBeanName(String name) {
    this.beanName = name;
  }

  /**
   * 获取运行扫描器时使用的beanNameGenerator。
   *
   * @return 已配置的beanNameGenerator
   * @since 1.2.0
   */
  public BeanNameGenerator getNameGenerator() {
    return nameGenerator;
  }

  /**
   * 设置beanNameGenerator在运行扫描器时使用。
   *
   * @param nameGenerator
   *          the beanNameGenerator to set
   * @since 1.2.0
   */
  public void setNameGenerator(BeanNameGenerator nameGenerator) {
    this.nameGenerator = nameGenerator;
  }

  /**
   * 设置扫描的映射器的默认范围。
   * 默认为{@code null}(枚,单例).
   *
   * @param defaultScope：默认范围
   *
   * @since 2.0.6
   */
  public void setDefaultScope(String defaultScope) {
    this.defaultScope = defaultScope;
  }

  /**
   * {@inheritDoc} InitializingBean 接口的具体实现
   *  当所有属性配置后进行校验
   */
  @Override
  public void afterPropertiesSet() throws Exception {
    notNull(this.basePackage, "Property 'basePackage' is required");
  }

  /**
   * {@inheritDoc} BeanFactoryPostProcessor 接口的具体实现，用于修改内部的bean工厂
   *    在标准初始化之后修改应用程序上下文的内部bean工厂。
   *    所有bean定义都将被加载，但是还没有实例化bean。
   *    这允许覆盖或添加属性，甚至可以在快速初始化bean中。
   */
  @Override
  public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
    // left intentionally blank
  }

  /**
   * {@inheritDoc} BeanDefinitionRegistryPostProcessor 接口的具体实现，用于添加内部的bean定义
   *    在标准初始化之后修改应用程序上下文的内部bean定义注册表。
   *    所有常规bean定义都已被加载，但是还没有实例化bean。
   *    这允许在进入下一个后处理阶段之前添加更多的bean定义。
   * @since 1.0.2
   */
  @Override
  public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
    // 判断是否需要执行占位符处理
    if (this.processPropertyPlaceHolders) {
      processPropertyPlaceHolders();
    }

    /**
     * 声明一个ClassPathMapperScanner 包扫描器对象，用于扫描指定的basePackage属性下的文件。
     *  继承了Spring的ClassPathBeanDefinitionScanner
     */
    ClassPathMapperScanner scanner = new ClassPathMapperScanner(registry);

    /**
     * 根据该类的属性对scanner进行设置
     */
    scanner.setAddToConfig(this.addToConfig);
    scanner.setAnnotationClass(this.annotationClass);
    scanner.setMarkerInterface(this.markerInterface);
    /**
     * 注入数据库会话工厂sqlSessionFactory 或 sqlSessionTemplate
     */
    scanner.setSqlSessionFactory(this.sqlSessionFactory);
    scanner.setSqlSessionTemplate(this.sqlSessionTemplate);
    scanner.setSqlSessionFactoryBeanName(this.sqlSessionFactoryBeanName);
    scanner.setSqlSessionTemplateBeanName(this.sqlSessionTemplateBeanName);
    /**
     * 注入上下文环境applicationContext
     */
    scanner.setResourceLoader(this.applicationContext);
    /**
     * 注入beanName命名策略器
     */
    scanner.setBeanNameGenerator(this.nameGenerator);
    /**
     * 注入mapper对应的对象工厂
     */
    scanner.setMapperFactoryBeanClass(this.mapperFactoryBeanClass);
    // 是否懒加载
    if (StringUtils.hasText(lazyInitialization)) {
      scanner.setLazyInitialization(Boolean.valueOf(lazyInitialization));
    }
    // 对象的范围
    if (StringUtils.hasText(defaultScope)) {
      scanner.setDefaultScope(defaultScope);
    }

    scanner.registerFilters();
    // 对MapperScam注解的basePackage包进行扫描
    scanner.scan(
        StringUtils.tokenizeToStringArray(this.basePackage, ConfigurableApplicationContext.CONFIG_LOCATION_DELIMITERS));
  }

  /*
   * BeanDefinitionRegistries在应用程序启动的早期调用，在BeanFactoryPostProcessors之前。
   * 这意味着PropertyResourceConfigurers将不会被加载，并且该类属性的任何属性替换将失败。
   * 为了避免这种情况，找到上下文中定义的所有PropertyResourceConfigurers，并在这个类的bean定义上运行它们。然后更新值。
   */
  private void processPropertyPlaceHolders() {
    Map<String, PropertyResourceConfigurer> prcs = applicationContext.getBeansOfType(PropertyResourceConfigurer.class,
        false, false);

    if (!prcs.isEmpty() && applicationContext instanceof ConfigurableApplicationContext) {
      BeanDefinition mapperScannerBean = ((ConfigurableApplicationContext) applicationContext).getBeanFactory()
          .getBeanDefinition(beanName);

      // PropertyResourceConfigurer不公开显式执行
      // 属性占位符替换的任何方法。
      // 相反，创建一个仅包含此映射器扫描器的BeanFactory并post处理该工厂。
      DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
      factory.registerBeanDefinition(beanName, mapperScannerBean);

      for (PropertyResourceConfigurer prc : prcs.values()) {
        prc.postProcessBeanFactory(factory);
      }

      PropertyValues values = mapperScannerBean.getPropertyValues();

      this.basePackage = getPropertyValue("basePackage", values);
      this.sqlSessionFactoryBeanName = getPropertyValue("sqlSessionFactoryBeanName", values);
      this.sqlSessionTemplateBeanName = getPropertyValue("sqlSessionTemplateBeanName", values);
      this.lazyInitialization = getPropertyValue("lazyInitialization", values);
      this.defaultScope = getPropertyValue("defaultScope", values);
    }
    this.basePackage = Optional.ofNullable(this.basePackage).map(getEnvironment()::resolvePlaceholders).orElse(null);
    this.sqlSessionFactoryBeanName = Optional.ofNullable(this.sqlSessionFactoryBeanName)
        .map(getEnvironment()::resolvePlaceholders).orElse(null);
    this.sqlSessionTemplateBeanName = Optional.ofNullable(this.sqlSessionTemplateBeanName)
        .map(getEnvironment()::resolvePlaceholders).orElse(null);
    this.lazyInitialization = Optional.ofNullable(this.lazyInitialization).map(getEnvironment()::resolvePlaceholders)
        .orElse(null);
    this.defaultScope = Optional.ofNullable(this.defaultScope).map(getEnvironment()::resolvePlaceholders).orElse(null);
  }

  private Environment getEnvironment() {
    return this.applicationContext.getEnvironment();
  }

  private String getPropertyValue(String propertyName, PropertyValues values) {
    PropertyValue property = values.getPropertyValue(propertyName);

    if (property == null) {
      return null;
    }

    Object value = property.getValue();

    if (value == null) {
      return null;
    } else if (value instanceof String) {
      return value.toString();
    } else if (value instanceof TypedStringValue) {
      return ((TypedStringValue) value).getValue();
    } else {
      return null;
    }
  }

}
