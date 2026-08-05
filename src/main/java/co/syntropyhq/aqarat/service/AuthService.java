package co.syntropyhq.aqarat.service;

import co.syntropyhq.aqarat.dao.AuditDao;
import co.syntropyhq.aqarat.dao.UserDao;
import co.syntropyhq.aqarat.model.AppUser;
import co.syntropyhq.aqarat.model.Role;
import co.syntropyhq.aqarat.model.UserStatus;
import co.syntropyhq.aqarat.util.Db;
import co.syntropyhq.aqarat.util.PasswordUtil;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class AuthService {

    private final UserDao userDao;
    private final AuditService auditService;

    // Login and registration never write more than the one row register()
    // creates, so most callers have no need of an AuditService - this keeps
    // them unchanged while the admin-only methods below get one to write to.
    public AuthService(UserDao userDao) {
        this(userDao, new AuditService(new AuditDao()));
    }

    public AuthService(UserDao userDao, AuditService auditService) {
        this.userDao = userDao;
        this.auditService = auditService;
    }

    // A wrong email and a wrong password both return null. Telling the caller
    // which one was wrong helps an attacker guess valid accounts.
    public AppUser login(String email, String password) throws SQLException {
        try (Connection connection = Db.get()) {
            AppUser user = userDao.findByEmail(connection, email);
            if (user == null || user.getStatus() == UserStatus.INACTIVE) {
                return null;
            }
            if (!PasswordUtil.verify(password, user.getPasswordHash())) {
                return null;
            }
            return user;
        }
    }

    /**
     * Creates a new account. A registering user always gets the CUSTOMER role
     * and ACTIVE status - nobody signs themselves up as staff. Returns null
     * if the email is already taken, since a duplicate email is a validation
     * outcome, not an exceptional one.
     */
    public AppUser register(String email, String password, String fullName, String phone)
            throws SQLException {
        try (Connection connection = Db.get()) {
            if (userDao.existsByEmail(connection, email)) {
                return null;
            }
            AppUser user = new AppUser();
            user.setEmail(email);
            user.setPasswordHash(PasswordUtil.hash(password));
            user.setFullName(fullName);
            user.setPhone(phone);
            user.setRole(Role.CUSTOMER);
            user.setStatus(UserStatus.ACTIVE);
            int id = userDao.insert(connection, user);
            user.setId(id);
            return user;
        }
    }

    public AppUser findById(int id) throws SQLException {
        return userDao.findById(id);
    }

    // Contract drafting looks a client up by the email an agent types in - the
    // same lookup login() already does, just without a password to check.
    public AppUser findByEmail(String email) throws SQLException {
        return userDao.findByEmail(email);
    }

    public List<AppUser> findAll() throws SQLException {
        return userDao.findAll();
    }

    /**
     * An admin creating an account for someone else - unlike register(), the
     * role is chosen rather than always CUSTOMER. Returns null on a duplicate
     * email, same as register().
     */
    public AppUser createAccount(String email, String password, String fullName, String phone,
            Role role) throws SQLException {
        try (Connection connection = Db.get()) {
            connection.setAutoCommit(false);
            try {
                if (userDao.existsByEmail(connection, email)) {
                    connection.rollback();
                    return null;
                }
                AppUser user = new AppUser();
                user.setEmail(email);
                user.setPasswordHash(PasswordUtil.hash(password));
                user.setFullName(fullName);
                user.setPhone(phone);
                user.setRole(role);
                user.setStatus(UserStatus.ACTIVE);
                int id = userDao.insert(connection, user);
                user.setId(id);
                auditService.record(connection, "app_user", id, "CREATE", null, role.name());
                connection.commit();
                return user;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    /**
     * Changes an account's role. Refused if the account is the last active
     * admin, so nobody can lock every admin out of the admin screens by
     * demoting themselves or the only other one (BUILD-ORDER.md phase 6).
     */
    public void changeRole(int userId, Role newRole) throws SQLException, LastAdminException {
        try (Connection connection = Db.get()) {
            connection.setAutoCommit(false);
            try {
                AppUser user = requireUser(connection, userId);
                if (isOnlyActiveAdmin(connection, user) && newRole != Role.ADMIN) {
                    throw new LastAdminException(
                        "Cannot change the role of the last active admin.");
                }
                userDao.updateRole(connection, userId, newRole);
                auditService.record(connection, "app_user", userId, "ROLE_CHANGE",
                    user.getRole().name(), newRole.name());
                connection.commit();
            } catch (SQLException | LastAdminException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    /** Same last-admin guard as changeRole(), against deactivation instead. */
    public void setStatus(int userId, UserStatus newStatus) throws SQLException, LastAdminException {
        try (Connection connection = Db.get()) {
            connection.setAutoCommit(false);
            try {
                AppUser user = requireUser(connection, userId);
                if (isOnlyActiveAdmin(connection, user) && newStatus == UserStatus.INACTIVE) {
                    throw new LastAdminException("Cannot deactivate the last active admin.");
                }
                userDao.updateStatus(connection, userId, newStatus);
                auditService.record(connection, "app_user", userId, "STATUS_CHANGE",
                    user.getStatus().name(), newStatus.name());
                connection.commit();
            } catch (SQLException | LastAdminException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    // Neither the old hash nor the new one is written to the audit trail -
    // DESIGN.md section 8 says nothing anywhere logs a password, and a hash
    // is still something nobody outside this table needs to see.
    public void resetPassword(int userId, String newPassword) throws SQLException {
        try (Connection connection = Db.get()) {
            connection.setAutoCommit(false);
            try {
                requireUser(connection, userId);
                userDao.updatePasswordHash(connection, userId, PasswordUtil.hash(newPassword));
                auditService.record(connection, "app_user", userId, "PASSWORD_RESET", null, null);
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    private boolean isOnlyActiveAdmin(Connection connection, AppUser user) throws SQLException {
        return user.getRole() == Role.ADMIN && user.getStatus() == UserStatus.ACTIVE
            && userDao.countActiveByRole(connection, Role.ADMIN) <= 1;
    }

    private AppUser requireUser(Connection connection, int userId) throws SQLException {
        AppUser user = userDao.findById(connection, userId);
        if (user == null) {
            throw new IllegalArgumentException("No user with id " + userId + ".");
        }
        return user;
    }

    // A distinct, checked type so a controller can show the refusal as a
    // message rather than a stack trace (CLAUDE.md, Errors).
    public static class LastAdminException extends Exception {

        public LastAdminException(String message) {
            super(message);
        }
    }
}
