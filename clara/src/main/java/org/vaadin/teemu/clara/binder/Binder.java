package org.vaadin.teemu.clara.binder;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Set;
import java.util.logging.Logger;

import org.vaadin.teemu.clara.Clara;
import org.vaadin.teemu.clara.binder.annotation.DataSource;
import org.vaadin.teemu.clara.binder.annotation.EventHandler;
import org.vaadin.teemu.clara.util.ReflectionUtils;

import com.vaadin.data.Container;
import com.vaadin.data.Item;
import com.vaadin.data.Property;
import com.vaadin.ui.Component;

public class Binder {

    protected Logger getLogger() {
        return Logger.getLogger(Binder.class.getName());
    }

    public void bind(Component componentRoot, Object controller) {
        Method[] methods = controller.getClass().getMethods();
        for (Method method : methods) {
            if (method.isAnnotationPresent(DataSource.class)) {
                bindDataSource(componentRoot, controller, method,
                        method.getAnnotation(DataSource.class));
            }

            if (method.isAnnotationPresent(EventHandler.class)) {
                bindEventHandler(componentRoot, controller, method,
                        method.getAnnotation(EventHandler.class));
            }
        }
    }

    private void bindEventHandler(Component componentRoot, Object controller,
            Method method, EventHandler eventListener) {
        String componentId = eventListener.value();
        Component component = Clara.findComponentById(componentRoot,
                componentId);
        if (component == null) {
            throw new BinderException("No component found for id: "
                    + componentId + ".");
        }

        Class<?> eventType = (method.getParameterTypes().length > 0 ? method
                .getParameterTypes()[0] : null);
        if (eventType == null) {
            throw new BinderException(
                    "Couldn't figure out event type for method " + method + ".");
        }

        Method addListenerMethod = getAddListenerMethod(component.getClass(),
                eventType);
        if (addListenerMethod != null) {
            try {
                Object listener = createListenerProxy(
                        addListenerMethod.getParameterTypes()[0], eventType,
                        method, controller);
                addListenerMethod.invoke(component, listener);
                // TODO exception handling
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
        }
    }

    private Object createListenerProxy(Class<?> listenerClass,
            final Class<?> eventClass, final Method listenerMethod,
            final Object controller) {
        Object proxy = Proxy.newProxyInstance(listenerClass.getClassLoader(),
                new Class<?>[] { listenerClass }, new InvocationHandler() {

                    public Object invoke(Object proxy, Method method,
                            Object[] args) throws Throwable {

                        if (args != null
                                && args.length > 0
                                && eventClass.isAssignableFrom(args[0]
                                        .getClass())) {
                            getLogger().fine(
                                    String.format(
                                            "Forwarding method call %s -> %s.",
                                            method.getName(),
                                            listenerMethod.getName()));
                            return listenerMethod.invoke(controller, args);
                        }
                        getLogger()
                                .fine(String.format(
                                        "Forwarding method call %s to %s.",
                                        method.getName(), controller.getClass()));
                        return method.invoke(controller, args);
                    }

                });
        getLogger().fine(
                String.format("Created a proxy for %s.", listenerClass));
        return proxy;
    }

    private Method getAddListenerMethod(
            Class<? extends Component> componentClass, Class<?> eventClass) {
        Set<Method> methods = ReflectionUtils.getMethodsByNameAndParamCount(
                componentClass, "addListener", 1);
        for (Method method : methods) {
            // Check if this method accepts correct type of listeners.
            Class<?> listenerClass = method.getParameterTypes()[0];
            Method[] listenerMethods = listenerClass.getMethods();
            for (Method listenerMethod : listenerMethods) {
                if (listenerMethod.getParameterTypes().length == 1
                        && listenerMethod.getParameterTypes()[0]
                                .equals(eventClass)) {
                    // Found a method from the listener interface
                    // that accepts the eventClass instance as
                    // a sole parameter.
                    return method;
                }
            }
        }
        return null;
    }

    private void bindDataSource(Component componentRoot, Object controller,
            Method method, DataSource dataSource) {
        String componentId = dataSource.value();
        Component component = Clara.findComponentById(componentRoot,
                componentId);
        Class<?> dataSourceClass = method.getReturnType();

        try {
            // Vaadin data model consists of Property/Item/Container
            // objects and each of them have a Viewer interface.
            if (isContainer(dataSourceClass)
                    && component instanceof Container.Viewer) {
                ((Container.Viewer) component)
                        .setContainerDataSource((Container) method
                                .invoke(controller));
            } else if (isProperty(dataSourceClass)
                    && component instanceof Property.Viewer) {
                ((Property.Viewer) component)
                        .setPropertyDataSource((Property) method
                                .invoke(controller));
            } else if (isItem(dataSourceClass)
                    && component instanceof Item.Viewer) {
                ((Item.Viewer) component).setItemDataSource((Item) method
                        .invoke(controller));
            }
            // TODO exception handling
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    private boolean isContainer(Class<?> dataSourceClass) {
        return Container.class.isAssignableFrom(dataSourceClass);
    }

    private boolean isItem(Class<?> dataSourceClass) {
        return Item.class.isAssignableFrom(dataSourceClass);
    }

    private boolean isProperty(Class<?> dataSourceClass) {
        return Property.class.isAssignableFrom(dataSourceClass);
    }
}
