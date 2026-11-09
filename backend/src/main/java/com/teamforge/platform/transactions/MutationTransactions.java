package com.teamforge.platform.transactions;

import com.teamforge.platform.api.ApiException;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.SQLException;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class MutationTransactions {
    private static final Set<String> RETRYABLE=Set.of("40P01","40001","55P03","57014");
    private final TransactionTemplate transaction;
    private final JdbcTemplate jdbc;
    private final MeterRegistry meters;
    private final int lockMillis, statementMillis, attempts;

    public MutationTransactions(PlatformTransactionManager manager, JdbcTemplate jdbc, MeterRegistry meters,
        @Value("${teamforge.mutations.lock-timeout-ms:5000}") int lockMillis,
        @Value("${teamforge.mutations.statement-timeout-ms:15000}") int statementMillis,
        @Value("${teamforge.mutations.max-attempts:3}") int attempts) {
        if (lockMillis<1 || statementMillis<lockMillis || attempts<1 || attempts>5)
            throw new IllegalArgumentException("Invalid mutation transaction limits.");
        this.jdbc=jdbc;this.meters=meters;this.lockMillis=lockMillis;
        this.statementMillis=statementMillis;this.attempts=attempts;
        transaction=new TransactionTemplate(manager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(30);
    }
    public <T> T run(Supplier<T> work) {
        boolean joined=TransactionSynchronizationManager.isActualTransactionActive();
        for (int attempt=1; ; attempt++) {
            try {
                return transaction.execute(status -> {
                    jdbc.execute("SET LOCAL lock_timeout = '"+lockMillis+"ms'");
                    jdbc.execute("SET LOCAL statement_timeout = '"+statementMillis+"ms'");
                    return work.get();
                });
            } catch (RuntimeException error) {
                String state=sqlState(error);
                if (RETRYABLE.contains(state)) {
                    meters.counter("teamforge.mutations.database.conflicts","sqlstate",state).increment();
                    // Do not retry a fragment of a caller-owned transaction.
                    if (!joined && attempt<attempts) {
                        meters.counter("teamforge.mutations.retries","sqlstate",state).increment();
                        continue;
                    }
                    throw new ApiException(409,"REQUEST_BUSY","The transaction could not finish. Retry the same request and key.");
                }
                if (state.startsWith("08"))
                    throw new ApiException(503,"OUTCOME_UNKNOWN","Could not confirm the result. Retry using the same idempotency key.");
                throw error;
            }
        }
    }
    private static String sqlState(Throwable error) {
        for (Throwable cause=error; cause!=null; cause=cause.getCause()) {
            if (cause instanceof SQLException sql && sql.getSQLState()!=null) return sql.getSQLState();
            if (cause.getCause()==cause) break;
        }
        return "";
    }
}
