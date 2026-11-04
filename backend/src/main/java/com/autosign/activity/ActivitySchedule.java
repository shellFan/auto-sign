package com.autosign.activity;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Random;

public final class ActivitySchedule {
    private ActivitySchedule() {
    }

    public static LocalDateTime dueAt(LocalDateTime start, LocalDateTime end,
                                      int index, int total, Random random) {
        if (start == null || end == null || random == null) {
            throw new IllegalArgumentException("start, end and random are required");
        }
        if (total <= 0 || index < 0 || index >= total || end.isBefore(start)) {
            throw new IllegalArgumentException("invalid schedule range");
        }
        long seconds = Duration.between(start, end).getSeconds();
        if (seconds == 0) {
            return start;
        }
        long bucketStart = seconds * index / total;
        long bucketEndExclusive = seconds * (index + 1L) / total;
        long width = bucketEndExclusive - bucketStart;
        long offset = width <= 1 ? 0 : Math.min(width - 1,
                (long) Math.floor(random.nextDouble() * width));
        return start.plusSeconds(Math.min(seconds - 1, bucketStart + offset));
    }

    public static int cooldownSeconds(int pending, long remainingSeconds) {
        if (pending <= 0) {
            return 60;
        }
        long capacityInterval = remainingSeconds / pending;
        if (capacityInterval < 5) {
            return 5;
        }
        if (capacityInterval > 60) {
            return 60;
        }
        return (int) capacityInterval;
    }
}
