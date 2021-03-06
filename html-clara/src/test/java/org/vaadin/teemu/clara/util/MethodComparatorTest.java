package org.vaadin.teemu.clara.util;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.vaadin.artur.html.util.MethodComparator;

public class MethodComparatorTest {

    private interface InterfaceToTest {
        void test();

        @Deprecated
        void test2();

        void test3();
    }

    @Test
    public void testMethodComparator() {
        List<Method> methods = Arrays
                .asList(InterfaceToTest.class.getMethods());
        Collections.sort(methods, new MethodComparator());

        // Deprecated method is now last.
        assertEquals(methods.get(2).getName(), "test2");
    }
}
