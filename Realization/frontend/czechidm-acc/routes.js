module.exports = {
  module: 'acc',
  component: 'div',
  childRoutes: [
    {
      path: 'schema-object-classes/:entityId/detail',
      component: require('./src/content/schema/SchemaObjectClass'),
      access: [ { 'type': 'HAS_ANY_AUTHORITY', 'authorities': ['SYSTEM_READ'] } ]
    },
    {
      path: 'schema-object-classes/:entityId/new',
      component: require('./src/content/schema/SchemaObjectClass'),
      access: [ { 'type': 'HAS_ANY_AUTHORITY', 'authorities': ['SYSTEM_WRITE'] } ]
    },
    {
      path: 'system-entities-handling/:entityId/detail',
      component: require('./src/content/schema/SystemEntityHandling'),
      access: [ { 'type': 'HAS_ANY_AUTHORITY', 'authorities': ['SYSTEM_READ'] } ]
    },
    {
      path: 'system-entities-handling/:entityId/new',
      component: require('./src/content/schema/SystemEntityHandling'),
      access: [ { 'type': 'HAS_ANY_AUTHORITY', 'authorities': ['SYSTEM_WRITE'] } ]
    },
    {
      path: 'schema-attributes/:entityId/detail',
      component: require('./src/content/schema/SchemaAttribute'),
      access: [ { 'type': 'HAS_ANY_AUTHORITY', 'authorities': ['SYSTEM_READ'] } ]
    },
    {
      path: 'schema-attributes/:entityId/new',
      component: require('./src/content/schema/SchemaAttribute'),
      access: [ { 'type': 'HAS_ANY_AUTHORITY', 'authorities': ['SYSTEM_WRITE'] } ]
    },
    {
      path: 'schema-attributes-handling/:entityId/detail',
      component: require('./src/content/schema/SchemaAttributeHandling'),
      access: [ { 'type': 'HAS_ANY_AUTHORITY', 'authorities': ['SYSTEM_READ'] } ]
    },
    {
      path: 'schema-attributes-handling/:entityId/new',
      component: require('./src/content/schema/SchemaAttributeHandling'),
      access: [ { 'type': 'HAS_ANY_AUTHORITY', 'authorities': ['SYSTEM_WRITE'] } ]
    },
    {
      path: 'systems',
      component: require('./src/content/system/Systems'),
      access: [ { 'type': 'HAS_ANY_AUTHORITY', 'authorities': ['SYSTEM_READ'] } ]
    },
    {
      path: 'system/:entityId/',
      component: require('./src/content/system/System'),
      access: [ { 'type': 'HAS_ANY_AUTHORITY', 'authorities': ['SYSTEM_READ'] } ],
      childRoutes: [
        {
          path: 'detail',
          component: require('./src/content/system/SystemContent'),
          access: [ { 'type': 'HAS_ANY_AUTHORITY', 'authorities': ['SYSTEM_READ'] } ]
        },
        {
          path: 'entities',
          component: require('./src/content/system/SystemEntities'),
          access: [ { 'type': 'HAS_ANY_AUTHORITY', 'authorities': ['SYSTEM_READ'] } ]
        },
        {
          path: 'object-classes',
          component: require('./src/content/system/SystemObjectClasses'),
          access: [ { 'type': 'HAS_ANY_AUTHORITY', 'authorities': ['SYSTEM_READ'] } ]
        },
        {
          path: 'entities-handling',
          component: require('./src/content/system/SystemEntitiesHandling'),
          access: [ { 'type': 'HAS_ANY_AUTHORITY', 'authorities': ['SYSTEM_READ'] } ]
        },
        {
          path: 'accounts',
          component: require('./src/content/system/SystemAccounts'),
          access: [ { 'type': 'HAS_ALL_AUTHORITIES', 'authorities': ['SYSTEM_READ', 'ACCOUNT_READ'] } ]
        },
        {
          path: 'connector',
          component: require('./src/content/system/SystemConnector'),
          access: [ { 'type': 'HAS_ANY_AUTHORITY', 'authorities': ['SYSTEM_READ'] } ]
        }
      ]
    },
    {
      path: 'system/:entityId/new',
      component: require('./src/content/system/SystemContent'),
      access: [ { 'type': 'HAS_ANY_AUTHORITY', 'authorities': ['SYSTEM_WRITE'] } ]
    },
    {
      path: 'identity/:entityId/',
      component: require('czechidm-core/src/content/identity/Identity'),
      childRoutes: [
        {
          path: 'accounts',
          component: require('./src/content/identity/IdentityAccounts')
        }
      ]
    },
    {
      path: 'role/:entityId/',
      component: require('czechidm-core/src/content/role/Role'),
      access: [ { 'type': 'HAS_ANY_AUTHORITY', 'authorities': ['ROLE_READ'] } ],
      childRoutes: [
        {
          path: 'systems',
          component: require('./src/content/role/RoleSystems'),
          access: [ { 'type': 'HAS_ANY_AUTHORITY', 'authorities': ['ROLE_READ', 'SYSTEM_READ'] } ]
        },
      ]
    },
  ]
};
