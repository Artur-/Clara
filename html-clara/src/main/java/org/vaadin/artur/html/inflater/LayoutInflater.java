package org.vaadin.artur.html.inflater;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Stack;
import java.util.logging.Logger;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;
import org.jsoup.select.NodeVisitor;
import org.vaadin.artur.html.binder.Binder;
import org.vaadin.artur.html.inflater.filter.AttributeFilter;
import org.vaadin.artur.html.inflater.handler.AttributeHandler;
import org.vaadin.artur.html.inflater.handler.LayoutAttributeHandler;

import com.vaadin.ui.Component;
import com.vaadin.ui.ComponentContainer;
import com.vaadin.ui.HasComponents;
import com.vaadin.ui.SingleComponentContainer;

public class LayoutInflater {

	private List<AttributeFilter> attributeFilters = new ArrayList<AttributeFilter>();

	protected Logger getLogger() {
		return Logger.getLogger(LayoutInflater.class.getName());
	}

	/**
	 * Inflates the given {@code html} into a {@link Component} (hierarchy).
	 * 
	 * @param xml
	 * @return the inflated {@link Component} (hierarchy).
	 * 
	 * @throws LayoutInflaterException
	 *             in case of an error in the inflation process.
	 */
	public Component inflate(InputStream xml) {
		return inflate(xml, null, null);
	}

	public Component inflate(InputStream xml, HasComponents fieldContainer,
			Object controller) {
		try {
			LayoutInflaterContentHandler contentHandler = new LayoutInflaterContentHandler(
					fieldContainer);

			Document doc = Jsoup.parse(xml, "UTF-8", "", Parser.htmlParser());
			doc.body().traverse(contentHandler);
			assignFields(fieldContainer, contentHandler);
			bind(controller, contentHandler);

			return contentHandler.root;
		} catch (IOException e) {
			throw new LayoutInflaterException(e);
		} catch (ComponentInstantiationException e) {
			throw new LayoutInflaterException(e.getMessage(), e);
		}
	}

	private void bind(Object controller,
			LayoutInflaterContentHandler contentHandler) {
		Binder binder = new Binder();
		binder.bind(contentHandler.root, controller, contentHandler.globalIds, contentHandler.localIds, contentHandler.captions);
		
	}

	private void assignFields(Object fieldContainer,
			LayoutInflaterContentHandler contentHandler) {
		if (fieldContainer == null)
			return;
		for (Field f : fieldContainer.getClass().getDeclaredFields()) {
			String fieldName = f.getName().toLowerCase(Locale.ENGLISH);
			Component c;
			c = contentHandler.globalIds.get(fieldName);
			if (c != null) {
				assign(f, fieldContainer, c);
				continue;
			}
			c = contentHandler.localIds.get(fieldName);
			if (c != null) {
				assign(f, fieldContainer, c);
				continue;
			}
			c = contentHandler.captions.get(fieldName);
			if (c != null) {
				assign(f, fieldContainer, c);
				continue;
			}

			throw new LayoutInflaterException(
					"Unable to find component for field " + fieldName + " in "
							+ fieldContainer.getClass().getName());
		}

	}

	private void assign(Field f, Object controller, Component c) {
		try {
			if (!f.isAccessible())
				f.setAccessible(true);
			f.set(controller, c);
		} catch (Exception e) {
			getLogger().severe(
					"Error assigning field " + f.getName()
							+ " in controller of type "
							+ controller.getClass().getName());
		}

	}

	public void addAttributeFilter(AttributeFilter attributeFilter) {
		attributeFilters.add(attributeFilter);
	}

	public void removeAttributeFilter(AttributeFilter attributeFilter) {
		attributeFilters.remove(attributeFilter);
	}

	private class LayoutInflaterContentHandler implements NodeVisitor {

		private static final String ID_ATTRIBUTE = "id";
		private static final String CAPTION_ATTRIBUTE = "caption";

		private Stack<Component> componentStack = new Stack<Component>();
		private HasComponents currentParent;
		private Component root;
		private final ComponentFactory componentFactory;
		private final AttributeHandler attributeHandler;
		private final LayoutAttributeHandler layoutAttributeHandler;

		private final Map<String, Component> globalIds = new HashMap<String, Component>();
		private final Map<String, Component> localIds = new HashMap<String, Component>();
		private final Map<String, Component> captions = new HashMap<String, Component>();
		private HasComponents fieldContainer;

		public LayoutInflaterContentHandler(HasComponents fieldContainer) {
			this.fieldContainer = fieldContainer;
			componentFactory = new ComponentFactory();
			attributeHandler = new AttributeHandler(attributeFilters);
			layoutAttributeHandler = new LayoutAttributeHandler(
					attributeFilters);
			// assignedIds.clear();
		}

		@Override
		public void head(Node node, int depth) {
			if (!(node instanceof Element))
				return;
			Element e = (Element) node;
			if ("body".equals(e.tagName()))
				return;

			org.jsoup.nodes.Attributes attributes = node.attributes();
			Map<String, String> attributeMap = getAttributeMap(attributes);
			// Global id
			String id = attributes.get(ID_ATTRIBUTE);
			Component component;
			if (root == null)
				component = fieldContainer;
			else
				component = instantiateComponent(node, id);

			if (id != null && id.length() > 0) {
				Component oldComponent = globalIds
						.put(camelCase(id), component);
				if (oldComponent != null) {
					throw new LayoutInflaterException("Duplicate ids: " + id);
				}
			}

			// Local id
			String localId = null;
			for (Attribute attribute : attributes.asList()) {
				if (attribute.getKey().startsWith("$")) {
					if (localId != null) {
						throw new LayoutInflaterException(
								"Duplicate local ids specified: " + localId
										+ " and " + attribute.getValue());
					}
					localId = attribute.getKey().substring(1);
					localIds.put(camelCase(localId), component);
				}
			}

			// Caption
			String caption = null;
			if (node.nodeName().equals("v-button")) {
				caption = textContent(node);
				if (!caption.equals("")) {
					attributeMap.put(CAPTION_ATTRIBUTE, caption);
				}
			}

			if (attributeMap.containsKey(CAPTION_ATTRIBUTE)) {
				caption = attributeMap.get(CAPTION_ATTRIBUTE);
				Component oldComponent = captions.put(camelCase(caption),
						component);
				if (oldComponent != null) {
					throw new LayoutInflaterException("Duplicate captions: "
							+ caption);
				}
			}

			// Basic attributes -> attach -> layout attributes.
			attributeHandler.assignAttributes(component, attributeMap);
			if (root == null) {
				// This was the first Component created -> root.
				root = component;
			} else {
				attachComponent(component);
			}
			layoutAttributeHandler.assignAttributes(component, attributeMap);

			if (component instanceof HasComponents) {
				currentParent = (HasComponents) component;
			}
			componentStack.push(component);
		}

		private String textContent(Node node) {
			String text = "";
			for (Node child : node.childNodes()) {
				if (child instanceof TextNode) {
					text += ((TextNode) child).text();
				}
			}
			return text;
		}

		private String camelCase(String localId) {
			// Remove all but a-Z, 0-9 (used for names) and _- and space (used
			// for separators)
			// localId = localId.replaceAll("[^a-zA-Z0-9_- ]", "");
			return localId.replaceAll("[^a-zA-Z0-9]", "").toLowerCase(
					Locale.ENGLISH);
			// String[] parts = localId.split("[ -_]+");
			// String thisPart = parts[0];
			// String camelCase =
			// thisPart.substring(0,1).toLowerCase(Locale.ENGLISH);
			// if (parts[0].length() > 1) {
			// camelCase += thisPart.substring(1);
			// }
			//
			// for (int i=1; i < parts.length; i++) {
			// thisPart = parts[i];
			// camelCase += thisPart.substring(0,1).toUpperCase(Locale.ENGLISH);
			// if (thisPart.length() > 1) {
			// camelCase += thisPart.substring(1);
			// }
			// }
			// return camelCase;
		}

		private Map<String, String> getAttributeMap(
				org.jsoup.nodes.Attributes attributes) {
			// Isn't this incorrectly optimized as load factor is 0.75?
			Map<String, String> attributeMap = new HashMap<String, String>(
					attributes.size());
			for (Attribute attr : attributes.asList()) {
				String key = attr.getKey();
				String value = attr.getValue();
				attributeMap.put(key, value);
			}
			return attributeMap;
		}

		private void attachComponent(Component component) {
			// Component topComponent = componentStack.isEmpty() ? null
			// : componentStack.peek();
			System.out.println("Attach " + component.getClass().getSimpleName()
					+ " to " + currentParent.getClass().getSimpleName());
			if (currentParent instanceof SingleComponentContainer) {
				((SingleComponentContainer) currentParent)
						.setContent(component);
			} else if (currentParent instanceof ComponentContainer) {
				((ComponentContainer) currentParent).addComponent(component);
			} else {
				getLogger().warning(
						"Don't know how to add child to "
								+ currentParent.getClass().getName());
			}
		}

		@Override
		public void tail(Node node, int depth) {
			if (!(node instanceof Element))
				return;
			Element e = (Element) node;
			if ("body".equals(e.tagName()))
				return;

			Component component = componentStack.pop();
			if (component instanceof HasComponents) {
				Component parent = component.getParent();
				while (parent != null && !(parent instanceof HasComponents)) {
					parent = parent.getParent();
				}
				currentParent = (HasComponents) parent;
			}
		}

		private Component instantiateComponent(Node node, String id) {
			// Extract the package and class names.
			String qualifiedClassName = tagNameToClassName(node);
			return componentFactory.createComponent(qualifiedClassName);
		}

		private String tagNameToClassName(Node node) {
			String tagName = node.nodeName();
			if (tagName.equals("v-addon")) {
				return node.attr("class");
			} else if (tagName.startsWith("v-")) {
				// v-vertical-layout -> com.vaadin.ui.VerticalLayout
				tagName = tagName.substring(2, 3).toUpperCase(Locale.ENGLISH)
						+ tagName.substring(3);
				int i;
				while ((i = tagName.indexOf("-")) != -1) {
					int length = tagName.length();
					if (i != length - 1) {
						tagName = tagName.substring(0, i)
								+ tagName.substring(i + 1, i + 2).toUpperCase(
										Locale.ENGLISH)
								+ tagName.substring(i + 2);

					} else {
						// Ends with "-", WTF?
						System.out.println("ends with '-', really?");
					}
				}
				return "com.vaadin.ui." + tagName;
			} else if (tagName.toLowerCase(Locale.ENGLISH).equals("span")
					|| tagName.toLowerCase(Locale.ENGLISH).equals("div"))
				return "com.vaadin.ui.Label";

			throw new LayoutInflaterException("Unknown tag: " + tagName);
		}

	}
}
