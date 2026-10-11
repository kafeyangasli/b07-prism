package com.github.kafeyangasli.prism.shared.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThat;

class OperatingDaysTest {
    @ParameterizedTest
    @CsvSource({"2026-10-16T07:00,2026-10-17T00:00,false",
            "2026-10-16T07:00,2026-10-17T00:30,true",
            "2026-10-17T07:00,2026-10-17T08:00,true",
            "2026-10-18T23:30,2026-10-19T00:00,true",
            "2026-10-19T00:00,2026-10-19T07:00,false",
            "2026-10-16T07:00,2026-10-19T08:00,true",
            "2026-12-25T07:00,2026-12-25T20:00,false"})
    void halfOpenBoundariesAndWeekdayHolidays(String start, String end, boolean weekend) {
        assertThat(OperatingDays.includesWeekend(LocalDateTime.parse(start), LocalDateTime.parse(end))).isEqualTo(weekend);
    }
}
