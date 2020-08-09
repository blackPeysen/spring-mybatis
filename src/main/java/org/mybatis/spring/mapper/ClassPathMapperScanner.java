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

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.logging.Logger;
import org.mybatis.logging.LoggerFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.scope.ScopedProxyFactoryBean;
import org.springframework.aop.scope.ScopedProxyUtils;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.util.StringUtils;

/**
 * 一个{@link ClassPathBeanDefinitionScanner}，给扫描到的Mapper.class对应的beanDefinition添加各种属性
 * 它通过{@code basePackage}、{@code annotationClass} 或 {@code markerInterface}注册映射器。
 * 如果指定了{@code annotationClass} 或 {@code markerInterface}，则只搜索指定的类型(搜索所有接口将被禁用)。
 *
 * 这个功能以前是{@link MapperScannerConfigurer}的私有类，但是在版本1.2.0中被打破了。
 *
 * @author Hunter Presnall
 * @author Eduardo Macarron
 *
 * @see MapperFactoryBean
 * @since 1.2.0
 */
public class ClassPathMapperScanner extends ClassPathBeanDefinitionScanner {

  private static final Logger LOGGER = LoggerFactory.getLogger(ClassPathMapperScanner.class);

  // 是否将该mapper添加到Configuration中
  private boolean addToConfig = true;

  // 该mapper是否懒加载
  private boolean lazyInitialization;

  // 该mapper对应的SqlSessionFactory
  private SqlSessionFactory sqlSessionFactory;

  // 该mapper对应的SqlSessionTemplate
  private SqlSessionTemplate sqlSessionTemplate;

  // 该mapper对应的SqlSessionFactory的beanName
  private String sqlSessionTemplateBeanName;

  // 该mapper对应的SqlSessionTemplate的BeanName
  private String sqlSessionFactoryBeanName;

  private Class<? extends Annotation> annotationClass;

  private Class<?> markerInterface;

  // 扫描出的mapper.java 使用的FactoryBean，使用该对象工厂生成mapper接口的代理对象
  private Class<? extends MapperFactoryBean> mapperFactoryBeanClass = MapperFactoryBean.class;

  // 默认的对象范围
  private String defaultScope;

  public ClassPathMapperScanner(BeanDefinitionRegistry registry) {
    super(registry, false);
  }

  public void setAddToConfig(boolean addToConfig) {
    this.addToConfig = addToConfig;
  }

  public void setAnnotationClass(Class<? extends Annotation> annotationClass) {
    this.annotationClass = annotationClass;
  }

  /**
   * 设置是否启用映射器bean的延迟初始化。
   * 默认值是{@code false}。
   *
   * @param lazyInitialization
   *          Set the @{code true} to enable
   * @since 2.0.2
   */
  public void setLazyInitialization(boolean lazyInitialization) {
    this.lazyInitialization = lazyInitialization;
  }

  public void setMarkerInterface(Class<?> markerInterface) {
    this.markerInterface = markerInterface;
  }

  public void setSqlSessionFactory(SqlSessionFactory sqlSessionFactory) {
    this.sqlSessionFactory = sqlSessionFactory;
  }

  public void setSqlSessionTemplate(SqlSessionTemplate sqlSessionTemplate) {
    this.sqlSessionTemplate = sqlSessionTemplate;
  }

  public void setSqlSessionTemplateBeanName(String sqlSessionTemplateBeanName) {
    this.sqlSessionTemplateBeanName = sqlSessionTemplateBeanName;
  }

  public void setSqlSessionFactoryBeanName(String sqlSessionFactoryBeanName) {
    this.sqlSessionFactoryBeanName = sqlSessionFactoryBeanName;
  }

  /**
   * 从2.0.1开始，请使用{@link #setMapperFactoryBeanClass(Class)}。
   */
  @Deprecated
  public void setMapperFactoryBean(MapperFactoryBean<?> mapperFactoryBean) {
    this.mapperFactoryBeanClass = mapperFactoryBean == null ? MapperFactoryBean.class : mapperFactoryBean.getClass();
  }

  /**
   * 设置{@code MapperFactoryBean}类。
   * @param mapperFactoryBeanClass
   *          the {@code MapperFactoryBean} class
   * @since 2.0.1
   */
  public void setMapperFactoryBeanClass(Class<? extends MapperFactoryBean> mapperFactoryBeanClass) {
    this.mapperFactoryBeanClass = mapperFactoryBeanClass == null ? MapperFactoryBean.class : mapperFactoryBeanClass;
  }

  /**
   * 设置扫描的映射器的默认范围。
   * <p>
   * Default is {@code null} (equiv to singleton).
   * </p>
   *
   * @param defaultScope
   *          the scope
   * @since 2.0.6
   */
  public void setDefaultScope(String defaultScope) {
    this.defaultScope = defaultScope;
  }

  /**
   * 配置父扫描程序以搜索正确的接口。
   * 它可以搜索所有接口，或者只搜索那些扩展了markerInterface的或者和那些用annotationClass标注的接口
   */
  public void registerFilters() {
    boolean acceptAllInterfaces = true;

    // 如果指定，使用给定的注释或标记接口
    if (this.annotationClass != null) {
      addIncludeFilter(new AnnotationTypeFilter(this.annotationClass));
      acceptAllInterfaces = false;
    }

    // 重写AssignableTypeFilter以忽略实际标记接口上的匹配项
    if (this.markerInterface != null) {
      addIncludeFilter(new AssignableTypeFilter(this.markerInterface) {
        @Override
        protected boolean matchClassName(String className) {
          return false;
        }
      });
      acceptAllInterfaces = false;
    }

    if (acceptAllInterfaces) {
      // 默认包括接受所有类的过滤器
      addIncludeFilter((metadataReader, metadataReaderFactory) -> true);
    }

    // 排除package-info.java
    addExcludeFilter((metadataReader, metadataReaderFactory) -> {
      String className = metadataReader.getClassMetadata().getClassName();
      return className.endsWith("package-info");
    });
  }

  /**
   * 调用父搜索，父搜索将搜索并注册所有候选项。
   * 然后对注册的对象进行post处理，将它们设置为mapperfactorybean
   */
  @Override
  public Set<BeanDefinitionHolder> doScan(String... basePackages) {
    // 调用父类的doScan()方法进行包扫描
    Set<BeanDefinitionHolder> beanDefinitions = super.doScan(basePackages);

    /**
     * 扫描后，若包下存在Mapper接口类，那么beanDefinitions不会为空
     */
    if (beanDefinitions.isEmpty()) {
      LOGGER.warn(() -> "No MyBatis mapper was found in '" + Arrays.toString(basePackages)
          + "' package. Please check your configuration.");
    } else {
      /**
       * 对扫描出的beanDefinition对象进行属性修改:
       *    因为扫描出的mapper是个接口类，并不能被实例化，spring在这里进行了内部替换操作
       */
      processBeanDefinitions(beanDefinitions);
    }

    return beanDefinitions;
  }

  /**
   * 对扫描出来的bean定义进行内部替换，可以返回该接口的一个代理类
   * @param beanDefinitions
   */
  private void processBeanDefinitions(Set<BeanDefinitionHolder> beanDefinitions) {
    AbstractBeanDefinition definition;
    BeanDefinitionRegistry registry = getRegistry();

    // 循环遍历beanDefinition
    for (BeanDefinitionHolder holder : beanDefinitions) {
      // 获取beanDefinition
      definition = (AbstractBeanDefinition) holder.getBeanDefinition();
      boolean scopedProxy = false;
      if (ScopedProxyFactoryBean.class.getName().equals(definition.getBeanClassName())) {
        definition = (AbstractBeanDefinition) Optional
            .ofNullable(((RootBeanDefinition) definition).getDecoratedDefinition())
            .map(BeanDefinitionHolder::getBeanDefinition).orElseThrow(() -> new IllegalStateException(
                "The target bean definition of scoped proxy bean not found. Root bean definition[" + holder + "]"));
        scopedProxy = true;
      }
      // 获取bean对应的className，全限定类名
      String beanClassName = definition.getBeanClassName();
      LOGGER.debug(() -> "Creating MapperFactoryBean with name '" + holder.getBeanName() + "' and '" + beanClassName
          + "' mapperInterface");

      /**
       * mapper接口是bean的原始类
       * 但是，bean的实际类是MapperFactoryBean
       * 1、beanDefinition此时是个接口，无法被实例化，所以将它的BeanClass改为MapperFactoryBean,
       *      使用mapperFactoryBean的工厂方法getObject()方法替我们返回一个Mapper接口的实例对象。
       * 2、指定生成实例对象的方式：使用构造器传入参数的方式。
       *      要传入原接口类型，因为使用jdk代理，要面向接口代理，最终返回一个原生接口的jdk代理实例对象。
       */
      definition.getConstructorArgumentValues().addGenericArgumentValue(beanClassName); // issue #59
      definition.setBeanClass(this.mapperFactoryBeanClass);

      /**
       * 是否添加到Configuration中
       */
      definition.getPropertyValues().add("addToConfig", this.addToConfig);

      /**
       * 给definition添加sqlSessionFactory属性
       */
      boolean explicitFactoryUsed = false;
      if (StringUtils.hasText(this.sqlSessionFactoryBeanName)) {
        definition.getPropertyValues().add("sqlSessionFactory",
            new RuntimeBeanReference(this.sqlSessionFactoryBeanName));
        explicitFactoryUsed = true;
      } else if (this.sqlSessionFactory != null) {
        definition.getPropertyValues().add("sqlSessionFactory", this.sqlSessionFactory);
        explicitFactoryUsed = true;
      }

      if (StringUtils.hasText(this.sqlSessionTemplateBeanName)) {
        if (explicitFactoryUsed) {
          LOGGER.warn(
              () -> "Cannot use both: sqlSessionTemplate and sqlSessionFactory together. sqlSessionFactory is ignored.");
        }
        definition.getPropertyValues().add("sqlSessionTemplate",
            new RuntimeBeanReference(this.sqlSessionTemplateBeanName));
        explicitFactoryUsed = true;
      } else if (this.sqlSessionTemplate != null) {
        if (explicitFactoryUsed) {
          LOGGER.warn(
              () -> "Cannot use both: sqlSessionTemplate and sqlSessionFactory together. sqlSessionFactory is ignored.");
        }
        definition.getPropertyValues().add("sqlSessionTemplate", this.sqlSessionTemplate);
        explicitFactoryUsed = true;
      }

      if (!explicitFactoryUsed) {
        LOGGER.debug(() -> "Enabling autowire by type for MapperFactoryBean with name '" + holder.getBeanName() + "'.");
        /**
         * 使用AUTOWIRE_BY_TYPE的注入方法，增强了容错性；
         * 如果使用AUTOWIRE_BY_NAME,就必须要求注入beanName的名称与被注入的类属性名完全一致
         */
        definition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);
      }

      // 是否懒加载
      definition.setLazyInit(lazyInitialization);

      if (scopedProxy) {
        continue;
      }

      /**
       * 判断是否是原型还是单例模式
       */
      if (ConfigurableBeanFactory.SCOPE_SINGLETON.equals(definition.getScope()) && defaultScope != null) {
        definition.setScope(defaultScope);
      }

      // 是否单例模式
      if (!definition.isSingleton()) {
        // 生成一个原生的
        BeanDefinitionHolder proxyHolder = ScopedProxyUtils.createScopedProxy(holder, registry, true);
        if (registry.containsBeanDefinition(proxyHolder.getBeanName())) {
          registry.removeBeanDefinition(proxyHolder.getBeanName());
        }
        registry.registerBeanDefinition(proxyHolder.getBeanName(), proxyHolder.getBeanDefinition());
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
    return beanDefinition.getMetadata().isInterface() && beanDefinition.getMetadata().isIndependent();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected boolean checkCandidate(String beanName, BeanDefinition beanDefinition) {
    if (super.checkCandidate(beanName, beanDefinition)) {
      return true;
    } else {
      LOGGER.warn(() -> "Skipping MapperFactoryBean with name '" + beanName + "' and '"
          + beanDefinition.getBeanClassName() + "' mapperInterface" + ". Bean already defined with the same name!");
      return false;
    }
  }

}
