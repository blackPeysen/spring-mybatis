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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;

/**
 * 聚合了几个{@link MapperScan}注释的容器注释。
 * <p>
 * 可以本地使用，声明几个嵌套的{@link MapperScan}注释。
 * 也可以与Java 8对可重复注释的支持一起使用，其中{@link MapperScan}可以简单地在同一个方法上声明几次，隐式地生成这个容器注释。
 *
 * @author Kazuki Shimizu
 * @since 2.0.0
 * @see MapperScan
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Import(MapperScannerRegistrar.RepeatingRegistrar.class)
public @interface MapperScans {
  MapperScan[] value();
}
