import Immutable from 'immutable';
//
import ConfigLoader from './ConfigLoader';

let _components = new Immutable.Map();
let _componentDescriptors = new Immutable.Map();

export default class ComponentLoader {

  static initComponents(componentDescriptors) {
    _componentDescriptors = componentDescriptors;
    this.reloadComponents();
  }

  /**
   * Reloads registered components - module could be disabled / enabled
   */
  static reloadComponents() {
    _components = _components.clear();
    _componentDescriptors.toArray().map(descriptor => {
      this._fillComponents(descriptor);
    });
  }

  static getComponentDescriptor(moduleName) {
    return _componentDescriptors.get(moduleName);
  }

  static getComponent(componentId) {
    if (_components.get(componentId)) {
      return _components.get(componentId).component;
    }
    return null;
  }

  static getComponentDefinition(componentId) {
    return _components
      .find(component => {
        return component.id === componentId;
      });
  }

  static getComponentDefinitions(type) {
    return _components
      .filter(component => {
        return !component.disabled && (!type || component.type === type);
      })
      .sortBy(item => item.order);
  }

  static _fillComponents(componentDescriptor) {
    for (const component of componentDescriptor.components) {
      if (!_components.has(component.id) || (_components.get(component.id).priority || 0) < (component.priority || 0)) {
        component.module = componentDescriptor.id;
        // map default export as component if needed
        if (component.component && component.component.__esModule && component.component.default) {
          component.component = component.component.default;
        }
        if (ConfigLoader.isEnabledModule(componentDescriptor.id)) {
          _components = _components.set(component.id, component);
        }
      }
    }
  }
}
