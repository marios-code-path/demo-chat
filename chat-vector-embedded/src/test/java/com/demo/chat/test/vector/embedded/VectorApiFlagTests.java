package com.demo.chat.test.vector.embedded;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorSpecies;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VectorApiFlagTests {

    @Test
    void vectorApiModuleLoads() {
        VectorSpecies<Float> species = FloatVector.SPECIES_PREFERRED;

        assertThat(species.length()).isGreaterThan(0);
    }
}
