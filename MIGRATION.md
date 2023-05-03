# Migration Guide : CzechIdM 9.7.x to CzechIdM 10.0.x

## 💡 Introduction

This guide describes the various things that are needed when migrating from CzechIdM version 9.7.x to version 10.
In version 10 were upgraded major devstack dependencies (see list bellow).

The goals of this version:
- Fix known issues with newer versions of currently used third party libraries (e.g. ModelMapper). More than 100 vulnerabilities are removed.
- To be up to date. Some third party libraries cannot be used with our old devstack.
- Remove obsolete deprecated classes and methods.

Why is migration good for you:
- Frontend localization can be changed without refresh is needed.
- Script textarea contains suggestion box (thx to newer library).
- Frontend performance was increased.
- Logger level can be changed by application configuration without restart is needed.

# 🌗 Backend
This chapter describes migration for the backend part of IdM.

> Note for **administrator**: it is needed to read [Infrastructure changes](#infrastructure-changes), [Before upgrade](#before-upgrade) and [Configuration section](#configuration-properties).

> Note for **module developer**: it is needed to read [Update custom module guide](#update-custom-module) and related conceptual and breaking changes.

> Note for **product developer:** it is needed to read it all :).

### 🚀 Upgraded libraries

- Spring Boot ``1.3.8.RELEASE`` => ``2.1.7.RELEASE``
  - Spring ``4.2.8.RELEASE`` => ``5.1.9.RELEASE``
  - Spring Security ``4.0.4.RELEASE`` => ``5.1.6.RELEASE``
  - Spring Data ``1.9.5.RELEASE`` => ``2.1.10.RELEASE``
  - Hibernate ``4.3.11.Final`` => ``5.3.10.Final``
  - Spring Data Rest removed at all
- Flyway ``4.0`` => ``5.2.4``
- Activiti ``5.22.0`` => ``6.0.0``
- Groovy ``2.4.7`` => ``2.5.8``
 - Groovy Sandbox ``1.11`` => ``1.19``
- ModelMapper ``0.7.8`` => ``2.3.5``
- Guava ``18.0`` => ``28.1-jre``
- Swagger ``2.7.0`` => ``2.9.2``
- Forest index ``0.3.0`` => ``1.1.1``
- ... *other minor and third party libraries*.

> Note for developer: We are using openjdk and Tomcat 9 for development.

#### Removed libraries

- **Spring Data Rest** - we are using Spring rest controllers.
- **Joda** time - and related libraries for json and dababase support.
- Spring Boot **Websocket** - websocket support removed.
- **org.codehaus.jackson** - we are usign **com.fasterxml.jackson** only.

## Infrastructure changes

- **Apache Tomcat** application server version 7 is not compatible with CzechIdM 10. Upgrade/reinstall to a [supported version](https://wiki.czechidm.com/devel/documentation/compatibility#apache_tomcat) if you run CzechIdM on this application server.
- **PostgresSQL database** versions pre 9.4 are not compatible with CzechIdM 10. [Upgrade PostgreSQL](https://wiki.czechidm.com/tutorial/adm/postgres_upgrade_tutorial) if you use older versions.

## Before upgrade

- 🟠 Delete all persisted events. You can use:
  - task ``DeleteExecutedEventTaskExecutor``
  - or button on **Entity events** agenda (available for super admin users).
- 🟡 Resolve all workflow approval tasks (complete or cancel by their approvers)
  - New workflow definitions version will be deployed automatically.
  - Old workflow tasks will be alive, but its safer to resolve them before new version is deployed, because it's not guaranteed that they could be resolved afterwards. If you have a testing environment of IdM, you can test if resolving tasks works after upgrade in your environment.
  - Historic tasks and processes will be preserved untouched and their detail will be available.

## Database migration

When the CzechIdM version 10 is started for the first time, it will do an automatic update (as with any 9 version) to the schema by provided Flyway change scripts.

Updated Flyway version requires **PostgresSQL database version >= 9.4** ([supported versions](https://flywaydb.org/documentation/database/postgresql)).

> Note for developer: Default database name configured for the all ``dev`` profiles was renamed to ``bcv_idm_10`` to prevent update (by Flyway) old database on the background - **old database can be used for LTS version 9.7.x development**, so clone database is needed.

## Configuration properties

Based on upgraded libraries we have to add, remove or change configuration properties (mainly related to connection pool, hibernate and quartz).

> Note for **administrator**: Most of these changes are already resolved by default settings in the upgraded product CzechIdM and you don't need to configure them specifically, if IdM is deployed in a standard way. The highlighted lines are important even for standard installations, so make sure to check them.
When you search or set the properties for your project, please note that they could be either in configuration files on the server with IdM, or bundled inside the CzechIdM package, or overriden in the CzechIdM database - see [Application configuration tutorial](https://wiki.czechidm.com/tutorial/adm/application_configuration).

### Added properties

- 🟡 ``spring.datasource.hikari.maximumPoolSize=50`` - **enlarge connection pool size** = the maximum number of active connections from CzechIdM to its database. This property should be revised **for each project**. The size should be configured by task and event thread pool size - should be higher than sum of pool sizes.
  - Check if any property named ``maxPoolSize`` or ``corePoolSize`` is configured in your project. Typically, it isn't, in such case check the number of CPU cores, calculate ``2*CPU+2 + 2*CPU`` and check that it's lower than 50.
  - If you increase the ``maximumPoolSize`` to more than 50 (for more complex projects), make sure it doesn't exceed the number of available connections in PostgreSQL database, which is by default 100 - the property ``max_connections`` in ``/data/pgsql/9.6/data/postgresql.conf`` (the path depends on your OS).
- 🟢 ``spring.jpa.properties.hibernate.jdbc.time_zone=UTC`` - datetime will be persisted in UTC in database.
- 🟢 ``spring.jpa.properties.hibernate.jdbc.lob.non_contextual_creation=true`` - disable warning during the boot, when Hibernate tries to retrieve some meta information from the database.
- 🟢 ``spring.jpa.properties.hibernate.session_factory.interceptor=eu.bcvsolutions.idm.core.model.repository.listener.AuditableInterceptor`` - replaced deprecated Hiberante property.
- 🟢 ``spring.jpa.hibernate.use-new-id-generator-mappings=false`` - Spring boot 2 changed default to ``true``, but we are using ``IDENTITY`` identifier generators for mssql database.
- 🟢 ``spring.servlet.multipart.max-file-size=100MB`` - Spring boot 2 changed property name for file upload size. The original name was ``multipart.max-file-size`` - see below.
- 🟠 ``spring.servlet.multipart.max-request-size=100MB`` - Spring boot 2 changed property name for file upload size.
  - For these properties to work correctly, you have to **update your Tomcat's ``server.xml``**. In this file, locate configuration of HTTP connector (usually on port 8080) and add the ``maxSwallowSize="-1"`` property therein.


### Removed properties

- 🟢 ``spring.jpa.properties.jadira.usertype.autoRegisterUserTypes`` - we are using java time now, so configuration for Joda time is not needed.
- 🟢 ``spring.jpa.properties.hibernate.ejb.interceptor`` - replaced by new property above.
- 🟢 ``multipart.max-file-size=100Mb`` - replaced by new properties above. Check if your project contained this property and if so, decide if you need to have a different value than the product sets by default - 100 MB (usually there is no reason => delete the old property from the properties file).

### Changed properties

🟠 For project, where **CzechIdM version 10.x will be upgraded from any older version, change property ``org.quartz.scheduler.instanceName=schedulerFactoryBean`` in your ``quartz.properties`` file** in your project profiles (typically the file ``/opt/czechidm/etc/quartz-production.properties`` located on the server with IdM) => tasks will be scheduled under previously effective ``schedulerFactoryBean`` instance name and **previously scheduled task will be preserved**. Without this change, different instance name configured in ``quartz.properties`` file would be effective, so you would lose all scheduled tasks and would need to schedule long running tasks again.

Newly installed **CzechIdM version 10.x** will use ``idm-scheduler-instance`` as default and will respect your configuration in ``quartz.properties`` file by your profile, if different instance name is needed. But be aware, tasks need to be rescheduled after any instance name is changes.

> This change is related to [Spring boot upgrade](https://github.com/spring-projects/spring-boot/issues/14243).

## Conceptual changes

- Spring repository queries, which updates audited entity (``@Modifying`` annotation) is prohibited, because audit is skipped this way. Use entity service (find, then update). All product provided repository methods are removed in version 10 (see changelog).

## Breaking changes

- **Joda time library was removed** - all entities, dtos and services use java time now ⇒ api was changed, all places which used joda time have to be refactored (included workflow and groovy scripts). The related issue is with [serialized dtos](#serialized-dtos) in workflow properties and operation result.
- **Activiti** 6 registered new ``formService`` bean usable in workflow definition ⇒ IdM eav service is not available under ``formService`` name any more. New bean alias ``idmFormService`` was created and has to be used in workflows.
- **Hibernate** removed data type ``org.hibernate.type.StringClobType`` - all entities has to be refactored, type ``org.hibernate.type.TextType`` has to be used.
- **Flyway** removed [support](https://flywaydb.org/documentation/database/postgresql) for old PostgreSgl databases lower than version 9.4.
- **Envers** changed api for extending criteria (check ``AuditCriterion`` usage).
- **Mockito** changes behavior for ``any(Class)`` checker - doesn't support ``null`` parameter value now. This is used just in unit tests. Test can be compiled but doesn't work.
- **Spring** data repository api changes:
  - e.g. ``findOne`` renamed to ``findOneById`` and returns ``Optional<E>``,
  - the concept is changed the same way for all methods.

## Update custom module

Due to breaking changes above, custom module requires some refactoring, before it's compatible with CzechIdM version 10. Some refactoring can be done with replaces, but some places has to be changed manually.

### Replaces

> Case sensitive find is expected.

- 🟡 ``org.hibernate.type.StringClobType`` ⇒ ``org.hibernate.type.TextType``
- 🟠 ``new PageRequest(`` ⇒ ``PageRequest.of(``
- 🟠 ``new Sort(`` ⇒ ``Sort.by(``
- 🟠 ``flyway.getTable()`` ⇒ ``flyway.getConfiguration().getTable()`` (a typical occurrence: the class ``<ProjectName>FlywayConfig``)
- 🟠 ``import org.joda.time.LocalDate;`` ⇒ ``import java.time.LocalDate;``
- 🟠 ``import org.joda.time.DateTime;`` ⇒ ``import java.time.ZonedDateTime;``
- 🟠 ``new DateTime()`` ⇒ ``ZonedDateTime.now()``
- 🟠 ``new LocalDate() ⇒ LocalDate.now()``
- 🟠 ``import org.joda.time.format.DateTimeFormatter;`` ⇒ ``import java.time.format.DateTimeFormatter;``
- 🟠 ``DateTimeFormat.forPattern(`` ⇒ ``DateTimeFormatter.ofPattern(``

### Manual changes / cookbook

- *Replaces above are expected*
- 🟠 **Java time** usage:
  - Try to find (whole word, case sensitive): ``DateTime`` and replace to ``ZonedDateTime`` - some constructors usage has to be refactored manually, see below.
  - **date.getMilis()** ⇒ **ZonedDateTime.now().toInstant().toEpochMilli()**
  - ``.print(`` ⇒ ``.format(``
  - **date.toString(pattern)** ⇒ **date.format(formatter)**
    - Try to search `.toString(` and replace (**ONLY** there is parameter **DateTimeFormatter**) with `.format(`.
    - If value contains pattern in the String (for example "yyyyMMddHHmmss"). You need to change `toString("yyyyMMddHHmmss")` ⇒ `.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))`.
  - **formatter.parse** methods ⇒ **LocalDate.parse(** or **LocalDateTime.parse(**. You must also use an **additional parameter** with DateTimeFormatter. It's recommended to create it by DateTimeFormatterBuilder if the number of milliseconds is not fixed - check the [tutorial](https://wiki.czechidm.com/tutorial/dev/groovy_scripts/converstringtolocaldate)
  - **date.plusMilis(1)** ⇒ **date.plus(1, ChronoUnit.MILLIS)**
    - Try to search `.plusMilis(1)` and replace with `.plus(1, ChronoUnit.MILLIS)`.
  - **Date date = Date.from(ZonedDateTime.now().toInstant());**
  - **ZonedDateTime date = ZonedDateTime.ofInstant(Instant.ofEpochMilli(longValue), ZoneId.systemDefault());**
  - **ChronoUnit.SECONDS.between(authenticationDto.getExpiration(), newExpiration);**
- 🟡 **Mockito**:
  - ``any(String.class)`` checker doesn't support ``null`` parameter value now ⇒ ``(String) any()`` can be used.
- 🟡 **Spring**:
  - data repository changes api - ``findOne`` renamed to ``findOneById`` and returns ``Optional<E>`` now.
  - ``@Service`` annotation cannot be used for the integration test itself ⇒ ``applicationContext.getBean(this.getClass)`` doesn't work in integration tests ⇒ has to be refactored to ``applicationContext.getAutowireCapableBeanFactory().createBean(this.getClass())`` - new instance is created, but can be overlooked in tests :).
  - ``@Service`` and ``@Component`` constructors was simplified - some constructor parameters was moved to ``@Autowired`` fields. If you are overriding service from IdM implementation package (``core-impl``), then update constructor usage.
  - if your project implements own DTOs with annotations e.g. ``@NotEmpty``, check their package - ``javax.validation.constraints`` should be used. Example: ``org.hibernate.validator.constraints.NotEmpty`` ⇒ ``javax.validation.constraints.NotNull``
- 🟡 **Workflow**:
  - find ``formService`` usage in workflow definitions and replace it with ``idmFormService ``.
  - check your workflow definitions. Mainly **prevent joda time usage** - replaces above should help to find all places, which have to be refactored.
- 🟠 **Groovy scripts**:
  - check your groovy scripts. Mainly **prevent joda time usage** (e.g. in transformation scripts) and replace usage with java time.
  - make sure to **redeploy** the changed Groovy scripts after you start new version of IdM in your environment, because Groovy scripts are not updated automatically. This will read new versions of Groovy scripts from your custom module and from product modules and upload them to IdM.
  - Java time classes were added to global script authorities (``LocalDate``, ``ZonedDateTime``, ``ZoneId``, ``OffsetTime``, ``OffsetDateTime``, ``LocalDateTime``, ``LocalTime``, ``Instant``, ``DateTimeFormatter``), so you don't need to add these classes to Script authorities for every Groovy script anymore.
  - Joda time classes could be still used as input for extended attribute value (``IdmFormValueDto#setValue``) => will be converted automatically to java time (``IdmFormValueDto#getValue`` returns java time only).
- 🟡 **fix warnings**:
  - ``Assert`` - add message (e.g. ``Assert.notNull(dto) ⇒ Assert.notNull(dto, "DTO cannot be null!")``)
  - ``IOUtils.closeQuietly`` - refactor with ``try-with-resources`` syntax.
  - ``class.newInstance()`` is deprecated - use ``class.getDeclaredConstructor().newInstance()`` - ``ReflectiveOperationException`` can be catched if needed.
- 🟡 Configure [logback](#logback-configuration) for tests.
- 🟡 Configure [application properties](#test-profile-properties) for tests.
- 🟡 Check usage of [DataSource](#integration-tests-using-datasource) in the tests.
- 🟡 [Run tests](#run-the-integration-tests-in-the-custom-module) (all green)
- Run application, test rest api or continue with frontend upgrade.


#### Serialized dtos

Serialized dtos are persisted in database - used in workflow properties or for operation results. After changing the data type from **Joda** to java time for dto fields, the persisted serialized stream for this dto is obsolete and cannot be deserialized out of box (=> data type is changed).
We need to provide custom deserialization method (see ``AbstractDto#readObject(ObjectInputStream)``) for dto, which uses Joda ``DataTime`` or ``LocalDate`` field and is used as workflow property or as operation result.

Example (from ``IdmConceptRoleRequestDto``):

```java
  /**
	 * DTO are serialized in WF and embedded objects.
	 * We need to solve legacy issues with joda (old) vs. java time (new) usage.
	 *
	 * @param ois
	 * @throws Exception
	 */
	private void readObject(ObjectInputStream ois) throws Exception {
		GetField readFields = ois.readFields();
		//
		roleRequest = (UUID) readFields.get("roleRequest", null);
	    identityContract = (UUID) readFields.get("identityContract", null);
	    contractPosition = (UUID) readFields.get("contractPosition", null);
	    role = (UUID) readFields.get("role", null);
	    identityRole = (UUID) readFields.get("identityRole", null);
	    roleTreeNode = (UUID) readFields.get("roleTreeNode", null);
	    validFrom = DtoUtils.toLocalDate(readFields.get("validFrom", null));
	    validTill = DtoUtils.toLocalDate(readFields.get("validTill", null));
	    operation = (ConceptRoleRequestOperation) readFields.get("operation", null);
	    state = (RoleRequestState) readFields.get("state", null);
	    wfProcessId = (String) readFields.get("wfProcessId", null);
	    log = (String) readFields.get("log", null);
	    valid = readFields.get("valid", false);
	    duplicate = (Boolean) readFields.get("duplicate", null);
	    systemState = (OperationResultDto) readFields.get("systemState", null);
    }
```

> Note for developer: serialized custom dto is most probably not persisted into database in custom module.

> Note for developer: persistent serialized properties are used for asynchronous event processing to, but its required to remove old events [before version 10](#before-upgrade) is installed.

#### Logback configuration

Configuration **file in test package ``logback-test.xml`` has to be changed**. New ``src/test/resource/logback-test.xml`` content:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!-- https://springframework.guru/using-logback-spring-boot/ -->
<!-- http://logback.qos.ch/manual/appenders.html -->
<!DOCTYPE configuration>
<configuration>

	<conversionRule conversionWord="clr" converterClass="org.springframework.boot.logging.logback.ColorConverter" />
	<conversionRule conversionWord="wex" converterClass="org.springframework.boot.logging.logback.WhitespaceThrowableProxyConverter" />
	<conversionRule conversionWord="wEx" converterClass="org.springframework.boot.logging.logback.ExtendedWhitespaceThrowableProxyConverter" />
	<property name="CONSOLE_LOG_PATTERN" value="${CONSOLE_LOG_PATTERN:-%clr(%d{${LOG_DATEFORMAT_PATTERN:-yyyy-MM-dd HH:mm:ss.SSS}}){faint} %clr(${LOG_LEVEL_PATTERN:-%5p}) %clr(${PID:- }){magenta} %clr(---){faint} %clr([%15.15t]){faint} %clr(%-40.40logger{39}){cyan} %clr(:){faint} %m%n${LOG_EXCEPTION_CONVERSION_WORD:-%wEx}}"/>
	<property name="FILE_LOG_PATTERN" value="${FILE_LOG_PATTERN:-%d{${LOG_DATEFORMAT_PATTERN:-yyyy-MM-dd HH:mm:ss.SSS}} ${LOG_LEVEL_PATTERN:-%5p} ${PID:- } --- [%t] %-40.40logger{39} : %m%n${LOG_EXCEPTION_CONVERSION_WORD:-%wEx}}"/>


	<appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
		<encoder>
			<pattern>${CONSOLE_LOG_PATTERN}</pattern>
		</encoder>
	</appender>

	<root level="WARN">
		<appender-ref ref="CONSOLE" />
	</root>

  <logger name="eu.bcvsolutions" level="ERROR"/>

</configuration>
```

> Note for developer: every custom module has ``logback-test.xml`` and this file has to be changed. Test cannot run without this change, you would get the following exception: ``java.lang.NoSuchMethodError: ch.qos.logback.core.rolling.SizeAndTimeBasedFNATP...``

#### Test profile properties

Configuration file in test package ``application.properties`` has to be **created/updated** (mainly jpa properties changed), use content (copy / paste) below.

Note that a typical module already has a configuration file ``application-test.properties``. This file should be kept in the module unchanged, because it typically disables asynchronous events and many integration tests depend on this.

```
######
## Test properties only - its needed here for tests in other modules...
######
#
#
#
# https://docs.spring.io/spring-boot/docs/1.3.8.RELEASE/reference/html/common-application-properties.html
#
# active spring profile
spring.profiles.active=${spring.profiles.active}
spring.resources.add-mappings=false
#
# jpa
spring.jpa.properties.org.hibernate.envers.audit_table_suffix=_a
# ZonedDateTime is stored in UTC
spring.jpa.properties.hibernate.jdbc.time_zone=UTC
# Driver (e.g. postgres) does not support contextual LOB creation
spring.jpa.properties.hibernate.jdbc.lob.non_contextual_creation=true
# connection pool setting
# https://github.com/brettwooldridge/HikariCP#configuration-knobs-baby
spring.datasource.hikari.maximumPoolSize=25
# flag with added mod columns to all attributes
# spring.jpa.properties.org.hibernate.envers.global_with_modified_flag=true
spring.jpa.properties.hibernate.session_factory.interceptor=eu.bcvsolutions.idm.core.model.repository.listener.AuditableInterceptor
spring.jpa.properties.hibernate.listeners.envers.autoRegister=true
spring.jpa.hibernate.use-new-id-generator-mappings=false
#
# Spring Data Rest basic configuration
# http://docs.spring.io/spring-data/rest/docs/current/reference/html/#_changing_other_spring_data_rest_properties
spring.data.rest.basePath=/api
spring.data.rest.returnBodyOnCreate=true
spring.data.rest.returnBodyOnupdate=true
spring.data.rest.defaultPageSize=10
#
# String boot properties for Activiti workflow engine
# https://github.com/Activiti/Activiti/blob/master/modules/activiti-spring-boot/spring-boot-starters/activiti-spring-boot-starter-basic/src/main/java/org/activiti/spring/boot/ActivitiProperties.java
spring.activiti.databaseSchemaUpdate=true
# disable automatic jpa entities persisting
spring.activiti.jpaEnabled=false
# Automatic process deployment
spring.activiti.checkProcessDefinitions=true
spring.activiti.processDefinitionLocationPrefix=classpath*:eu/bcvsolutions/idm/workflow/
spring.activiti.processDefinitionLocationSuffixes=**/**.bpmn20.xml
#
# Check that the templates location exists is disabled not
spring.velocity.checkTemplateLocation=false
spring.velocity.resourceLoaderPath=classpath*:eu/bcvsolutions/idm/templates/
#
# Swagger config
# doc endpoint
springfox.documentation.swagger.enabled=true
springfox.documentation.swagger.v2.path=/api/doc
#
# Cipher secret key for crypt values in confidential storage
# for crypt values is used secretKey or secretKey defined by file - secretKeyPath
# Can be empty => confidential storage will not be crypted, application cannot be used in production (dev, test only).
cipher.crypt.secret.key=
# cipher.crypt.secret.keyPath=/path/to/key
#
# use cglib for proxies by default
spring.aop.proxy-target-class=true
#
# Application instance / server id
idm.pub.app.instanceId=idm-primary
#
# Enable flyway migrations.
# @see https://proj.bcvsolutions.eu/ngidm/doku.php?id=navrh:databazove_scripty
flyway.enabled=false
#
# Scheduler
scheduler.enabled=true
scheduler.task.queue.process=1000
scheduler.event.queue.process=500
scheduler.event.executor.queueCapacity=50
#
# Asynchronous event processing
# disable / enable asynchronous event processing. Events will be executed synchronously, if it's disabled. Enabled by default.
idm.sec.core.event.asynchronous.enabled=true
# Asynchronous events will be executed on server instance with id. Default is the same as {@link ConfigurationService#getInstanceId()} (current server instance).
idm.sec.core.event.asynchronous.instanceId=
# Asynchronous events will be executed in batch - batch will be split for event with HIGH / NORMAL priority in 70% HIGH / 30% NORMAL.
# If you events are processed quickly (~provisioning on your environment is quick), then batch size can be higher (in combination with higher 'scheduler.event.queue.process' property).
idm.sec.core.event.asynchronous.batchSize=15
#
idm.pub.core.version=${project.version}
# supports delete identity
idm.pub.core.identity.delete=true
#
# default password change type for custom users, one of values (get from this enum: PasswordChangeType):
# DISABLED - password change is disable
# ALL_ONLY - users can change passwords only for all accounts
# CUSTOM - users can choose for which accounts change password
idm.pub.core.identity.passwordChange=ALL_ONLY
#
# supports authorization policies for extended form definitions and their values
idm.sec.core.identity.formAttributes.secured=false
#
# Properties for load template from templates folders by modules
idm.sec.core.notification.template.folder=classpath*:/eu/bcvsolutions/idm/templates/
idm.sec.core.notification.template.fileSuffix=**/**.xml
#
# Properties for load script from folders by module
idm.sec.core.script.folder=classpath*:/eu/bcvsolutions/idm/scripts/
idm.sec.core.script.fileSuffix=**/**.xml
#
# Approve by manager
idm.sec.core.wf.approval.manager.enabled=false
# Approve by security department
idm.sec.core.wf.approval.security.enabled=false
idm.sec.core.wf.approval.security.role=Security
# Approve by helpdesk department
idm.sec.core.wf.approval.helpdesk.enabled=false
idm.sec.core.wf.approval.helpdesk.role=Helpdesk
# Approve by usermanager department
idm.sec.core.wf.approval.usermanager.enabled=false
idm.sec.core.wf.approval.usermanager.role=Usermanager
# Approval wf by role priority
idm.sec.core.wf.role.approval.1=approve-role-by-manager
idm.sec.core.wf.role.approval.2=approve-role-by-guarantee
idm.sec.core.wf.role.approval.3=approve-role-by-guarantee-security
# Approval wf for unassign role (one remove WF for whole application)
idm.sec.core.wf.role.approval.remove=approve-remove-role-by-manager
# Enable sending notification of changing roles to user, whose account will be modified
# idm.sec.core.wf.notification.applicant.enabled=false
# Enable sending notification of changing roles to user, who made request
idm.sec.core.wf.notification.implementer.enabled=true
#
# configuration property for default backup
# idm.sec.core.backups.default.folder.path=/tmp/backup
#
## Attachment manager
# attachments will be stored under this path.
# new directories for attachment will be created in this folder (permissions has to be added)
# System.getProperty("user.home")/idm_data will be used if no path is given
# idm.sec.core.attachment.storagePath=/opt/data
# temporary files for attachment processing (e.g. temp files for download / upload)
# getStoragePath()/temp will be used if no path is given
# idm.sec.core.attachment.tempPath=/opt/data/temp
# temporary file time to live in milliseconds
# older temporary files will be purged, default 14 days
idm.sec.core.attachment.tempTtl=1209600000
#
# Max file size of uploaded file. Values can use the suffixed "MB" or "KB" to indicate a Megabyte or Kilobyte size.
multipart.max-file-size=1Mb
```

#### Integration tests using DataSource

Check if some tests use ``org.apache.tomcat.jdbc.pool.DataSource`` when creating a system and using provisioning/synchronization.

Replace ``org.apache.tomcat.jdbc.pool.DataSource tomcatDataSource = ((org.apache.tomcat.jdbc.pool.DataSource) dataSource);`` to ``HikariDataSource tomcatDataSource = ((HikariDataSource) dataSource);``

If you use the method ``.getUrl()``, replace it to ``getJdbcUrl()``, and ``getPoolProperties().getPassword()`` to ``getPassword()``.

#### Run the integration tests in the custom module

The module should have 3 files in the folder ``src/test/resources``:
* application.properties (the one added in the step above)
* application-test.properties (the original file)
* logback-test.xml

Run the tests with VM arguments: ``-Dspring.profiles.active=test`` (profile).

>New versions of IDE Eclipse may have issues when running the tests with the Test runner JUnit 5 ⇒ change it to JUnit 4.

# 🌓 Frontend
This chapter describes migration for the frontend part of IdM.

The main goal of upgrading the frontend in **version 10** was to upgrade **React** to version **16**. Previous version **15** has already limited us in selecting new and upgrading existing components.

>Some refactoring can be done with replaces (marks as **Replace all occurrences**), but some places has to be changed manually.

>Case sensitive find is expected.

### 🚀 Main upgraded libraries:

* **React** from **15.6** to **16.9**.
* **React-router** from **2.3** to **5.1**.
* **React-redux** from **4.4** to **7.1**.
* **Redux** from **3.5** to **4.0**.
* **Redux-immutable** from **1.3** to **4.0**.
* **React-ace** from **3.7** to **7.0**.
* **Browserify** from **13.0** to **16.5**.
* And next more then 20 minor updates to dependent components.

> All updated dependencies are in the product package.json. It means, If you will used the CzechIdM 10.x.x, then you already have dependency on the **React 16** and others.

## React since version 16 has PropTypes in separate module

Use official conversion utility for move PropTypes in you module.

<code>npx react-codemod React-PropTypes-to-prop-types --force</code>

## Method componentWillReceiveProps and componentWillMount
Method **componentWillReceiveProps** and **componentWillMount** were renamed to **UNSAFE_componentWillReceiveProps** and **UNSAFE_componentWillMount**. Since **React 17** only UNSAFE variant will be called!

This **methods are deprecated** and should be not used. In the product was this method componentWillReceiveProps used in 60 files. In version 10 was this method removed from 30 files (typically from component shows a detail).

Removal of this method was possible thanks to a new approach to rendering individual routed components.
In other words, the **componentWillReceiveProps** method was used in these places to detect changes in properies. For example, if the user name identity has changed in the url. If this change was detected, the identity was read again and the detail was initialized (sets to the state).

However, since **version 10**, if the url is changed, the routed component is destroyed and recreated. Therefore, the **componentWillReceiveProps** method is no longer needed.

> If you don't want remove UNSAFE_componentWillReceiveProps or UNSAFE_componentWillMount you don't need to. Only what you have to need is rename this methods (add UNSAFE_).

Use official conversion utility for rename deprecated methods.

<code>npx react-codemod rename-unsafe-lifecycles --force</code>

## React TestUtils was moved to react-dom

**Replace all occurrences**:

<code>import TestUtils from 'react-addons-test-utils';</code>
⇒
 <code>import TestUtils from 'react-dom/test-utils';</code>

## React Shallow renderer

Tests using Shallow renderer from the **react-test-renderer** now.

**Replace all occurrences**:

<code>TestUtils.createRenderer();</code>
⇒
 <code>new ShallowRenderer();</code>

 You have to add import to a modified files:

 <code>import ShallowRenderer from 'react-test-renderer/shallow';</code>.

## React-redux - forwardRef

In new version of Redux, is returned wrapped component directly (component.getWrappedInstance() is not necessary). For this feature you have to define  **forwardRef: true** in redux connect function.

**Replace all occurrences**:

<code>withRef: true</code>
⇒
 <code>forwardRef: true</code>

## React-redux - remove getWrappedInstance

Method **getWrappedInstance()** on a redux components no longer exists, because is no necessary (if **forwardRef** is used).

**Replace all occurrences**:

<code>.getWrappedInstance().</code>
⇒
 <code>.</code>

## React router - Link
React router components were moved to the module **react-router-dom**.

**Replace all occurrences**:

<code>import { Link } from 'react-router';</code>
⇒
 <code>import { Link } from 'react-router-dom';</code>

## React router - Context
In new version of the React Router is router context no longer propagate.

>We used router context for redirection (methods **replace**/**push**/**goBack**) in previous verson of CzechIdM.
For replace it was in CzechIdM 10 created **IdmContext**. This context contains all **routes**, redux **store** and router **history**.

You have to use **this.context.history** for redirection (replace/push/goBack).

**Replace all occurrences**:

<code>this.context.router</code>
⇒
 <code>this.context.history</code>

## React router - URL params
Url parameters are no longer in **props.params**, but in match object. Use **props.match.params** instead.

**Replace all occurrences (in the same order)**:

<code>component.params</code>
⇒
 <code>component.match.params</code>

<code>this.props.params</code>
 ⇒
  <code>this.props.match.params</code>

<code>params={ this.props.match.params }</code>
 ⇒
  <code>match={ this.props.match }</code>

<code>params={this.props.match.params}</code>
 ⇒
  <code>match={ this.props.match }</code>

<code>props.params</code>
 ⇒
  <code>props.match.params</code>

<code>nextProps.params</code>
 ⇒
  <code>nextProps.match.params</code>

## React router - Dynamic routes
In **React Router v4** are **routes.js** no longer use. Every parent component must know all his children now.

**This broked our modularity**, because we compose routes from every modules dynamically. It means parent component doesn't know about his children routes in CzechIdM.  

In **CzechIdM 10** was developed mechanism for generating **<Route>** elements by configuration from a **routes.js**.

**This was the biggest task in upgrade the frontend.**

>All components in CzechIdM extends **AbstractContextComponent**. In this class was implemented dynamic routing. It means here are used old routes (composed from all routes.js) and generated new `<Route>` for React Router V4. All what you need is call **this.getRoutes()** in every places where routes children (**this.props.children**) were rendered (in Routes V4 will be **this.props.children** empty).

Definition of routes in all modules shouldn't need any update. But definition of a routes can be written in many ways and it means some of these cases have to be not covered by dynamic routing in CzechIdM 10.

**So beware and test  all routing/redirections in your module after upgrade on CzechIdM 10!**

**!!ONLY!!** for components using **Advanced.TabPanel** make replace:

<code>this.props.children</code>
⇒
 <code>this.getRoutes()</code>

## React router - redirections

If you using redirect via **replace** or **push** method, then you have to ensure correct format of the path. Previouse version of react-router used absolute path only. **This is not true now**!

So, if you have this redirection:

`this.context.history.replace('systems');`

, then final url will be `http://localhost:3000/#/currentUrl/systems`!

If you want to use **absolute path** `http://localhost:3000/#/systems`, you have to add the slash:

 `this.context.history.replace('/systems');`!

**Suggestion is try to search** (`this.context.history.push(`, `this.context.history.replace(`) and check if all useing of **replace**/**push** starts with **slash**.
>Replace all is not good idea, because in some cases can starts expression with call a method.

## React router - redirections with `to`
Same situation as previous occurs for `<Basic.LinkCell>` and `<Advanced.ColumnLink>` components, where attribute **to** is no longer absolute. So you need to add slash (if missing on the start of path).

**Suggestion is try to search** (`to="`) and check if all useing starts with slash.
> Replace all is not good idea, because in some cases can starts expression with call a method.

**Example:**

 `<Basic.LinkCell property="key" to="workflow/definitions/:key"/>`

 ⇒

 `<Basic.LinkCell property="key" to="/workflow/definitions/:key"/>`

## React router - query parameters
 **React router since V4 doesn't parse the query parameters** (`.../?new=1`).

In CzechIdM 10 was implemented parsing in **AbstractContextComponent._parseUrlQuery**.
Parsing is executed in constructor and result query object is sets to the `this.props.location.query` (for ensure back compatibility).

**Beware** `this.props.location.query` could be **null**!

Because Redux calling **Select** method before constructor of **AbstractContextComponent**. You have to prevent of throwing of the **null pointer**, typicaly in select method.

**For example**:

 `component.location ? component.location.query.automaticRoleId` should be modified on `component.location && component.location.query ? component.location.query.automaticRoleId : null;`

**Suggestion is try to search** (`location.query`) and check using (ensure prevent of null pointer ) in redux select methods.

## Method checkAccess removed
Method **checkAccess** in the SecurityManager was removed. This logic was moved to the `AbstractContextComponent._getComponent(route)`.


## 🐞 React bootstrap - Modal component bug

**Modal** component from react-bootstrap contains bug! Modal dialog is show before subcomponents are rendered. So if you call **setState** for show a modal dialog and in callback you want to set focus on some component (`this.refs.firstName.focus()`), then you will obtain **exception**, because the ref for that component will be null.

I found ugly but working solution, it is the timeout (10ms). This timeout is implemented in **AbstractTableContent.showDetail**. It means, if you use this method, your modal dialog should be correctly rendered. If you showing modal dialog **manually**, then **you have to add timeout or use method showDialog**.

**Here is example:**

_Not working:_
<pre><code class="text">
  this.setState({
      detail: {
        show: true,
        entity
      }
    }, () => {
      this.refs.generatorType.focus();
    });
</code></pre>

_Working (using showDetail from AbstractTableContent):_
<pre><code class="text">
  super.showDetail(entity, () => {
      this.refs.generatorType.focus();
    });
</code></pre>

_Working (manually added timeout):_
<pre><code class="text">
  this.setState({
      detail: {
        show: true,
        entity
      }
    }, () => {
      setTimeout(() => {
        this.refs.generatorType.focus();
      }, 10);
    });
</code></pre>

# Migration Guide : CzechIdM 12.2.x to CzechIdM 13.0.x

## 💡 Introduction

This guide describes the various things that are needed when migrating from CzechIdM version 12.2.x to version 13.
Version 13 brings changes to role assignments which are not backward compatible.

The changes that are needed to run CzechIdM 13 are only relevant if you use a custom module.

# 🌗 Backend

## Breaking changes

- **Role assignment changed** - there were API changes made to role assignment which require changes in your custom module. Roles can now be assigned to other objects - in CzechIdM 13, this means accounts.
- **Registrable system entity types** - system entity types are no longer represented just as a static enum but can now be registered in a custom module.

## Update custom module

Due to breaking changes above, custom module requires some refactoring, before it's compatible with CzechIdM version 13. Some refactoring can be done with replaces, but some places need to be changed manually.

### Replaces

> Case sensitive find is expected.

- 🟠 ``SystemEntityType.IDENTITY`` ⇒ ``IdentitySynchronizationExecutor.SYSTEM_ENTITY_TYPE``
- 🟠 ``SystemEntityType.CONTRACT`` ⇒ ``ContractSynchronizationExecutor.SYSTEM_ENTITY_TYPE``
- 🟠 ``SystemEntityType.ROLE`` ⇒ ``RoleSynchronizationExecutor.SYSTEM_ENTITY_TYPE``
- 🟠 ``SystemEntityType.TREE`` ⇒ ``TreeSynchronizationExecutor.SYSTEM_ENTITY_TYPE``
- 🟠 ``SystemEntityType.ROLE_CATALOGUE`` ⇒ ``RoleCatalogueSynchronizationExecutor.SYSTEM_ENTITY_TYPE``
- 🟠 ``SystemEntityType.CONTRACT_SLICE`` ⇒ ``ContractSliceSynchronizationExecutor.SYSTEM_ENTITY_TYPE``
- 🟠 ``SystemEntityType.IDENTITY_ROLE`` ⇒ ``IdentityRoleSynchronizationExecutor.SYSTEM_ENTITY_TYPE``

### Manual changes / cookbook

- *Replaces above are expected*
- 🟠 **Account type** usage:
  - Account type is now a field of mapping, not of account itself. Remove setting the account type from account and set it for mapping.
- 🟠 **Concept role request API** usage:
  - Due to the added possibility to assign role to object other than the identity, concept role requests can now exist for different entities as well. This requires some replaces:
  - Example:
  ```java
    @Autowired private IdmConceptRoleRequestService conceptRoleRequestService;
    //
    List<IdmConceptRoleRequestDto> concepts = request.getConceptRoles();
    for (IdmConceptRoleRequestDto concept : concepts) {
      concept.setRoleRequest(request.getId());		
      conceptRoleRequestService.save(concept);
    }
  ```
  needs to be changed to
  
  ```java
    @Autowired private IdmConceptRoleRequestManager conceptRoleRequestManager;				
    //
    List<AbstractConceptRoleRequestDto> concepts = request.getConceptRoles();
    for (AbstractConceptRoleRequestDto concept : concepts) {
      concept.setRoleRequest(request.getId());
      conceptRoleRequestManager.getServiceForConcept(concept).save(concept);
    }
  ```
### Changes to role assignment

Starting with CzechIdM 13, roles can be assigned to objects other than contracts, namely, accounts.

In general, the code you use in modules to work with role assignments should be backward compatible. However, without further changes, it will only work with identity roles (contract role assignments). If you want it to take other role assignments into consideration, you need to replace the specific services, DTOs, and filters for their abstract classes. For example:

- ``IdmIdentityRoleDto`` ⇒ ``AbstractRoleAssignmentDto``
- ``IdmIdentityRoleService`` ⇒ ``IdmRoleAssignmentService``
- ...

Keep in mind, however, that this is major change in the business logic and that it may well not be necessary.
	
# 🌗 Database

There was a minor change in the database structure concerning where account type is saved. Starting with 13.0.0, an account is expected to have a link to a provisioning mapping. Since updating a large table could take some time, the following script is not run automatically. To update the table, you can either recalculate accounts via a bulk action, or run the following SQL query:
	
```sql
update acc_account to_update
set system_mapping_id = (select ssm.id
                         from acc_account aa
                                  join sys_system ss on aa.system_id = ss.id
                                  join sys_schema_obj_class oc on oc.system_id = ss.id
                                  join sys_system_mapping ssm on ssm.object_class_id = oc.id
                         where aa.id = to_update.id and ssm.operation_type = 'PROVISIONING' and aa.entity_type = ssm.entity_type);
```
