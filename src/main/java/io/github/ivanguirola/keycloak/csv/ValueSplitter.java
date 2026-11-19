package io.github.ivanguirola.keycloak.csv;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

final class ValueSplitter {

    private ValueSplitter() {}

    static List<String> split(Object value, String separator) {
        if (separator == null || separator.isEmpty()) {
            throw new IllegalArgumentException("The separator must not be empty");
        }
        Pattern pattern = Pattern.compile(separator, Pattern.LITERAL);
        Set<String> values = new LinkedHashSet<>();
        Collection<?> items = value instanceof Collection<?> collection ? collection : List.of(value == null ? "" : value);
        for (Object item : items) {
            if (item == null) {
                continue;
            }
            for (String part : pattern.split(item.toString())) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    values.add(trimmed);
                }
            }
        }
        return new ArrayList<>(values);
    }
}
