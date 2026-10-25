package com.starfinplugins.bedwars.stats;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

/** Validated ranking request; SQL fragments come only from enums, never player text. */
public final class RankingSpec {
    public enum Metric {
        WINS, GAMES, KILLS, FINAL_KILLS, DEATHS, FINAL_DEATHS, BEDS, XP, LEVEL, WIN_RATE, KD;

        public static Metric parse(String value) {
            if (value == null) return WINS;
            String normalized = value.toUpperCase(Locale.ROOT).replace('-', '_');
            if ("TOTAL_XP".equals(normalized)) normalized = "XP";
            return Metric.valueOf(normalized);
        }
    }
    public enum Period { ALL, WEEKLY, MONTHLY;
        public static Period parse(String value) {
            if (value == null) return ALL;
            String text = value.toUpperCase(Locale.ROOT);
            if ("GERAL".equals(text) || "GLOBAL".equals(text)) return ALL;
            if ("SEMANA".equals(text)) return WEEKLY;
            if ("MES".equals(text)) return MONTHLY;
            return Period.valueOf(text);
        }
    }

    public final Metric metric;
    public final Period period;
    public final String mode;
    public final int page, pageSize;
    public final long fromMillis, untilMillis;

    public RankingSpec(Metric metric, Period period, String mode, int page, int pageSize,
                       int maximumPageSize, Instant now) {
        if (metric == null || period == null || now == null || page < 1 || page > 1000
                || pageSize < 1 || pageSize > maximumPageSize || maximumPageSize > 50)
            throw new IllegalArgumentException("Consulta de ranking invalida");
        this.metric = metric;
        this.period = period;
        this.mode = MatchMode.persisted(mode);
        this.page = page;
        this.pageSize = pageSize;
        LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate from = period == Period.WEEKLY
            ? today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            : period == Period.MONTHLY ? today.withDayOfMonth(1) : null;
        this.fromMillis = from == null ? 0L : from.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        this.untilMillis = from == null ? Long.MAX_VALUE : (period == Period.WEEKLY
            ? from.plusWeeks(1) : from.plusMonths(1)).atStartOfDay(ZoneOffset.UTC)
                .toInstant().toEpochMilli();
    }

    public boolean isGlobalAllTime() { return mode == null && period == Period.ALL; }

    public String cacheKey() {
        return metric + "/" + period + "/" + mode + "/" + page + "/" + pageSize
            + "/" + fromMillis;
    }
}
