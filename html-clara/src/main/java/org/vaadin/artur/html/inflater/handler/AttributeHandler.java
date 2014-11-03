package org.vaadin.artur.html.inflater.handler;

import static org.vaadin.artur.html.util.ReflectionUtils.findMethods;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.vaadin.artur.html.inflater.LayoutInflater;
import org.vaadin.artur.html.inflater.filter.AttributeContext;
import org.vaadin.artur.html.inflater.filter.AttributeFilter;
import org.vaadin.artur.html.inflater.parser.AttributeParser;
import org.vaadin.artur.html.inflater.parser.ComponentPositionParser;
import org.vaadin.artur.html.inflater.parser.EnumAttributeParser;
import org.vaadin.artur.html.inflater.parser.PrimitiveAttributeParser;
import org.vaadin.artur.html.inflater.parser.VaadinAttributeParser;
import org.vaadin.artur.html.util.MethodComparator;
import org.vaadin.artur.html.util.ReflectionUtils.ParamCount;

import com.vaadin.ui.Component;

public class AttributeHandler {

	private final List<AttributeParser> attributeParsers = new ArrayList<AttributeParser>();
	private final List<AttributeFilter> attributeFilters;

	public AttributeHandler(List<AttributeFilter> attributeFilters) {
		this.attributeFilters = attributeFilters;

		// Setup the default AttributeHandlers.
		attributeParsers.add(new PrimitiveAttributeParser());
		attributeParsers.add(new VaadinAttributeParser());
		attributeParsers.add(new EnumAttributeParser());
		attributeParsers.add(new ComponentPositionParser());
	}

	protected Logger getLogger() {
		return Logger.getLogger(AttributeHandler.class.getName());
	}

	/**
	 * Assigns the given attributes to the given {@link Component}.
	 * 
	 * @param component
	 * @param attributes
	 */
	public void assignAttributes(Component component,
			Map<String, String> attributes) {

		if (attributes.isEmpty()) {
			return;
		}

		for (Map.Entry<String, String> attribute : attributes.entrySet()) {
			if (attribute.getKey().startsWith("$"))
				continue;
			
			Method setter = getWriteMethod(attribute.getKey(),
					component.getClass());
			if (setter == null) {
				getLogger().warning(
						"Unknown attribute " + attribute.getKey() + " for "
								+ component.getClass().getSimpleName());
			} else {
				if (setter.getParameterTypes().length == 0) {
					// Setter method without any parameters.
					try {
						setter.invoke(component);
					} catch (Exception e) {
						getLogger().warning(
								"Error handling attribute " + attribute.getKey() + " for "
										+ component.getClass().getSimpleName());
					}

				} else {
					AttributeParser parser = getParserFor(setter
							.getParameterTypes()[0]);
					if (parser != null) {
						// We have a handler that knows how to handle
						// conversion
						// for this property.
						String attributeValue = attribute.getValue();
						// Ask the AttributeHandler to convert the
						// value.
						try {
							invokeWithAttributeFilters(setter, component,
									parser.getValueAs(attributeValue,
											setter.getParameterTypes()[0],
											component));
						} catch (Exception e) {
							getLogger().warning(
									"Error handling attribute " + attribute.getKey() + " for "
											+ component.getClass().getSimpleName());
						}
					}
				}
			}
		}
	}

	protected void invokeWithAttributeFilters(final Method methodToInvoke,
			final Object obj, final Object... args)
			throws IllegalArgumentException, IllegalAccessException,
			InvocationTargetException {

		if (attributeFilters.isEmpty()) {
			methodToInvoke.invoke(obj, args);
		} else {
			final LinkedList<AttributeFilter> filtersCopy = new LinkedList<AttributeFilter>(
					attributeFilters);
			AttributeFilter firstFilter = filtersCopy.pop();
			firstFilter.filter(new AttributeContext(methodToInvoke,
					args.length > 1 ? args[1] : args[0]) {

				@Override
				public void proceed() throws Exception {
					if (filtersCopy.size() > 0) {
						// More filters -> invoke them.
						filtersCopy.pop().filter(this);
					} else {
						// No more filters -> time to invoke the actual
						// method.
						if (args.length > 1) {
							methodToInvoke.invoke(obj, args[0], this.getValue());
						} else {
							methodToInvoke.invoke(obj, this.getValue());
						}
					}
				}
			});
		}
	}

	protected AttributeParser getParserFor(Class<?> type) {
		for (AttributeParser parser : attributeParsers) {
			if (parser.isSupported(type)) {
				return parser;
			}
		}
		return null;
	}

	protected static String getWriteMethodName(String propertyName) {
		if (propertyName.length() > 0) {
			// For example: "sizeFull" -> "setSizeFull"
			return "set" + propertyName.substring(0, 1).toUpperCase()
					+ propertyName.substring(1);
		}
		return "";
	}

	protected Method getWriteMethod(String propertyName,
			Class<? extends Component> componentClass) {
		List<Method> writeMethods = findMethods(componentClass,
				getWriteMethodName(propertyName), ParamCount.fromTo(0, 1));
		return getPreferredMethod(writeMethods);
	}

	protected Method getPreferredMethod(List<Method> methods) {
		if (methods == null || methods.isEmpty()) {
			return null;
		}

		// Sort to find the preferred method.
		Collections.sort(methods, new ParserAwareMethodComparator());
		return methods.get(0);
	}

	/**
	 * Comparator to sort {@link Method}s into a preferred ordering taking into
	 * account available parsers in addition to method deprecation.
	 */
	private class ParserAwareMethodComparator extends MethodComparator {

		private Class<?> getPropertyClass(Method method) {
			Class<?>[] parameterTypes = method.getParameterTypes();
			if (parameterTypes.length > 1
					&& parameterTypes[0] == Component.class) {
				// First parameter is the Component -> use the second one for
				// the property.
				return parameterTypes[1];
			}
			return parameterTypes[0];
		}

		private boolean isSpecialAttributeParser(AttributeParser parser) {
			return parser != null
					&& !(parser instanceof PrimitiveAttributeParser);
		}

		@Override
		public int compare(Method method1, Method method2) {
			// Check for parsers.
			AttributeParser parser1 = getParserFor(getPropertyClass(method1));
			AttributeParser parser2 = getParserFor(getPropertyClass(method2));
			if (isSpecialAttributeParser(parser1)
					&& !isSpecialAttributeParser(parser2)) {
				return -1;
			}
			if (isSpecialAttributeParser(parser2)
					&& !isSpecialAttributeParser(parser1)) {
				return 1;
			}
			return super.compare(method1, method2);
		}
	}

}
