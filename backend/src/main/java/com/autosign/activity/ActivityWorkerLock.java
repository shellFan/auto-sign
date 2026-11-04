package com.autosign.activity;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Objects;

/** The dedicated JDBC connection holds this lock across claim AND browser effect. */
@Component
public class ActivityWorkerLock {
    private final JdbcTemplate jdbc;

    public ActivityWorkerLock(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean runLocked(Runnable operation) {
        Boolean ran = jdbc.execute((ConnectionCallback<Boolean>) connection -> {
            String key = "autosign:weibo:" + Integer.toHexString(Objects.hashCode(connection.getCatalog()));
            try (PreparedStatement acquire = connection.prepareStatement("SELECT GET_LOCK(?, 0)")) {
                acquire.setString(1, key);
                try (ResultSet result = acquire.executeQuery()) {
                    if (!result.next() || result.getInt(1) != 1 || result.wasNull()) return false;
                }
            }
            try {
                operation.run();
                return true;
            } finally {
                try (PreparedStatement release = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
                    release.setString(1, key);
                    release.execute();
                }
            }
        });
        return Boolean.TRUE.equals(ran);
    }
}
