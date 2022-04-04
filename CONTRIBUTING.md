# Contributing to CzechIdM
We want to make contributing to this project as easy and transparent as possible.

## Issues
We use [Redmine](https://redmine.czechidm.com) to track bugs and enhancements.

### Bugs

If you believe that you have found a bug, please take a moment to search the existing issues. If no one else has reported the problem, please open a new ticket that describes the problem in detail and has sufficient category and instructions to be able to reproduce the issue. Ideally, one which describes how to reproduce it. Application, module (backend, frontend) and browser version has to be included in ticket description.

**Please dont forget write affected version**. If you want help with solving bug, we primary need know what version do you use. Without affected version we are not able solve the ticket and ticket will be returned to you.

#### Strict ticket example:
| Name form attribute   |      Values/Info      |
|----------|:-------------:|
| Queue |  Task, Defect, Feature |
| Subject |    Clear, unambiguous and unique name, **don't create duplicate tickets please!** |
| Description | **Detailed description**, bug or feature, **with affected version**. Description of how to reproduce bug, preferably StackTrace with error. Info as to why this new feature is necessary. |
| State | Please keep state as New |
| Priority | Only critical bugs have higher priority than normal. For features, never set priority higher than normal. Features are planned far in advance. It is possible that your feature will be appear in a later version. |
| Assigned | If you don't know who is responsible for a category don't choose anyone. |
| Category | Category which best matches your problem. |
| Target version | If you don't know what version is needed, don't choose any version. |
| Files | Please add some screens, your specific code and **tests** with bug. |
| Parent ticket, from, till, estimated time, done % | Please do not fill in these attributes. |

Feel free to add any other additional info.

### Enhancements

If you’d like an enhancement to be made to CzechIdM, pull requests are most welcome. The source code is on GitHub. You may want to search the existing issues and pull requests to see if a similar enhancement is already being worked on. You may also want to open a new issue to discuss a possible enhancement before work begins on it.

## Backend

### Coding Style, convention

* https://google.github.io/styleguide/javaguide.html
* 1 tab for indentation
* Use `{}` brackets
* Don't use abbreviations for fields, variables etc.
* Don't use camelCase in package names
* Configuration
  * Use ``Configurable`` interface for configurations.
  *  If the name of a configuration item contains the ``password`` or ``token`` chain, the value of the configuration item is hidden in the rest interface listing (or rather replaced with substitute characters).
  * It is better to use constants for configuration properties (keys), e.g. ``ConfigurationService.IDM_PUBLIC_PROPERTY_PREFIX + "core.identity.delete"`` - using separator ot other constant in key name is not preferred - configuration property can be simply found by key suffix (``"ctrl-f" + "core.identity.delete"``). Another benefit - key is readable without finding other constant or string concatiation in code.
  * Configuration properties (constants in code) should start with ``PROPERTY_`` prefix.
  * Registrable component parameters configurable by eav form (constants in code) should start with ``PARAMETER_`` prefix.
* Database
  * [Convention](https://wiki.czechidm.com/devel/documentation/conventions/dev/database-conventions)
  * `Entity` / `Dto` has to contain jsr303 validations
  * Entity names in singular
* Rest
  * Rest endpoints naming with snake-case lowercase in plural, e.g. `<server>/wf-identities`, `<server>/tasks`
* Spring
  * use bean names, e.g. `@Service("identityService")`
  * use ``@Component`` for registrable components (e.g. long running tasks, processors, reports), use ``@Service`` for services and managers
  * use `@Transaction` annotation for service methods
  * use interfaces
    * ``Configurable`` for configurations
    * ``Codeable`` for entities with code and name - see database naming [convention](https://wiki.czechidm.com/devel/documentation/conventions/dev/database-conventions)
    * ...
* Java
  * Class - fields first, then constructors, then public methods, then private methods
  * ``final`` modifier only there, when is needed (its discutable, but we are using it this way)
  * keep formatting like author (author = by added source code amount in class) - e.g. inline @Autocomplete, stream formatting
* Modules
  * Spring bean names, component names, services, entities etc. should start with module identifier prefix.
  * Registrable component bean names (e.g. ``AuthorizationEvaluator``, ``FilterBuilder``, ``IdmAuthenticationFilter`` ...) should start with module prefix ``core-sso-authentication-filter``. This name could be used as bean name in Spring context.
  * Use interfaces and classes from `idm-core-api` module dependency. If some API is missing (its only in `idm-core-impl`), contact us. Only exception is entity + jpa metamodel usage.
  * Rest endpoints should start with module identifier prefix e.g. <server>/api/v1/crt/certificates.
  * When backend bulk actions are supported, then ``DataFilter`` (with data usage only) has to be used in underlying service (enable bulk action serialization and deserialization from database).


### IDE
* [Eclipse](https://wiki.czechidm.com/7.3/dev/quickstart/ide/eclipse)
* [Idea](https://wiki.czechidm.com/7.3/dev/quickstart/ide/idea)

## Frontend

### Coding Style, convention

* ES6
  * use classes
  * use arrow functions (`.bind(this)` is not needed)
  * use `import` - import third party libraries
  * https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array
  * Immutable
  * ...
* Use semicolons;
* Use `{}` brackets
* Commas last,
* 2 spaces for indentation
* Prefer `'` over `"`
* 150 character line length
* Do not use the optional parameters of `setTimeout` and `setInterval`
* Use JSDoc for documentation (`@author` etc.) https://developers.google.com/closure/compiler/docs/js-for-compiler
* Make tests (mocha, chai)
* Use identity operator `!==`, `===`. Look out - check boolean values always `parameter === true` or `parameter !== null`.
* Naming convention for objects is the same as in java - camelCase, singular etc. Exception are contents representing url or route - can be in plurar (e.g. Identities, Roles with the list of entities).
* `constructor` method has to be at the start of class
* `componentDidMount` and the next react lifecycle methods has to be in the lifecycle order.
* `render` method has to be on the end of react component
* Character `_` at the start of attribute or method => private attribute or method
* use less variables
* use ``encodeURIComponent`` to encode parameters used directly in urls e.g. ``this.context.router.push(`identity/${encodeURIComponent(entity.username)}/profile`)``
* use `super.componentDidMount();` in all contents (extends `AbstractContent`).
* use ``LocalizationTester`` to validate locales (see core test package),
* listen redux state ``i18nReady: state.config.get('i18nReady')`` if your component / content use localization (F5 is not needed for the change locale).

### IDE

* [Atom](https://wiki.czechidm.com/devel/documentation/quickstart/dev/ide/atom)
* [VS Code](https://wiki.czechidm.com/devel/documentation/quickstart/dev/ide/vscode)

## Documentation

### Convention

* Create relative links - e.g. [[.:configuration|Configuration]] - when version is released, then namespace is copied.
* TODO: screen shot conventions


## Our Development Process

1. Clone or Fork the repo and create your branch from `CzechIdMng` (branch `develop`).

2. If you've added code that should be tested, add tests.

3. Ensure the test suite passes.

4. Make sure your code lints.

5. Do not commit anything to the `dist` or `target` folder.

6. Add information about the change to the changelog.
 
7. Create a pull request to the `develop` branch. Select a reviewer and assign them as the assignee of the PR. As a comment, add a link to the related Redmine page. In the Redmine page, add a link to the PR.
 
8. After the review is finished, the PR can be merged to the `develop` branch. (This is typically done by the reviewer.)

9. Once the PR is merged to the `develop` branch, delete the source branch.

### Hotfix

Preparing and releasing hotfixes is a responsibility of a maintainer.

1. Clone or Fork the repo and create your branch from `CzechIdMng`. As a base for your branch, use the immediatelly preceding tag. For example, if you want to release hotfix version 10.7.5, the base for your branch should be the tag 10.7.4. Name the branch `10.7.5-hotfix`. Commit and push this (empty) branch.

2. Create a new branch based on the `10.7.5-hotfix` branch. Add your code following the same rules as above.

3. Create a pull request to the `10.7.5-hotfix` branch. Select a reviewer and assign them as the assignee of the PR. As a comment, add a link to the related Redmine page. In the Redmine page, add a link to the PR.

4. Add information about the change to the changelog.

5. After the review is finished, the PR can be merged to the 10.7.5-hotfix branch. (This is typically done by the reviewer.)

6. If you want to add the code to the current version, repeat the same steps but use the branch `develop` which already exists. In the PR, refer to the original PR.

7. Ensure that the version 10.7.5 is released from the branch `10.7.5-hotfix`.

8. Once the PR is merged to the `10.7.5-hotfix` branch, delete the source branch.

## License

[MIT License](./LICENSE)
