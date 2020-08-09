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
package org.mybatis.spring;

import org.springframework.dao.UncategorizedDataAccessException;

/**
 * MyBatis特定的子类{@code UncategorizedDataAccessException}，
 * 用于不匹配任何具体的{@code org.springframework.dao}例外。
 *
 * 在MyBatis 3中{@code org.apache.ibatis.exception。PersistenceException}是一个{@code RuntimeException}，
 * 但是使用这个包装器类将所有东西都放在一个单一层次结构下，这样客户端代码处理起来会更容易。
 *
 * @author Hunter Presnall
 */
@SuppressWarnings("squid:MaximumInheritanceDepth") // It is the intended design
public class MyBatisSystemException extends UncategorizedDataAccessException {

  private static final long serialVersionUID = -5284728621670758939L;

  public MyBatisSystemException(Throwable cause) {
    super(null, cause);
  }

}
