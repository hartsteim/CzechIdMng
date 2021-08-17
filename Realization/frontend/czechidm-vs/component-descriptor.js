module.exports = {
  id: 'vs',
  name: 'Virtual systems for CzechIdM',
  description: 'Components for Virtual system module',
  components: [
    {
      id: 'vsDashboard',
      type: 'dashboard',
      order: '400',
      component: require('./src/content/dashboards/VsDashboard')
    },
    {
      id: 'vs-request-info',
      type: 'entity-info',
      entityType: ['vs-request', 'VsRequest'],
      component: require('./src/components/advanced/VsRequestInfo/VsRequestInfo').default,
      manager: require('./src/redux').VsRequestManager
    },
    {
      id: 'vs-connector-icon',
      type: 'icon',
      entityType: ['virtual-reality'],
      component: require('./src/components/basic/VsConnectorIcon/VsConnectorIcon')
    },
    {
      id: 'vs-form-value-info',
      type: 'entity-info',
      entityType: ['VsAccountFormValue'],
      component: require('czechidm-core/src/components/advanced/FormValueInfo/FormValueInfo').default
    }
  ]
};
