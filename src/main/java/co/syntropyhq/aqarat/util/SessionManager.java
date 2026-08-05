package co.syntropyhq.aqarat.util;

import co.syntropyhq.aqarat.model.AppUser;
import co.syntropyhq.aqarat.model.Role;

public final class SessionManager {

    private static AppUser currentUser;

    private SessionManager() {
    }

    public static void login(AppUser user) {
        currentUser = user;
    }

    public static void logout() {
        currentUser = null;
    }

    public static AppUser getCurrentUser() {
        return currentUser;
    }

    public static boolean isAdmin() {
        return currentUser != null && currentUser.getRole() == Role.ADMIN;
    }

    public static boolean isAgent() {
        return currentUser != null && currentUser.getRole() == Role.AGENT;
    }

    public static boolean isCustomer() {
        return currentUser != null && currentUser.getRole() == Role.CUSTOMER;
    }
}
