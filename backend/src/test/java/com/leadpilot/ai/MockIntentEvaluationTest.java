package com.leadpilot.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

class MockIntentEvaluationTest {
    private record Example(String id, Intent intent, String enquiry) {}

    @Test
    void evaluatesMockIntentAgainstFictionalEnquiries() throws Exception {
        List<Example> examples;
        try (InputStream fixtures = getClass().getResourceAsStream("/ai-intent-evaluation.json")) {
            examples = new ObjectMapper().readValue(fixtures, new TypeReference<>() {});
        }

        assertThat(examples).hasSizeGreaterThanOrEqualTo(30);
        assertThat(examples).extracting(Example::id).doesNotHaveDuplicates();
        assertThat(examples).extracting(Example::intent).contains(Intent.values());
        int[][] matrix = new int[Intent.values().length][Intent.values().length];
        MockLeadAiProvider provider = new MockLeadAiProvider();
        int correct = 0;
        for (Example example : examples) {
            Intent expected = example.intent();
            Intent actual = provider.analyze(example.enquiry()).intent();
            matrix[expected.ordinal()][actual.ordinal()]++;
            if (expected == actual) {
                correct++;
            } else {
                System.out.printf("Mismatch %-3s expected=%-6s predicted=%s%n", example.id(), expected, actual);
            }
        }

        System.out.printf("Mock intent evaluation: %d/%d correct (%.1f%%)%n",
                correct, examples.size(), 100.0 * correct / examples.size());
        System.out.println("Expected \\ Predicted    HIGH MEDIUM LOW");
        for (Intent expected : Intent.values()) {
            int[] row = matrix[expected.ordinal()];
            System.out.printf("%-23s %4d %6d %3d%n", expected, row[0], row[1], row[2]);
        }
    }
}
