package org.vaadin.artur.html;

import com.vaadin.ui.HasComponents;

public class DeclaredView<T extends HasComponents> {
	protected DeclaredView() {
		try {
			root = getLayoutClass().newInstance();
			DeclaredView<T> controller = this;
			Clara.create(root.getClass().getSimpleName() + ".html", root,
					controller);
		} catch (InstantiationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// Loads design and binds
	}

	protected Class<? extends T> getLayoutClass() {
		if (getClass().isAnnotationPresent(Layout.class)) {
			return (Class<? extends T>) getClass().getAnnotation(Layout.class)
					.value();
		}
		// Convention
		String cls = getClass().getName();
		if (cls.endsWith("View")) {
			try {
				return (Class<? extends T>) Class.forName(cls.replaceFirst(
						"View$", ""));
			} catch (Exception e) {
			}
		}
		return null;

	}

	protected T root;

	public T getRoot() {
		return root;
	}
}
