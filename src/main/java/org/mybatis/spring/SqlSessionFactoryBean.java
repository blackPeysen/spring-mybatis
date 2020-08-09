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

import static org.springframework.util.Assert.notNull;
import static org.springframework.util.Assert.state;
import static org.springframework.util.ObjectUtils.isEmpty;
import static org.springframework.util.StringUtils.hasLength;
import static org.springframework.util.StringUtils.tokenizeToStringArray;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;

import javax.sql.DataSource;

import org.apache.ibatis.builder.xml.XMLConfigBuilder;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.io.VFS;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.type.TypeHandler;
import org.mybatis.logging.Logger;
import org.mybatis.logging.LoggerFactory;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.NestedIOException;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.ClassMetadata;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.util.ClassUtils;

/**
 * 创建MyBatis {@code SqlSessionFactory}的@code FactoryBean。
 * 这是在Spring应用程序上下文中设置共享MyBatis {@code SqlSessionFactory}的常用方法;
 * 然后，可以通过依赖项注入将SqlSessionFactory传递给基于mybatisy的dao。
 *
 * {@code DataSourceTransactionManager}或{@code JtaTransactionManager}可以与{@code SqlSessionFactory}一起用于事务界定。
 *  JTA应该用于跨多个数据库的事务，或者在使用容器管理事务(CMT)时用于。
 * 实现接口：
 *     FactoryBean<SqlSessionFactory>： 生成的 SqlSessionFactory 的工厂
 *     InitializingBean ： 实现该接口的 afterPropertiesSet()
 *     ApplicationListener： 实现该解耦的 onApplicationEvent()
 *
 * @author Putthiphong Boonphong
 * @author Hunter Presnall
 * @author Eduardo Macarron
 * @author Eddú Meléndez
 * @author Kazuki Shimizu
 *
 * @see #setConfigLocation
 * @see #setDataSource
 */
public class SqlSessionFactoryBean
    implements FactoryBean<SqlSessionFactory>, InitializingBean, ApplicationListener<ApplicationEvent> {

  private static final Logger LOGGER = LoggerFactory.getLogger(SqlSessionFactoryBean.class);

  private static final ResourcePatternResolver RESOURCE_PATTERN_RESOLVER = new PathMatchingResourcePatternResolver();
  private static final MetadataReaderFactory METADATA_READER_FACTORY = new CachingMetadataReaderFactory();

  private Resource configLocation;

  /**
   * 保存一个全局的Configuration对象
   *    可以从xml配置文件生成：mybatis-config.xml
   *    可以外部传入：在MybatisAutoConfiguration类中
   */
  private Configuration configuration;

  //
  /**
   * 映射器（mappers）文件存放的位置
   *    resource:相对于类路径的资源引用
   *    url:完全限定资源定位符
   *    class:使用映射器接口实现类的完全限定类名
   *    package:将包内的映射器接口实现全部注册为映射器
   */
  private Resource[] mapperLocations;

  /**
   * 使用标准的 JDBC 数据源接口来配置 JDBC 连接对象的资源。
   */
  private DataSource dataSource;

  /**
   * 事务管理器（transactionManager）
   * 在 MyBatis 中有两种类型的事务管理器（type="[JDBC|MANAGED]"）
   *    JDBC – 这个配置直接使用了 JDBC 的提交和回滚设施，它依赖从数据源获得的连接来管理事务作用域。
   *    MANAGED – 这个配置几乎没做什么。它从不提交或回滚一个连接，而是让容器来管理事务的整个生命周期（比如 JEE 应用服务器的上下文）。
   *              默认情况下它会关闭连接。然而一些容器并不希望连接被关闭，因此需要将 closeConnection 属性设置为 false 来阻止默认的关闭行为。
   */
  private TransactionFactory transactionFactory;

  /**
   * 属性可以在外部进行配置，并可以进行动态替换。
   * 既可以在典型的 Java 属性文件中配置这些属性，也可以在 properties 元素的子元素中设置
   */
  private Properties configurationProperties;

  // 用来生成SqlSessionFactory对象的构建器
  private SqlSessionFactoryBuilder sqlSessionFactoryBuilder = new SqlSessionFactoryBuilder();

  // 最终生成的SqlSessionFactory实例对象
  private SqlSessionFactory sqlSessionFactory;

  // EnvironmentAware requires spring 3.1
  /**
   * MyBatis 可以配置成适应多种环境，这种机制有助于将 SQL 映射应用于多种数据库之中， 现实情况下有多种理由需要这么做。
   * 例如，开发、测试和生产环境需要有不同的配置；或者想在具有相同 Schema 的多个生产数据库中使用相同的 SQL 映射。
   * 不过要记住：尽管可以配置多个环境，但每个 SqlSessionFactory 实例只能选择一种环境。
   * 每个数据库对应一个 SqlSessionFactory 实例
   */
  private String environment = SqlSessionFactoryBean.class.getSimpleName();

  // 快速失败
  private boolean failFast;

  /**
   * MyBatis 配置的插件列表
   * MyBatis 允许你在映射语句执行过程中的某一点进行拦截调用。
   * 默认情况下，MyBatis 允许使用插件来拦截的方法调用包括：
   *    Executor (update, query, flushStatements, commit, rollback, getTransaction, close, isClosed)
   *    ParameterHandler (getParameterObject, setParameters)
   *    ResultSetHandler (handleResultSets, handleOutputParameters)
   *    StatementHandler (prepare, parameterize, batch, update, query)
   *  只需实现 Interceptor 接口
   */

  private Interceptor[] plugins;

  // 自定义的mybatis配置的类型处理器列表
  private TypeHandler<?>[] typeHandlers;

  //类型处理器（typeHandlers）
  private String typeHandlersPackage;

  @SuppressWarnings("rawtypes")
  private Class<? extends TypeHandler> defaultEnumTypeHandler;

  /**
   * 类型别名
   */
  private Class<?>[] typeAliases;

  /**
   * 可以指定一个包名，MyBatis会在包名下面搜索需要的Java Bean
   */
  private String typeAliasesPackage;

  private Class<?> typeAliasesSuperType;

  private LanguageDriver[] scriptingLanguageDrivers;

  private Class<? extends LanguageDriver> defaultScriptingLanguageDriver;

  // issue #19. 没有默认的提供程序。
  /**
   * 数据库厂商标识（databaseIdProvider）
   * MyBatis 可以根据不同的数据库厂商执行不同的语句，这种多厂商的支持是基于映射语句中的 databaseId 属性。
   * MyBatis 会加载带有匹配当前数据库 databaseId 属性和所有不带 databaseId 属性的语句。
   * 如果同时找到带有 databaseId 和不带 databaseId 的相同语句，则后者会被舍弃。
   */
  private DatabaseIdProvider databaseIdProvider;

  private Class<? extends VFS> vfs;

  // 缓存，用来缓存？
  private Cache cache;

  /**
   * 对象工厂（objectFactory）
   * 每次 MyBatis 创建结果对象的新实例时，它都会使用一个对象工厂（ObjectFactory）实例来完成实例化工作。
   * 默认的对象工厂需要做的仅仅是实例化目标类，要么通过默认无参构造方法，要么通过存在的参数映射来调用带有参数的构造方法。
   * 如果想覆盖对象工厂的默认行为，可以通过创建自己的对象工厂来实现。
   */
  private ObjectFactory objectFactory;

  private ObjectWrapperFactory objectWrapperFactory;

  /**
   * ObjectFactory集。
   *
   * @since 1.1.2
   * @param objectFactory：一个自定义ObjectFactory
   */
  public void setObjectFactory(ObjectFactory objectFactory) {
    this.objectFactory = objectFactory;
  }

  /**
   * 设置ObjectWrapperFactory。
   *
   * @since 1.1.2
   * @param objectWrapperFactory
   *          a specified ObjectWrapperFactory
   */
  public void setObjectWrapperFactory(ObjectWrapperFactory objectWrapperFactory) {
    this.objectWrapperFactory = objectWrapperFactory;
  }

  /**
   * 得到了DatabaseIdProvider
   *
   * @since 1.1.0
   * @return 指定DatabaseIdProvider
   */
  public DatabaseIdProvider getDatabaseIdProvider() {
    return databaseIdProvider;
  }

  /**
   * 设置DatabaseIdProvider。
   * 在版本1.2.2中，默认情况下该变量没有初始化。
   *
   * @since 1.1.0
   * @param databaseIdProvider
   *          a DatabaseIdProvider
   */
  public void setDatabaseIdProvider(DatabaseIdProvider databaseIdProvider) {
    this.databaseIdProvider = databaseIdProvider;
  }

  /**
   * Gets the VFS.
   *
   * @return a specified VFS
   */
  public Class<? extends VFS> getVfs() {
    return this.vfs;
  }

  /**
   * Sets the VFS.
   *
   * @param vfs：a VFS
   */
  public void setVfs(Class<? extends VFS> vfs) {
    this.vfs = vfs;
  }

  /**
   * Gets the Cache.
   * @return a specified Cache
   */
  public Cache getCache() {
    return this.cache;
  }

  /**
   * Sets the Cache.
   * @param cache
   */
  public void setCache(Cache cache) {
    this.cache = cache;
  }

  /**
   * Mybatis插件列表
   * @since 1.0.1
   * @param plugins：插件列表
   *
   */
  public void setPlugins(Interceptor... plugins) {
    this.plugins = plugins;
  }

  /**
   * 搜索类型别名的包
   * 从2.0.1开始，允许指定一个通配符，比如{@code com.example.*.model}。
   * @since 1.0.1
   *
   * @param typeAliasesPackage：包扫描域对象
   *
   */
  public void setTypeAliasesPackage(String typeAliasesPackage) {
    this.typeAliasesPackage = typeAliasesPackage;
  }

  /**
   * 超类，域对象必须扩展该超类以创建类型别名。
   * 如果没有配置要扫描的包，则没有效果。
   *
   * @since 1.1.2
   * @param typeAliasesSuperType：域对象的超类
   */
  public void setTypeAliasesSuperType(Class<?> typeAliasesSuperType) {
    this.typeAliasesSuperType = typeAliasesSuperType;
  }

  /**
   * 搜索类型处理程序的包
   * 从2.0.1开始，允许指定一个通配符，比如{@code com.example.*.typehandler}。
   * @since 1.0.1
   *
   * @param typeHandlersPackage：包以扫描类型处理程序
   */
  public void setTypeHandlersPackage(String typeHandlersPackage) {
    this.typeHandlersPackage = typeHandlersPackage;
  }

  /**
   * 设置类型的处理程序。它们必须使用{@code MappedTypes}进行注释，也可以使用{@code MappedJdbcTypes}进行注释。
   * @since 1.0.1
   *
   * @param typeHandlers：类型处理程序列表
   */
  public void setTypeHandlers(TypeHandler<?>... typeHandlers) {
    this.typeHandlers = typeHandlers;
  }

  /**
   * 为enum设置默认类型处理程序类。
   * @since 2.0.5
   * @param defaultEnumTypeHandler：枚举的默认类型处理程序类
   */
  public void setDefaultEnumTypeHandler(
      @SuppressWarnings("rawtypes") Class<? extends TypeHandler> defaultEnumTypeHandler) {
    this.defaultEnumTypeHandler = defaultEnumTypeHandler;
  }

  /**
   * 要注册的类型别名列表。它们可以用{@code别名}注释
   * @since 1.0.1
   *
   * @param typeAliases：类型别名列表
   */
  public void setTypeAliases(Class<?>... typeAliases) {
    this.typeAliases = typeAliases;
  }

  /**
   * 如果为真，将在配置上进行最后的检查，以确保所有映射语句都已完全加载，并且没有人仍然等待解析include。
   * 默认值为false。
   *
   * @since 1.0.1
   *
   * @param failFast：使failFast，快速失败
   */
  public void setFailFast(boolean failFast) {
    this.failFast = failFast;
  }

  /**
   * 设置MyBatis {@code SqlSessionFactory}配置文件的位置。
   * 一个典型的值是"WEB-INF/mybatis-configuration.xml"。
   *
   * @param configLocation：MyBatis配置文件的一个位置
   */
  public void setConfigLocation(Resource configLocation) {
    this.configLocation = configLocation;
  }

  /**
   * 设置一个定制的MyBatis配置。
   *
   * @since 1.3.0
   * @param configuration：MyBatis配置
   */
  public void setConfiguration(Configuration configuration) {
    this.configuration = configuration;
  }

  /**
   * 设置运行时将合并到{@code SqlSessionFactory}配置中的MyBatis映射器文件的位置。
   *
   * 这是在MyBatis配置文件中指定“&lt;sqlmapper&gt;”条目的另一种选择。
   * 这个属性基于Spring的资源抽象，也允许在这里指定资源模式:例如： "classpath*:sqlmap/*-mapper.xml"。
   *
   * @param mapperLocations：MyBatis mapper文件的位置
   */
  public void setMapperLocations(Resource... mapperLocations) {
    this.mapperLocations = mapperLocations;
  }

  /**
   * 设置要传递到SqlSession配置中的可选属性，作为配置xml文件中的{@code &lt;properties&gt;}标记的替代。
   * 这将用于解析配置文件中的占位符。
   *
   * @param sqlSessionFactoryProperties：SqlSessionFactory的可选属性
   */
  public void setConfigurationProperties(Properties sqlSessionFactoryProperties) {
    this.configurationProperties = sqlSessionFactoryProperties;
  }

  /**
   * 设置此实例应该为其管理事务的JDBC {@code DataSource}。
   * {@code DataSource}应该与{@code SqlSessionFactory}使用的数据源相匹配:例如，您可以为指定相同的JNDI数据源。
   *
   * 此{@code数据源}的事务JDBC {@code连接}将通过{@code DataSourceUtils}或{@code DataSourceTransactionManager}直接提供给访问此{@code数据源}的应用程序代码。
   *
   * 这里指定的{@code DataSource}应该是管理事务的目标{@code DataSource}，而不是{@code TransactionAwareDataSourceProxy}。
   * 只有数据访问代码可以{@code TransactionAwareDataSourceProxy}一起工作，而事务管理器需要在底层目标{@code DataSource}上工作。
   * 如果仍然有一个{@code TransactionAwareDataSourceProxy}传入，它将被解包装以提取其目标{@code DataSource}。
   *
   * @param dataSource a JDBC {@code DataSource}
   *
   */
  public void setDataSource(DataSource dataSource) {
    if (dataSource instanceof TransactionAwareDataSourceProxy) {
      // 如果我们得到了一个TransactionAwareDataSourceProxy，我们需要为它的底层目标数据源执行事务，
      // 否则数据访问代码将看不到正确公开的事务(即为目标数据源的事务)。
      this.dataSource = ((TransactionAwareDataSourceProxy) dataSource).getTargetDataSource();
    } else {
      this.dataSource = dataSource;
    }
  }

  /**
   * 设置在创建{@code SqlSessionFactory}时使用的{@code SqlSessionFactory}。
   *
   * 这主要用于测试，以便可以注入mock SqlSessionFactory类。
   * 默认情况下，{@code SqlSessionFactoryBuilder}创建{@code DefaultSqlSessionFactory}实例。
   *
   * @param sqlSessionFactoryBuilder：在SqlSessionFactoryBuilder
   */
  public void setSqlSessionFactoryBuilder(SqlSessionFactoryBuilder sqlSessionFactoryBuilder) {
    this.sqlSessionFactoryBuilder = sqlSessionFactoryBuilder;
  }

  /**
   * 设置MyBatis TransactionFactory使用。默认值是{@code SpringManagedTransactionFactory}
   *
   * 默认的{@code SpringManagedTransactionFactory}应该适用于所有情况:
   *    无论是Spring transaction management、EJB CMT还是普通的JTA。
   *    如果没有活动事务，SqlSession操作将以非事务方式执行SQL *语句。
   *
   * <b>强烈建议使用默认的{@code TransactionFactory}。
   *    如果不使用，任何通过Spring的MyBatis框架获取SqlSession的尝试都会抛出一个异常，如果事务是活动的。
   *
   * @see SpringManagedTransactionFactory
   * @param transactionFactory：the MyBatis TransactionFactory
   */
  public void setTransactionFactory(TransactionFactory transactionFactory) {
    this.transactionFactory = transactionFactory;
  }

  /**
   * 注意:这个类覆盖你在MyBatis配置文件中设置的任何{@code environment}。
   * 这仅用作占位符名称。默认值是{@code SqlSessionFactoryBean.class.getSimpleName()}。
   *
   * @param environment：the environment name
   */
  public void setEnvironment(String environment) {
    this.environment = environment;
  }

  /**
   * 设置脚本语言驱动程序。
   *
   * @since 2.0.2
   * @param scriptingLanguageDrivers：scripting language drivers
   */
  public void setScriptingLanguageDrivers(LanguageDriver... scriptingLanguageDrivers) {
    this.scriptingLanguageDrivers = scriptingLanguageDrivers;
  }

  /**
   * 设置默认脚本语言驱动程序类。
   *
   * @param defaultScriptingLanguageDriver：默认的脚本语言驱动程序类
   * @since 2.0.2
   */
  public void setDefaultScriptingLanguageDriver(Class<? extends LanguageDriver> defaultScriptingLanguageDriver) {
    this.defaultScriptingLanguageDriver = defaultScriptingLanguageDriver;
  }

  /**
   * {@inheritDoc} 实现InitializingBean接口方法，构建sqlSessionFactory对象
   */
  @Override
  public void afterPropertiesSet() throws Exception {
    notNull(dataSource, "Property 'dataSource' is required");
    notNull(sqlSessionFactoryBuilder, "Property 'sqlSessionFactoryBuilder' is required");
    state((configuration == null && configLocation == null) || !(configuration != null && configLocation != null),
        "Property 'configuration' and 'configLocation' can not specified with together");

    this.sqlSessionFactory = buildSqlSessionFactory();
  }

  /**
   * 构建一个{@code SqlSessionFactory}实例。
   *
   * 默认实现使用标准MyBatis {@code XMLConfigBuilder} API构建一个基于Reader的{@code SqlSessionFactory}实例。
   * 从1.3.0开始，它可以被直接指定为{@link Configuration}实例(不需要配置文件)。
   *
   * 1、new创建了对象Configuration，这个对象是mybatis框架的一个核心类，在这里我们不做详细介绍，以后再剖析。
   * 2、创建了new SpringManagedTransactionFactory()，后面介绍这个类的作用，此处略过。
   * 3、创建new Environment(this.environment, this.transactionFactory, this.dataSource)，这个Environment类中持有事物工厂和数据源的引用。
   * 4、创建XMLMapperBuilder对象，并且调用了xmlMapperBuilder.parse()方法，这个方法的详细，不在此分析，也不是我们这篇文章要记录的重点，否则会偏离我们的主题，
   * 5、parse()这个方法就是在解析mapperLocation变量所代表的就是mybatis的一个xml配置文件，mapperLocation-->AuthUserMapper.xml。
   *
   * 总结起来，就是创建了几个对象：
   *      a、依次是mybatis的核心类Configuration、
   *      b、spring和mybatis集成的事物工厂类SpringManagedTransactionFactory、
   *      c、mybatis的Environment类、
   *      d、mybatis的DefaultSqlSessionFactory类，
   *      同时还完成了对mybatis的xml文件解析，并将解析结果封装在Configuration类中。
   *
   * @return SqlSessionFactory
   * @throws Exception
   *           如果配置失败
   */
  protected SqlSessionFactory buildSqlSessionFactory() throws Exception {

    final Configuration targetConfiguration;

    /**
     * 如果当前configuration不为空，则直接赋值给targetConfiguration
     *      然后将当前的configurationProperties添加或者赋值给targetConfiguration
     * 如果当前configLocation不为空，则根据configLocation生成targetConfiguration
     * 如果都为空，则生成一个全新的Configuration对象，赋值给targetConfiguration
     */
    XMLConfigBuilder xmlConfigBuilder = null;
    if (this.configuration != null) {
      targetConfiguration = this.configuration;
      if (targetConfiguration.getVariables() == null) {
        targetConfiguration.setVariables(this.configurationProperties);
      } else if (this.configurationProperties != null) {
        targetConfiguration.getVariables().putAll(this.configurationProperties);
      }
    }
    else if (this.configLocation != null) {
      /**
       * 解析mybatis-config.xml 配置文件，生成对应的configuration对象
       */
      xmlConfigBuilder = new XMLConfigBuilder(this.configLocation.getInputStream(), null, this.configurationProperties);
      targetConfiguration = xmlConfigBuilder.getConfiguration();
    } else {
      LOGGER.debug(
          () -> "Property 'configuration' or 'configLocation' not specified, using default MyBatis Configuration");
      targetConfiguration = new Configuration();
      Optional.ofNullable(this.configurationProperties).ifPresent(targetConfiguration::setVariables);
    }

    Optional.ofNullable(this.objectFactory).ifPresent(targetConfiguration::setObjectFactory);
    Optional.ofNullable(this.objectWrapperFactory).ifPresent(targetConfiguration::setObjectWrapperFactory);
    Optional.ofNullable(this.vfs).ifPresent(targetConfiguration::setVfsImpl);

    /**
     * 使用别名的基本包名
     */
    if (hasLength(this.typeAliasesPackage)) {
      scanClasses(this.typeAliasesPackage, this.typeAliasesSuperType).stream()
          .filter(clazz -> !clazz.isAnonymousClass()).filter(clazz -> !clazz.isInterface())
          .filter(clazz -> !clazz.isMemberClass()).forEach(targetConfiguration.getTypeAliasRegistry()::registerAlias);
    }

    /**
     * 使用别名的单个配置
     */
    if (!isEmpty(this.typeAliases)) {
      Stream.of(this.typeAliases).forEach(typeAlias -> {
        targetConfiguration.getTypeAliasRegistry().registerAlias(typeAlias);
        LOGGER.debug(() -> "Registered type alias: '" + typeAlias + "'");
      });
    }

    /**
     * mybatis配置的插件列表
     */
    if (!isEmpty(this.plugins)) {
      Stream.of(this.plugins).forEach(plugin -> {
        targetConfiguration.addInterceptor(plugin);
        LOGGER.debug(() -> "Registered plugin: '" + plugin + "'");
      });
    }

    /**
     * 自定义mybatis的类型处理器的扫描基本包
     */
    if (hasLength(this.typeHandlersPackage)) {
      scanClasses(this.typeHandlersPackage, TypeHandler.class).stream().filter(clazz -> !clazz.isAnonymousClass())
          .filter(clazz -> !clazz.isInterface()).filter(clazz -> !Modifier.isAbstract(clazz.getModifiers()))
          .forEach(targetConfiguration.getTypeHandlerRegistry()::register);
    }

    /**
     * 自定义mybatis的类型处理器的类型列表
     */
    if (!isEmpty(this.typeHandlers)) {
      Stream.of(this.typeHandlers).forEach(typeHandler -> {
        targetConfiguration.getTypeHandlerRegistry().register(typeHandler);
        LOGGER.debug(() -> "Registered type handler: '" + typeHandler + "'");
      });
    }
    /**
     * 使用默认的枚举类型处理器
     */
    targetConfiguration.setDefaultEnumTypeHandler(defaultEnumTypeHandler);

    if (!isEmpty(this.scriptingLanguageDrivers)) {
      Stream.of(this.scriptingLanguageDrivers).forEach(languageDriver -> {
        targetConfiguration.getLanguageRegistry().register(languageDriver);
        LOGGER.debug(() -> "Registered scripting language driver: '" + languageDriver + "'");
      });
    }
    Optional.ofNullable(this.defaultScriptingLanguageDriver)
        .ifPresent(targetConfiguration::setDefaultScriptingLanguage);

    if (this.databaseIdProvider != null) {// fix #64 set databaseId before parse mapper xmls
      try {
        targetConfiguration.setDatabaseId(this.databaseIdProvider.getDatabaseId(this.dataSource));
      } catch (SQLException e) {
        throw new NestedIOException("Failed getting a databaseId", e);
      }
    }

    Optional.ofNullable(this.cache).ifPresent(targetConfiguration::addCache);

    /**
     * 如果xmlConfigBuilder不为空，则说明存在mybatis-config.xml配置文件
     * 对该配置文件进行解析
     */
    if (xmlConfigBuilder != null) {
      try {
        xmlConfigBuilder.parse();
        LOGGER.debug(() -> "Parsed configuration file: '" + this.configLocation + "'");
      } catch (Exception ex) {
        throw new NestedIOException("Failed to parse config resource: " + this.configLocation, ex);
      } finally {
        ErrorContext.instance().reset();
      }
    }

    /**
     * 设置上下文环境变量
     */
    targetConfiguration.setEnvironment(new Environment(this.environment,
        this.transactionFactory == null ? new SpringManagedTransactionFactory() : this.transactionFactory,
        this.dataSource));

    // 解析mybatis的 *mapper.xml 文件，并封装到targetConfiguration中
    if (this.mapperLocations != null) {
      if (this.mapperLocations.length == 0) {
        LOGGER.warn(() -> "Property 'mapperLocations' was specified but matching resources are not found.");
      } else {
        for (Resource mapperLocation : this.mapperLocations) {
          if (mapperLocation == null) {
            continue;
          }
          try {
            /**
             * 用来解析mapper.xml文件
             */
            XMLMapperBuilder xmlMapperBuilder = new XMLMapperBuilder(mapperLocation.getInputStream(),
                targetConfiguration, mapperLocation.toString(), targetConfiguration.getSqlFragments());
            xmlMapperBuilder.parse();
          } catch (Exception e) {
            throw new NestedIOException("Failed to parse mapping resource: '" + mapperLocation + "'", e);
          } finally {
            ErrorContext.instance().reset();
          }
          LOGGER.debug(() -> "Parsed mapper file: '" + mapperLocation + "'");
        }
      }
    } else {
      LOGGER.debug(() -> "Property 'mapperLocations' was not specified.");
    }

    /**
     * 使用构建者模式创建出SqlSessionFactory对象
     */
    return this.sqlSessionFactoryBuilder.build(targetConfiguration);
  }

  /**
   * {@inheritDoc} 实现FactoryBean的接口，返回SqlSessionFactory对象
   */
  @Override
  public SqlSessionFactory getObject() throws Exception {
    /**
     * 如果sqlSessionFactory为空，说明还没进行mapper扫描解析，则先执行Mapper的扫描解析操作
     */
    if (this.sqlSessionFactory == null) {
      afterPropertiesSet();
    }

    return this.sqlSessionFactory;
  }

  /**
   * {@inheritDoc}  实现FactoryBean的接口，返回SqlSessionFactory对象的class类型
   */
  @Override
  public Class<? extends SqlSessionFactory> getObjectType() {
    return this.sqlSessionFactory == null ? SqlSessionFactory.class : this.sqlSessionFactory.getClass();
  }

  /**
   * {@inheritDoc} 判断是否为单例模式
   */
  @Override
  public boolean isSingleton() {
    return true;
  }

  /**
   * {@inheritDoc} 实现ApplicationListener的接口
   */
  @Override
  public void onApplicationEvent(ApplicationEvent event) {
    if (failFast && event instanceof ContextRefreshedEvent) {
      // 检查所有语句是否完成
      this.sqlSessionFactory.getConfiguration().getMappedStatementNames();
    }
  }

  /**
   *
   * @param packagePatterns
   * @param assignableType
   * @return
   * @throws IOException
   */
  private Set<Class<?>> scanClasses(String packagePatterns, Class<?> assignableType) throws IOException {
    Set<Class<?>> classes = new HashSet<>();
    String[] packagePatternArray = tokenizeToStringArray(packagePatterns,
        ConfigurableApplicationContext.CONFIG_LOCATION_DELIMITERS);
    for (String packagePattern : packagePatternArray) {
      Resource[] resources = RESOURCE_PATTERN_RESOLVER.getResources(ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX
          + ClassUtils.convertClassNameToResourcePath(packagePattern) + "/**/*.class");
      for (Resource resource : resources) {
        try {
          ClassMetadata classMetadata = METADATA_READER_FACTORY.getMetadataReader(resource).getClassMetadata();
          Class<?> clazz = Resources.classForName(classMetadata.getClassName());
          if (assignableType == null || assignableType.isAssignableFrom(clazz)) {
            classes.add(clazz);
          }
        } catch (Throwable e) {
          LOGGER.warn(() -> "Cannot load the '" + resource + "'. Cause by " + e.toString());
        }
      }
    }
    return classes;
  }

}
