package org.example.entity;

 import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum AdditionalRoleName {
    SELECT_ROLE("SELECT"),
    INSERT_ROLE("INSERT"),
    UPDATE_ROLE("UPDATE"),
    DELETE_ROLE("DELETE");

    private final String sqlVerb;

    AdditionalRoleName(String sqlVerb) {
        this.sqlVerb = sqlVerb;
    }

    public String sqlVerb() {
        return sqlVerb;
    }

    /** The permission a statement starting with this SQL verb needs, if any permission allows it at all. */
    public static Optional<AdditionalRoleName> forSqlVerb(String verb) {
        String wanted = verb.toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(permission -> permission.sqlVerb.equals(wanted)).findFirst();
    }
}
