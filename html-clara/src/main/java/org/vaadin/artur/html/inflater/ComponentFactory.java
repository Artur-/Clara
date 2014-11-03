package org.vaadin.artur.html.inflater;

import static org.vaadin.artur.html.util.ReflectionUtils.isComponent;

import com.vaadin.ui.Component;

public class ComponentFactory {

	/**
	 * Returns a new {@link Component} instance of given {@code namespace} and
	 * {@code name} with fields populated from the {@code attributes} map. If
	 * the component cannot be instantiated properly a
	 * {@link ComponentInstantiationException} is thrown.
	 * 
	 * @param namespace
	 * @param name
	 * @param attributes
	 * @return a new {@link Component} instance.
	 * @throws ComponentInstantiationException
	 */
	public Component createComponent(String qualifiedClassName)
			throws ComponentInstantiationException {
		try {
			Class<? extends Component> componentClass = resolveComponentClass(
					qualifiedClassName);
			Component newComponent = componentClass.newInstance();
			return newComponent;
		} catch (Exception e) {
			throw createException(e, qualifiedClassName);
		}
	}

	@SuppressWarnings("unchecked")
	private Class<? extends Component> resolveComponentClass(
			String qualifiedClassName) throws ClassNotFoundException {
		Class<?> componentClass = null;
		componentClass = Class.forName(qualifiedClassName);

		// Check that we're dealing with a Component.
		if (isComponent(componentClass)) {
			return (Class<? extends Component>) componentClass;
		} else {
			throw new IllegalArgumentException(String.format(
					"Resolved class %s is not a %s.", componentClass.getName(),
					Component.class.getName()));
		}
	}

	private ComponentInstantiationException createException(Exception e,
			String qualifiedClassName) {
		String message = String
				.format("Couldn't instantiate a component for %s.",
						qualifiedClassName);
		if (e != null) {
			return new ComponentInstantiationException(message, e);
		} else {
			return new ComponentInstantiationException(message);
		}
	}

}
