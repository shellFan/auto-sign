package com.autosign.activity;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ActivityWorkerLockTest {
    @Test void holdsTheSameConnectionUntilEffectCompletesAndReleasesOnFailure() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Connection connection = mock(Connection.class);
        PreparedStatement acquire = mock(PreparedStatement.class);
        PreparedStatement release = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(connection.getCatalog()).thenReturn("autosign");
        when(connection.prepareStatement("SELECT GET_LOCK(?, 0)")).thenReturn(acquire);
        when(connection.prepareStatement("SELECT RELEASE_LOCK(?)")).thenReturn(release);
        when(acquire.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getInt(1)).thenReturn(1);
        when(jdbc.execute(any(ConnectionCallback.class))).thenAnswer(call ->
                ((ConnectionCallback<?>) call.getArgument(0)).doInConnection(connection));
        ActivityWorkerLock lock = new ActivityWorkerLock(jdbc);
        assertThrows(IllegalStateException.class, () -> lock.runLocked(() -> {
            try { verify(release, never()).execute(); } catch (SQLException e) { throw new RuntimeException(e); }
            throw new IllegalStateException("browser interrupted");
        }));
        verify(release).execute();
        verify(connection, never()).close(); // JdbcTemplate owns connection lifecycle.
    }

    @Test void busyLockSkipsWorkInsteadOfWaitingOrSending() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Connection connection = mock(Connection.class);
        PreparedStatement acquire = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(connection.prepareStatement("SELECT GET_LOCK(?, 0)")).thenReturn(acquire);
        when(acquire.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getInt(1)).thenReturn(0);
        when(jdbc.execute(any(ConnectionCallback.class))).thenAnswer(call ->
                ((ConnectionCallback<?>) call.getArgument(0)).doInConnection(connection));
        Runnable action = mock(Runnable.class);
        assertFalse(new ActivityWorkerLock(jdbc).runLocked(action));
        verifyNoInteractions(action);
    }
}
