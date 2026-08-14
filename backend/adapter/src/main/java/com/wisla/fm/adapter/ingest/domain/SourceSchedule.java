package com.wisla.fm.adapter.ingest.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Per-source scrape cadence: interval strings such as {@code 30s} or a 5/6-field cron expression.
 */
public final class SourceSchedule {

    private static final Pattern INTERVAL = Pattern.compile("^(\\d+)(ms|s|m|h)$");

    private SourceSchedule() {
    }

    public static boolean isDue(String schedule, Instant lastRun, Instant now) {
        if (lastRun == null) {
            return true;
        }
        if (schedule == null || schedule.isBlank()) {
            return true;
        }
        String trimmed = schedule.trim();
        Duration interval = parseInterval(trimmed);
        if (interval != null) {
            return !now.isBefore(lastRun.plus(interval));
        }
        return CronExpression.isDue(trimmed, lastRun, now);
    }

    static Duration parseInterval(String schedule) {
        Matcher matcher = INTERVAL.matcher(schedule);
        if (!matcher.matches()) {
            return null;
        }
        long amount = Long.parseLong(matcher.group(1));
        return switch (matcher.group(2)) {
            case "ms" -> Duration.ofMillis(amount);
            case "s" -> Duration.ofSeconds(amount);
            case "m" -> Duration.ofMinutes(amount);
            case "h" -> Duration.ofHours(amount);
            default -> null;
        };
    }

    /**
     * Minimal 5- or 6-field cron matcher (star, number, range, step, and lists).
     */
    static final class CronExpression {

        private CronExpression() {
        }

        static boolean isDue(String cron, Instant lastRun, Instant now) {
            ZonedDateTime current = now.atZone(ZoneOffset.UTC);
            if (!matches(cron, current)) {
                return false;
            }
            ZonedDateTime previous = lastRun.atZone(ZoneOffset.UTC);
            return !sameSlot(cron, previous, current);
        }

        static boolean matches(String cron, ZonedDateTime time) {
            String[] fields = cron.split("\\s+");
            if (fields.length == 5) {
                return field(fields[0], time.getMinute(), 0, 59)
                        && field(fields[1], time.getHour(), 0, 23)
                        && field(fields[2], time.getDayOfMonth(), 1, 31)
                        && field(fields[3], time.getMonthValue(), 1, 12)
                        && field(fields[4], time.getDayOfWeek().getValue() % 7, 0, 6);
            }
            if (fields.length == 6) {
                return field(fields[0], time.getSecond(), 0, 59)
                        && field(fields[1], time.getMinute(), 0, 59)
                        && field(fields[2], time.getHour(), 0, 23)
                        && field(fields[3], time.getDayOfMonth(), 1, 31)
                        && field(fields[4], time.getMonthValue(), 1, 12)
                        && field(fields[5], time.getDayOfWeek().getValue() % 7, 0, 6);
            }
            return false;
        }

        private static boolean sameSlot(String cron, ZonedDateTime previous, ZonedDateTime current) {
            String[] fields = cron.split("\\s+");
            if (fields.length == 6) {
                return previous.getSecond() == current.getSecond()
                        && previous.getMinute() == current.getMinute()
                        && previous.getHour() == current.getHour()
                        && previous.toLocalDate().equals(current.toLocalDate());
            }
            return previous.getMinute() == current.getMinute()
                    && previous.getHour() == current.getHour()
                    && previous.toLocalDate().equals(current.toLocalDate());
        }

        private static boolean field(String expr, int value, int min, int max) {
            for (String part : expr.split(",")) {
                if (matchesPart(part, value, min, max)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean matchesPart(String part, int value, int min, int max) {
            if ("*".equals(part) || "?".equals(part)) {
                return true;
            }
            if (part.startsWith("*/")) {
                int step = Integer.parseInt(part.substring(2));
                return step > 0 && (value - min) % step == 0;
            }
            int dash = part.indexOf('-');
            if (dash > 0) {
                int from = Integer.parseInt(part.substring(0, dash));
                String rest = part.substring(dash + 1);
                int slash = rest.indexOf('/');
                if (slash > 0) {
                    int to = Integer.parseInt(rest.substring(0, slash));
                    int step = Integer.parseInt(rest.substring(slash + 1));
                    return value >= from && value <= to && step > 0 && (value - from) % step == 0;
                }
                int to = Integer.parseInt(rest);
                return value >= from && value <= to;
            }
            try {
                return Integer.parseInt(part) == value;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
    }
}
