package com.teamforge.platform.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.teamforge.platform.api.ApiException;
import com.teamforge.platform.transactions.MutationTransactions;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class IdempotentMutations {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final MutationTransactions transactions;
    public IdempotentMutations(JdbcTemplate jdbc, ObjectMapper mapper, MutationTransactions transactions) {
        this.jdbc=jdbc;this.mapper=mapper;this.transactions=transactions;
    }
    public <T> T execute(UUID actor, String operation, String key, Object payload,
                         Class<T> responseType, Supplier<T> mutation) {
        if (actor==null || key==null || !key.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}"))
            throw new ApiException(400,"INVALID_IDEMPOTENCY_KEY","Supply an Idempotency-Key of 1–128 safe ASCII characters.");
        String hash=fingerprint(payload);
        return transactions.run(() -> {
            int claimed=jdbc.update("""
                INSERT INTO idempotency_records(actor_id,operation,idempotency_key,request_hash,state)
                VALUES (?,?,?,?,'IN_PROGRESS') ON CONFLICT DO NOTHING
                """,actor,operation,key,hash);
            var record=jdbc.queryForMap("""
                SELECT request_hash,state,response_body::text,response_status FROM idempotency_records
                WHERE actor_id=? AND operation=? AND idempotency_key=? FOR UPDATE
                """,actor,operation,key);
            if (!hash.equals(record.get("request_hash")))
                throw new ApiException(409,"IDEMPOTENCY_KEY_REUSED","This key was already used with different request content.");
            if ("COMPLETED".equals(record.get("state"))) return read((String)record.get("response_body"),responseType);
            if (claimed==0)
                throw new ApiException(409,"IDEMPOTENCY_IN_PROGRESS","This key is still in progress. Retry the same request later.");
            T result=mutation.get();
            jdbc.update("""
                UPDATE idempotency_records SET state='COMPLETED',response_status=201,
                    response_body=?::jsonb,completed_at=clock_timestamp()
                WHERE actor_id=? AND operation=? AND idempotency_key=?
                """,json(result),actor,operation,key);
            return result;
        });
    }
    private String fingerprint(Object payload) {
        try {
            byte[] bytes=json(sorted(mapper.valueToTree(payload))).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private JsonNode sorted(JsonNode node) {
        if (node.isObject()) {
            var result=mapper.createObjectNode();var keys=new TreeSet<String>();
            node.fieldNames().forEachRemaining(keys::add);
            keys.forEach(key -> result.set(key,sorted(node.get(key))));return result;
        }
        if (node.isArray()) {
            var result=mapper.createArrayNode();node.forEach(item -> result.add(sorted(item)));return result;
        }
        return node;
    }
    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException error) { throw new IllegalStateException("Cannot encode mutation outcome.",error); }
    }
    private <T> T read(String value,Class<T> type) {
        try { return mapper.readValue(value,type); }
        catch (JsonProcessingException error) { throw new IllegalStateException("Cannot decode persisted mutation outcome.",error); }
    }
}
