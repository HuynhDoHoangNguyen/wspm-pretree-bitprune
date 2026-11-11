package vn.edu.wspm.backend.services.combo.review;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import javax.sql.DataSource;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.repositories.JdbcComboAdminReader;
import vn.edu.wspm.backend.web.auth.AdminPrincipal;

/** Explicit Admin publication. Uses existing V2 lifecycle/audit columns. */
@Service
public class ComboPublicationService {
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(ComboPublicationService.class);
    private final JdbcTemplate jdbc;
    private final JdbcComboAdminReader reader;
    private final ComboReviewAuthority authority;
    private final TransactionTemplate writes;

    public record Publication(String batchId, String status, int publishedCount) {}

    public ComboPublicationService(DataSource source, JdbcComboAdminReader reader, ComboReviewAuthority authority) {
        this.jdbc = new JdbcTemplate(source);
        this.reader = reader;
        this.authority = authority;
        writes = new TransactionTemplate(new DataSourceTransactionManager(source));
        writes.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        writes.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    public Publication publish(String batchId, AdminPrincipal actor) {
        if (actor == null) throw new ApplicationException(ErrorCode.AUTHENTICATION_REQUIRED);
        if (!actor.roles().contains("ADMIN")) throw new ApplicationException(ErrorCode.ACCESS_DENIED);
        if (batchId == null || !batchId.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,39}"))
            throw new ApplicationException(ErrorCode.INVALID_ID);
        return writes.execute(tx -> {
            // Lock the entire existing batch index in one order, including the empty ACTIVE case.
            // All publications take this lock before changing any batch or combo lifecycle state.
            var batches = jdbc.queryForList("SELECT batch_id,status FROM COMBO_BATCH ORDER BY batch_id FOR UPDATE");
            var target = batches.stream().filter(b -> batchId.equals(b.get("batch_id"))).findFirst()
                    .orElseThrow(() -> new ApplicationException(ErrorCode.BATCH_NOT_FOUND));
            var admins = jdbc.queryForList("""
                    SELECT a.user_id FROM USER_ACCOUNT a JOIN USER_ROLE ur ON ur.user_id=a.user_id
                    JOIN ROLE r ON r.role_id=ur.role_id
                    WHERE a.user_id=? AND a.status='ACTIVE' AND r.code='ADMIN' FOR UPDATE
                    """, actor.userId());
            if (admins.isEmpty()) throw new ApplicationException(ErrorCode.ACCESS_DENIED);
            if ("ACTIVE".equals(target.get("status"))) {
                if (batches.stream().filter(b -> "ACTIVE".equals(b.get("status"))).count() != 1) throw blocked();
                return new Publication(batchId, "ACTIVE", jdbc.queryForObject(
                        "SELECT COUNT(*) FROM COMBO WHERE batch_id=? AND status='PUBLISHED'", Integer.class, batchId));
            }
            if (!"DRAFT".equals(target.get("status"))) throw blocked();
            var approved = jdbc.queryForList("""
                    SELECT combo_id FROM COMBO WHERE batch_id=? AND status='APPROVED'
                    AND combo_kind IN ('SAME_ORDER_BUNDLE','SEQUENTIAL_CROSS_SELL','MIXED_SEQUENCE') ORDER BY combo_id FOR UPDATE
                    """, String.class, batchId);
            if (approved.isEmpty()) throw blocked();
            // Keep the commercial facts checked by the authority stable until commit.
            jdbc.queryForList("""
                    SELECT p.product_id,cat.category_id FROM PRODUCT p
                    JOIN CATEGORY cat ON cat.category_id=p.category_id
                    WHERE p.product_id IN (SELECT i.product_id FROM COMBO_ITEM i
                        JOIN COMBO c ON c.combo_id=i.combo_id WHERE c.batch_id=?)
                    ORDER BY p.product_id FOR SHARE
                    """, batchId);
            for (String id : approved) {
                var bundle = reader.detailCurrent(id);
                if (bundle == null || !"SUCCEEDED".equals(bundle.row().runStatus())) throw blocked();
                var check = authority.verify(bundle);
                if (check == null || check.policy() == null || !check.policy().confirmed()
                        || check.evidence() == null || !check.evidence().dataAndFreshnessVerified()
                        || !check.evidence().writerLockProtocolVerified()) throw blocked();
                if (!new ComboReviewRules().assess(bundle, check).blockingIssues().isEmpty()) throw blocked();
            }
            var now = Instant.now().truncatedTo(ChronoUnit.MICROS);
            jdbc.update("UPDATE COMBO_BATCH SET status='ARCHIVED' WHERE status='ACTIVE'");
            int count = jdbc.update("""
                    UPDATE COMBO SET status='PUBLISHED',published_at=? WHERE batch_id=?
                    AND status='APPROVED' AND combo_kind IN ('SAME_ORDER_BUNDLE','SEQUENTIAL_CROSS_SELL','MIXED_SEQUENCE')
                    """, java.sql.Timestamp.from(now), batchId);
            if (count != approved.size()) throw blocked();
            jdbc.update("UPDATE COMBO_BATCH SET status='ACTIVE',activated_by=?,activated_at=? WHERE batch_id=?",
                    actor.userId(), java.sql.Timestamp.from(now), batchId);
            log.info("Combo batch published: batchId={}, publishedCount={}", batchId, count);
            return new Publication(batchId, "ACTIVE", count);
        });
    }

    private static ApplicationException blocked() {
        return new ApplicationException(ErrorCode.INVALID_RESOURCE_STATE,
                "Publication requires a DRAFT batch with verified APPROVED combos of supported kinds.");
    }
}
