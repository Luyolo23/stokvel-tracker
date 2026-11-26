package com.stokveltracker.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

public final class Dates {
    public static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");

    private Dates() {}

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    /** Due day clamped to the month length (a due day of 31 becomes 28/29/30 when needed). */
    public static LocalDate dueDate(YearMonth month, int dueDay) {
        return month.atDay(Math.min(dueDay, month.lengthOfMonth()));
    }
}
