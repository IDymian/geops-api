package io.github.idymian.geops.shared.audit;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Append-only record of sensitive actions (coupon redemptions now; payments and role changes later).
 * Runs inside the caller's transaction, so an action and its audit entry are saved together.
 */
@Component
public class AuditLog {

    private static final String SQL = """
            INSERT INTO audit_log (actor_id, action, entity_type, entity_id, metadata, created_at)
            VALUES (:actorId, :action, :entityType, :entityId, CAST(:metadata AS jsonb), :createdAt)
            """;

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final Clock clock;

    public AuditLog(JdbcClient jdbc, JsonMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    public void record(UUID actorId, String action, String entityType, Object entityId, Map<String, ?> metadata) {
        jdbc.sql(SQL)
                .param("actorId", actorId)
                .param("action", action)
                .param("entityType", entityType)
                .param("entityId", String.valueOf(entityId))
                .param("metadata", json.writeValueAsString(metadata))
                .param("createdAt", Timestamp.from(clock.instant()))
                .update();
    }
}
