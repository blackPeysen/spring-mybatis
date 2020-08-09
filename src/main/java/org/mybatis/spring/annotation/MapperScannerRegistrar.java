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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.mybatis.spring.mapper.ClassPathMapperScanner;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * 用于解析@MapperScan注解，主要是注册一个MapperScannerConfigurer的BeanDefinition对象
 * 一个{@link ImportBeanDefinitionRegistrar}允许MyBatis映射器扫描的注释配置。
 * 使用一个@Enable注释允许通过@Component配置注册bean，
 * 而实现{@code BeanDefinitionRegistryPostProcessor}将适用于XML配置。
 *
 * @author Michael Lanyon
 * @author Eduardo Macarron
 * @author Putthiphong Boonphong
 *
 * @see MapperFactoryBean
 * @see ClassPathMapperScanner
 * @since 1.2.0
 */
public class MapperScannerRegistrar implements ImportBeanDefinitionRegistrar, ResourceLoaderAware {

  /**
   * {@inheritDoc}
   * @deprecated Since 2.0.2, 这种方法从未被使用过。
   */
  @Override
  @Deprecated
  public void setResourceLoader(ResourceLoader resourceLoader) {
    // NOP
  }

  /**
   * {@inheritDoc} 获取@MapperScan 注解的元信息，然后进行注册mapper对应的beanDefinition对象
   * @param importingClassMetadata: 是指加了@MapperScan注解的类元信息，比如引导类
   * @param registry: 注册器
   */
  @Override
  public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
    /**
     * 从类元信息上获取@MapperScan的注解信息，并转换为AnnotationAttributes对象
     */
    AnnotationAttributes mapperScanAttrs = AnnotationAttributes
        .fromMap(importingClassMetadata.getAnnotationAttributes(MapperScan.class.getName()));

    if (mapperScanAttrs != null) {
      /**
       * 根据@MapperScan的注解信息，注册一个BeanDefinition
       */
      registerBeanDefinitions(importingClassMetadata, mapperScanAttrs, registry,
          generateBaseBeanName(importingClassMetadata, 0));
    }
  }

  /**
   * 获取@MapperScan注解的属性值，然后传递给MapperScannerConfigurer，最后对MapperScannerConfigurer进行注册
   *    获取xxxMapper.java，并注册，
   *    解析xxxMapper.xml是由SqlSessionFactroyBean执行
   * @param annoMeta
   * @param annoAttrs
   * @param registry
   * @param beanName
   */
  void registerBeanDefinitions(AnnotationMetadata annoMeta, AnnotationAttributes annoAttrs,
      BeanDefinitionRegistry registry, String beanName) {

    /**
     * 创建bean定义构建器，通过构建器来创建出我们的bean定义《MapperScannerConfigurer》，应用到【建造者模式】
     * 构建一个包含MapperScannerConfigurer对象的构建器：
     *    目的的是生成一个MapperScannerConfigurer的BeanDefinition对象
     */
    BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(MapperScannerConfigurer.class);
    builder.addPropertyValue("processPropertyPlaceHolders", true);

    /**
     * 获取 @MapperScan 注解上的属性值
     */
    Class<? extends Annotation> annotationClass = annoAttrs.getClass("annotationClass");
    if (!Annotation.class.equals(annotationClass)) {
      builder.addPropertyValue("annotationClass", annotationClass);
    }

    Class<?> markerInterface = annoAttrs.getClass("markerInterface");
    if (!Class.class.equals(markerInterface)) {
      builder.addPropertyValue("markerInterface", markerInterface);
    }

    Class<? extends BeanNameGenerator> generatorClass = annoAttrs.getClass("nameGenerator");
    if (!BeanNameGenerator.class.equals(generatorClass)) {
      builder.addPropertyValue("nameGenerator", BeanUtils.instantiateClass(generatorClass));
    }

    Class<? extends MapperFactoryBean> mapperFactoryBeanClass = annoAttrs.getClass("factoryBean");
    if (!MapperFactoryBean.class.equals(mapperFactoryBeanClass)) {
      builder.addPropertyValue("mapperFactoryBeanClass", mapperFactoryBeanClass);
    }

    String sqlSessionTemplateRef = annoAttrs.getString("sqlSessionTemplateRef");
    if (StringUtils.hasText(sqlSessionTemplateRef)) {
      builder.addPropertyValue("sqlSessionTemplateBeanName", annoAttrs.getString("sqlSessionTemplateRef"));
    }

    String sqlSessionFactoryRef = annoAttrs.getString("sqlSessionFactoryRef");
    if (StringUtils.hasText(sqlSessionFactoryRef)) {
      builder.addPropertyValue("sqlSessionFactoryBeanName", annoAttrs.getString("sqlSessionFactoryRef"));
    }

    List<String> basePackages = new ArrayList<>();
    basePackages.addAll(
        Arrays.stream(annoAttrs.getStringArray("value")).filter(StringUtils::hasText).collect(Collectors.toList()));

    basePackages.addAll(Arrays.stream(annoAttrs.getStringArray("basePackages")).filter(StringUtils::hasText)
        .collect(Collectors.toList()));

    basePackages.addAll(Arrays.stream(annoAttrs.getClassArray("basePackageClasses")).map(ClassUtils::getPackageName)
        .collect(Collectors.toList()));

    if (basePackages.isEmpty()) {
      basePackages.add(getDefaultBasePackage(annoMeta));
    }

    String lazyInitialization = annoAttrs.getString("lazyInitialization");
    if (StringUtils.hasText(lazyInitialization)) {
      builder.addPropertyValue("lazyInitialization", lazyInitialization);
    }

    String defaultScope = annoAttrs.getString("defaultScope");
    if (!AbstractBeanDefinition.SCOPE_DEFAULT.equals(defaultScope)) {
      builder.addPropertyValue("defaultScope", defaultScope);
    }

    builder.addPropertyValue("basePackage", StringUtils.collectionToCommaDelimitedString(basePackages));

    /**
     * 在注册前给该BeanDefinition的各属性进行填充
     * 使用默认的注册器DefaultListableBeanFactory注册该BeanDefinition
     */
    registry.registerBeanDefinition(beanName, builder.getBeanDefinition());

  }

  /**
   * 获取类的基本命名
   * @param importingClassMetadata
   * @param index
   * @return
   */
  private static String generateBaseBeanName(AnnotationMetadata importingClassMetadata, int index) {
    return importingClassMetadata.getClassName() + "#" + MapperScannerRegistrar.class.getSimpleName() + "#" + index;
  }

  /**
   * 如果@MapperScan没有指定扫描包，则默认使用最顶层包作为扫描基本包
   * @param importingClassMetadata
   * @return
   */
  private static String getDefaultBasePackage(AnnotationMetadata importingClassMetadata) {
    return ClassUtils.getPackageName(importingClassMetadata.getClassName());
  }

  /**
   * 一个{@link MapperScannerRegistrar}用于{@link MapperScans}。
   *
   * @since 2.0.0
   */
  static class RepeatingRegistrar extends MapperScannerRegistrar {
    /**
     * {@inheritDoc}
     */
    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
      AnnotationAttributes mapperScansAttrs = AnnotationAttributes
          .fromMap(importingClassMetadata.getAnnotationAttributes(MapperScans.class.getName()));
      if (mapperScansAttrs != null) {
        AnnotationAttributes[] annotations = mapperScansAttrs.getAnnotationArray("value");
        for (int i = 0; i < annotations.length; i++) {
          registerBeanDefinitions(importingClassMetadata, annotations[i], registry,
              generateBaseBeanName(importingClassMetadata, i));
        }
      }
    }
  }

}
