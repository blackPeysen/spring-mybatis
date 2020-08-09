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
package org.mybatis.spring.annotation;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.mybatis.spring.mapper.MapperFactoryBean;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.context.annotation.Import;

/**
 * 在使用Java配置时，使用这个注释来注册MyBatis映射器接口。
 * 它在与{@link MapperScannerConfigurer}通过{@link MapperScannerRegistrar}执行相同的工作时执行。
 *
 * 可以指定{@link #basePackageClasses}或{@link #basePackages}(或其别名{@link #value})来定义要扫描的特定包。
 * 从2.0.4开始，如果没有定义特定的包，那么扫描将从声明这个注释的类的包中进行。
 *
 * 配置的例子:
 * <pre class="code">
 * &#064;Configuration
 * &#064;MapperScan("org.mybatis.spring.sample.mapper")
 * public class AppConfig {
 *
 *   &#064;Bean
 *   public DataSource dataSource() {
 *     return new EmbeddedDatabaseBuilder().addScript("schema.sql").build();
 *   }
 *
 *   &#064;Bean
 *   public DataSourceTransactionManager transactionManager() {
 *     return new DataSourceTransactionManager(dataSource());
 *   }
 *
 *   &#064;Bean
 *   public SqlSessionFactory sqlSessionFactory() throws Exception {
 *     SqlSessionFactoryBean sessionFactory = new SqlSessionFactoryBean();
 *     sessionFactory.setDataSource(dataSource());
 *     return sessionFactory.getObject();
 *   }
 * }
 * </pre>
 *
 * @author Michael Lanyon
 * @author Eduardo Macarron
 *
 * @since 1.2.0
 * @see MapperScannerRegistrar
 * @see MapperFactoryBean
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Import(MapperScannerRegistrar.class)
@Repeatable(MapperScans.class)
public @interface MapperScan {

  /**
   * {@link #basePackages()}属性的别名。
   * 允许更简洁的注释声明，例如:{@code @MapperScan("org.my.pkg")}，而不是{@code @MapperScan(basePackages = "org.my.pkg"})}。
   *
   * @return 基本包名
   */
  String[] value() default {};

  /**
   * 扫描MyBatis接口的基本包。
   * 注意，只有具有至少一个方法的接口才会注册，具体类将被忽略。
   *
   * @return 用于扫描映射器接口的基本包名称
   */
  String[] basePackages() default {};

  /**
   * 类型安全的{@link #basePackages()}的替代方案，用于指定要扫描的包以寻找带注释的组件。
   * 每个指定类别的包将被扫描。
   * 考虑在每个包中创建一个特殊的no-op标记类或接口，它除了被这个属性引用之外没有其他用途。
   *
   * @return 指明扫描映射器接口的基包的类
   */
  Class<?>[] basePackageClasses() default {};

  /**
   * 用于命名Spring容器中检测到的组件的{@link BeanNameGenerator}类。
   *
   * @return {@link BeanNameGenerator}的类
   */
  Class<? extends BeanNameGenerator> nameGenerator() default BeanNameGenerator.class;

  /**
   * 此属性指定扫描程序将搜索的注释。
   * 扫描器将注册基包中同样具有指定注释的所有接口。
   * 注意，这可以与markerInterface结合使用。
   *
   * @return 扫描器要搜索的注释
   */
  Class<? extends Annotation> annotationClass() default Annotation.class;

  /**
   * 此属性指定扫描程序将搜索的父对象。
   * 扫描器将注册基包中同样将指定接口类作为父类的所有接口。
   * 注意，这可以与annotationClass结合使用。
   *
   * @return 扫描器将搜索的父元素
   */
  Class<?> markerInterface() default Class.class;

  /**
   * 指定在spring上下文中有多于一个的情况下使用哪个{@code SqlSessionTemplate}。
   * 通常只有当你有多个数据源时才需要。
   *
   * @return {@code SqlSessionTemplate}的bean名
   */
  String sqlSessionTemplateRef() default "";

  /**
   * 指定在spring上下文中有多于一个的情况下使用哪个{@code SqlSessionFactory}。
   * 通常只有当你有多个数据源时才需要。
   *
   * @return {@code SqlSessionFactory}的bean名
   */
  String sqlSessionFactoryRef() default "";

  /**
   * 指定一个自定义MapperFactoryBean来返回一个mybatis代理作为spring bean。
   *
   * @return the class of {@code MapperFactoryBean}
   */
  Class<? extends MapperFactoryBean> factoryBean() default MapperFactoryBean.class;

  /**
   * 是否启用映射器bean的延迟初始化。
   * 默认值是{@code false}。
   *
   * @return 设置{@code true}启用延迟初始化
   * @since 2.0.2
   */
  String lazyInitialization() default "";

  /**
   * 指定扫描映射器的默认范围。
   * 默认是{@code ""}  (equiv to singleton).
   *
   * @return 默认范围
   */
  String defaultScope() default AbstractBeanDefinition.SCOPE_DEFAULT;

}
