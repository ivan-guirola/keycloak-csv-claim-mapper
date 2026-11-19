package io.github.ivanguirola.keycloak.csv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class ValueSplitterTest {

    @Test
    void splitsOnTheSeparator() {
        assertEquals(List.of("1", "2", "3"), ValueSplitter.split("1,2,3", ","));
    }

    @Test
    void trimsValuesAndDropsEmptyOnes() {
        assertEquals(List.of("a", "b"), ValueSplitter.split(" a , ,b,", ","));
    }

    @Test
    void keepsTheFirstOccurrenceOfARepeatedValue() {
        assertEquals(List.of("b", "a"), ValueSplitter.split("b,a,b", ","));
    }

    @Test
    void readsTheSeparatorLiterally() {
        assertEquals(List.of("a", "b.c"), ValueSplitter.split("a|b.c", "|"));
        assertEquals(List.of("a|b", "c"), ValueSplitter.split("a|b.c", "."));
    }

    @Test
    void acceptsASeparatorOfSeveralCharacters() {
        assertEquals(List.of("a", "b:c"), ValueSplitter.split("a::b:c", "::"));
    }

    @Test
    void splitsEveryElementOfAList() {
        assertEquals(List.of("a", "b", "c"), ValueSplitter.split(List.of("a,b", "c"), ","));
    }

    @Test
    void acceptsValuesThatAreNotText() {
        assertEquals(List.of("42"), ValueSplitter.split(42, ","));
        assertEquals(List.of("1", "2"), ValueSplitter.split(List.of(1, 2), ","));
    }

    @Test
    void returnsNothingForAMissingOrBlankValue() {
        assertEquals(List.of(), ValueSplitter.split(null, ","));
        assertEquals(List.of(), ValueSplitter.split("  ", ","));
    }

    @Test
    void rejectsAnEmptySeparator() {
        assertThrows(IllegalArgumentException.class, () -> ValueSplitter.split("a,b", ""));
        assertThrows(IllegalArgumentException.class, () -> ValueSplitter.split("a,b", null));
    }
}
