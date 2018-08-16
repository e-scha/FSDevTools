package com.espirit.moddev.core;

import org.junit.Test;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class StringUtilsTest {
    @Test
    public void testParameterlessConstructor() {
        StringPropertiesMap map = new StringPropertiesMap();

        assertThat("Paramaterless constructor should create empty map!", map.values(), is(empty()));
    }
}
