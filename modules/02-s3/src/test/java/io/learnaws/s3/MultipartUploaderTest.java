package io.learnaws.s3;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Pure logic, no Floci or network required. */
class MultipartUploaderTest {

    @Test
    void singlePartWhenContentFitsInOnePart() {
        List<long[]> plan = MultipartUploader.planParts(3_000_000, MultipartUploader.MIN_PART_SIZE);

        assertThat(plan).hasSize(1);
        assertThat(plan.get(0)).containsExactly(0, 3_000_000);
    }

    @Test
    void splitsExactMultiplesIntoEqualParts() {
        List<long[]> plan = MultipartUploader.planParts(10_000_000, 5_000_000);

        assertThat(plan).hasSize(2);
        assertThat(plan.get(0)).containsExactly(0, 5_000_000);
        assertThat(plan.get(1)).containsExactly(5_000_000, 10_000_000);
    }

    @Test
    void lastPartCanBeSmallerThanPartSize() {
        List<long[]> plan = MultipartUploader.planParts(12_000_000, 5_000_000);

        assertThat(plan).hasSize(3);
        assertThat(plan.get(0)).containsExactly(0, 5_000_000);
        assertThat(plan.get(1)).containsExactly(5_000_000, 10_000_000);
        assertThat(plan.get(2)).containsExactly(10_000_000, 12_000_000);
    }

    @Test
    void emptyContentProducesNoParts() {
        assertThat(MultipartUploader.planParts(0, MultipartUploader.MIN_PART_SIZE)).isEmpty();
    }
}
