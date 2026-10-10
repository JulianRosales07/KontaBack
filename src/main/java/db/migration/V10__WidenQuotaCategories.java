package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Amplía las categorías de conceptos de cuota/recaudo para incluir ahorros (SAVINGS) y los conceptos de préstamo.
 *
 * <p>V5 creó CHECKs sin nombre sobre la columna {@code category}; su nombre generado difiere entre PostgreSQL
 * y H2, por eso se localizan por metadatos y se reemplazan por un CHECK con nombre explícito.</p>
 */
public class V10__WidenQuotaCategories extends BaseJavaMigration {

    private static final String ALLOWED =
            "'INCOME', 'EQUITY_SAVING', 'SAVINGS', 'SOCIAL_FUND', 'LOAN_PAYMENT', 'LOAN_PRINCIPAL', 'LOAN_INTEREST'";

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        for (String table : List.of("quota_template_items", "associate_receipt_items")) {
            if (!tableExists(connection, table)) {
                continue;
            }
            for (String constraint : findCategoryChecks(connection, table)) {
                try (Statement st = connection.createStatement()) {
                    st.execute("ALTER TABLE " + table + " DROP CONSTRAINT \"" + constraint + "\"");
                }
            }
            try (Statement st = connection.createStatement()) {
                st.execute("ALTER TABLE " + table + " ADD CONSTRAINT chk_" + table + "_category CHECK (category IN (" + ALLOWED + "))");
            }
        }
    }

    private boolean tableExists(Connection connection, String table) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM information_schema.tables WHERE LOWER(table_name) = ? AND LOWER(table_schema) = LOWER(CURRENT_SCHEMA())")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * PostgreSQL expone los NOT NULL como pseudo-CHECK ("category IS NOT NULL") en information_schema;
     * se excluyen porque no se pueden eliminar con DROP CONSTRAINT.
     */
    private List<String> findCategoryChecks(Connection connection, String table) throws Exception {
        String sql = """
                SELECT tc.constraint_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.check_constraints cc
                  ON cc.constraint_name = tc.constraint_name
                 AND cc.constraint_schema = tc.constraint_schema
                WHERE LOWER(tc.table_name) = ?
                  AND LOWER(tc.table_schema) = LOWER(CURRENT_SCHEMA())
                  AND tc.constraint_type = 'CHECK'
                  AND LOWER(cc.check_clause) LIKE '%category%'
                  AND LOWER(cc.check_clause) NOT LIKE '%is not null%'
                """;
        List<String> names = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    names.add(rs.getString(1));
                }
            }
        }
        return names;
    }
}
