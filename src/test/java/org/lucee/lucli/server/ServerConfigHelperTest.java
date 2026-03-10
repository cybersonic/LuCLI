package org.lucee.lucli.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

class ServerConfigHelperTest {

    @SuppressWarnings("unchecked")
    private List<String> invokeFilterSupportedVersions(List<String> versions) throws Exception {
        ServerConfigHelper helper = new ServerConfigHelper();
        Method method = ServerConfigHelper.class.getDeclaredMethod("filterSupportedVersions", List.class);
        method.setAccessible(true);
        return (List<String>) method.invoke(helper, versions);
    }

    @Test
    void filterSupportedVersions_removesUnsupportedAndDuplicates_preservesOrder() throws Exception {
        List<String> input = Arrays.asList(
                "7.0.1.100-RC",
                "6.2.2.91",
                "6.0.4.10",
                "5.4.5.17",
                "6.1.8.29",
                "6.2.2.91");

        List<String> filtered = invokeFilterSupportedVersions(input);

        assertEquals(Arrays.asList("7.0.1.100-RC", "6.2.2.91", "6.1.8.29"), filtered);
    }

    @Test
    void filterSupportedVersions_handlesNullAndEmptyInputs() throws Exception {
        List<String> fromNull = invokeFilterSupportedVersions(null);
        List<String> fromEmpty = invokeFilterSupportedVersions(Collections.emptyList());

        assertTrue(fromNull.isEmpty());
        assertTrue(fromEmpty.isEmpty());
    }
}
