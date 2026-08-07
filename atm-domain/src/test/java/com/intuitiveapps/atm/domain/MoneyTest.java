package com.intuitiveapps.atm.domain;

import com.intuitiveapps.atm.domain.exception.InvalidAmountException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class MoneyTest {

    @ParameterizedTest
    @CsvSource({
            // whole amounts lose the decimals, as the sample session shows ($0, $100)
            "0,      0",
            "100,    100",
            "100.00, 100",
            // fractional amounts keep both places - $0.1 is not how money is written
            "24.50,  24.50",
            "0.10,   0.10",
            "0.05,   0.05",
    })
    @DisplayName("renders the way money is written, matching the sample output")
    void rendersCleanly(String input, String expected) {
        assertThat(Money.parse(input)).hasToString(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "1e5", "+5", "abc", "1.234", "", " ", "1,000", "$5"})
    @DisplayName("rejects anything that is not a plain non-negative decimal")
    void rejectsMalformedInput(String input) {
        assertThatExceptionOfType(InvalidAmountException.class).isThrownBy(() -> Money.parse(input));
    }

    @Test
    void rejectsNull() {
        assertThatExceptionOfType(InvalidAmountException.class).isThrownBy(() -> Money.parse(null));
    }

    @Test
    @DisplayName("rejects more than two decimal places rather than silently rounding")
    void refusesToRound() {
        assertThatExceptionOfType(InvalidAmountException.class)
                .isThrownBy(() -> Money.parse("10.999"))
                .withMessageContaining("10.999");
    }

    @Test
    @DisplayName("addition of tenths is exact - the reason this is not a double")
    void addsExactly() {
        Money total = Money.ZERO;
        for (int i = 0; i < 10; i++) {
            total = total.plus(Money.parse("0.10"));
        }

        assertThat(total).isEqualTo(Money.of(1));
        assertThat(total).hasToString("1");
    }

    @Test
    @DisplayName("equal amounts written differently are equal, and hash alike")
    void equalityIgnoresWrittenScale() {
        assertThat(Money.parse("100")).isEqualTo(Money.parse("100.00"));
        assertThat(Money.parse("100")).hasSameHashCodeAs(Money.parse("100.00"));
    }

    @Test
    void subtractionCannotProduceNegativeMoney() {
        assertThatExceptionOfType(InvalidAmountException.class)
                .isThrownBy(() -> Money.of(5).minus(Money.of(6)));
    }

    @Test
    void comparesAndPicksTheSmaller() {
        assertThat(Money.min(Money.of(30), Money.of(100))).isEqualTo(Money.of(30));
        assertThat(Money.of(100).isGreaterThan(Money.of(30))).isTrue();
        assertThat(Money.ZERO.isPositive()).isFalse();
        assertThat(Money.ZERO.isZero()).isTrue();
    }
}
