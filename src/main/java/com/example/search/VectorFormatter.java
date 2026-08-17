package com.example.search;

import org.springframework.stereotype.Component;

@Component
public class VectorFormatter {

    public String toPgVector(float[] vector) {

        if (vector == null || vector.length == 0) {
            throw new IllegalArgumentException(
                    "Vector cannot be empty."
            );
        }

        StringBuilder builder =
                new StringBuilder("[");

        for (int i = 0; i < vector.length; i++) {

            if (i > 0) {
                builder.append(",");
            }

            builder.append(
                    Float.toString(vector[i])
            );
        }

        builder.append("]");

        return builder.toString();
    }
}
