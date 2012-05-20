package org.vaadin.teemu.clara.factory;

public interface AttributeHandler {

    /**
     * Returns {@code true} if this {@link AttributeHandler} can convert a
     * {@link String} to an instance of the given {@code valueType}.
     * 
     * @param valueType
     * @return
     */
    boolean isSupported(Class<?> valueType);

    /**
     * Returns the given {@code value} as an instance of the given
     * {@code valueType}. Before this method is ever invoked the support for
     * value conversion is checked by calling the {@link #isSupported(Class)}
     * method.
     * 
     * @param valueType
     * @return
     */
    Object getValueAs(String value, Class<?> valueType);

}
