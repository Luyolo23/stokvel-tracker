package com.stokveltracker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class DatesTest {

    @Test
    void dueDayWithinMonthIsUsedAsIs() {
        assertEquals(LocalDate.of(2026, 11, 5), Dates.dueDate(YearMonth.of(2026, 11), 5));
    }

    @Test
    void dueDayBeyondMonthLengthIsClamped() {
        assertEquals(LocalDate.of(2026, 2, 28), Dates.dueDate(YearMonth.of(2026, 2), 31));
        assertEquals(LocalDate.of(2028, 2, 29), Dates.dueDate(YearMonth.of(2028, 2), 31));
        assertEquals(LocalDate.of(2026, 4, 30), Dates.dueDate(YearMonth.of(2026, 4), 31));
    }
}
