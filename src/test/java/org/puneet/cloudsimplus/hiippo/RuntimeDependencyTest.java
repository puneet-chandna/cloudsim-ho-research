package org.puneet.cloudsimplus.hiippo;

import org.apache.commons.lang3.ClassUtils;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuntimeDependencyTest {
    @Test void longMissingArrayClassFailsWithoutStackOverflow() {
        // CVE-2025-48924: resolving dotted array names must not recurse until the stack overflows.
        var name="missing.".repeat(8192)+"Type"+"[]".repeat(256);
        assertThrows(ClassNotFoundException.class,()->ClassUtils.getClass(name));
    }
}
