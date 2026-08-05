package co.syntropyhq.aqarat.service;

import co.syntropyhq.aqarat.dao.AuditDao;
import co.syntropyhq.aqarat.model.AppUser;
import co.syntropyhq.aqarat.model.AuditLog;
import co.syntropyhq.aqarat.util.SessionManager;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class AuditService {

    private final AuditDao auditDao;

    public AuditService(AuditDao auditDao) {
        this.auditDao = auditDao;
    }

    /**
     * Records one line of the audit trail. Every create, update and delete
     * in the system must call this, recording who made the change, what
     * entity it touched, when, and the before and after values (DESIGN.md
     * section 1). Joins the caller's connection and does not commit - the
     * calling service owns the transaction this entry belongs to.
     */
    public void record(Connection connection, String entityType, Integer entityId,
            String action, String oldValue, String newValue) throws SQLException {
        AppUser currentUser = SessionManager.getCurrentUser();
        AuditLog auditLog = new AuditLog();
        // user_id is nullable so that a change made with nobody signed in still
        // writes a row instead of failing.
        auditLog.setUserId(currentUser == null ? null : currentUser.getId());
        auditLog.setEntityType(entityType);
        auditLog.setEntityId(entityId);
        auditLog.setAction(action);
        auditLog.setOldValue(oldValue);
        auditLog.setNewValue(newValue);
        auditDao.insert(connection, auditLog);
    }

    public List<AuditLog> findByEntity(Connection connection, String entityType, int entityId)
            throws SQLException {
        return auditDao.findByEntity(connection, entityType, entityId);
    }
}
