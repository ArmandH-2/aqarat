package co.syntropyhq.aqarat.service;

import co.syntropyhq.aqarat.dao.UserDao;
import co.syntropyhq.aqarat.model.AppUser;
import co.syntropyhq.aqarat.model.Role;
import co.syntropyhq.aqarat.model.UserStatus;
import co.syntropyhq.aqarat.util.Db;
import co.syntropyhq.aqarat.util.PasswordUtil;
import java.sql.Connection;
import java.sql.SQLException;

public class AuthService {

    private final UserDao userDao;

    public AuthService(UserDao userDao) {
        this.userDao = userDao;
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
}
