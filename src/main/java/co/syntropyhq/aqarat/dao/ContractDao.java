package co.syntropyhq.aqarat.dao;

import co.syntropyhq.aqarat.model.Contract;
import co.syntropyhq.aqarat.model.ContractStatus;
import co.syntropyhq.aqarat.model.ContractType;
import co.syntropyhq.aqarat.model.PaymentFrequency;
import co.syntropyhq.aqarat.util.Db;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ContractDao {

    private static final String COLUMNS = """
        id, property_id, owner_id, client_id, agent_id, contract_type, total_amount,
        monthly_rent, start_date, end_date, term_months, payment_frequency, installment_count,
        commission_rate, commission_amount, status, created_at, activated_at, closed_at""";

    public int insert(Connection connection, Contract contract) throws SQLException {
        String sql = """
            INSERT INTO contract (property_id, owner_id, client_id, agent_id, contract_type,
                total_amount, monthly_rent, start_date, end_date, term_months, payment_frequency,
                installment_count, commission_rate, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement statement =
                connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, contract.getPropertyId());
            statement.setInt(2, contract.getOwnerId());
            statement.setInt(3, contract.getClientId());
            statement.setInt(4, contract.getAgentId());
            statement.setString(5, contract.getContractType().name());
            statement.setBigDecimal(6, contract.getTotalAmount());
            statement.setBigDecimal(7, contract.getMonthlyRent());
            statement.setObject(8, contract.getStartDate());
            statement.setObject(9, contract.getEndDate());
            statement.setObject(10, contract.getTermMonths());
            statement.setString(11, contract.getPaymentFrequency().name());
            statement.setInt(12, contract.getInstallmentCount());
            statement.setBigDecimal(13, contract.getCommissionRate());
            statement.setString(14, contract.getStatus().name());
            statement.executeUpdate();
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                generatedKeys.next();
                return generatedKeys.getInt(1);
            }
        }
    }

    public Contract findById(Connection connection, int id) throws SQLException {
        String sql = ("""
            SELECT %s
            FROM contract
            WHERE id = ?
            """).formatted(COLUMNS);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapRow(resultSet);
                }
                return null;
            }
        }
    }

    public Contract findById(int id) throws SQLException {
        try (Connection connection = Db.get()) {
            return findById(connection, id);
        }
    }

    public List<Contract> findByProperty(Connection connection, int propertyId) throws SQLException {
        String sql = ("""
            SELECT %s
            FROM contract
            WHERE property_id = ?
            ORDER BY created_at DESC
            """).formatted(COLUMNS);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, propertyId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return mapRows(resultSet);
            }
        }
    }

    // A client sees only their own contracts - client_id is bound into the
    // WHERE clause here, not applied by filtering a full result set
    // afterwards (DESIGN.md section 8).
    public List<Contract> findByClient(Connection connection, int clientId) throws SQLException {
        String sql = ("""
            SELECT %s
            FROM contract
            WHERE client_id = ?
            ORDER BY created_at DESC
            """).formatted(COLUMNS);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, clientId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return mapRows(resultSet);
            }
        }
    }

    public List<Contract> findByAgent(Connection connection, int agentId) throws SQLException {
        String sql = ("""
            SELECT %s
            FROM contract
            WHERE agent_id = ?
            ORDER BY created_at DESC
            """).formatted(COLUMNS);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, agentId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return mapRows(resultSet);
            }
        }
    }

    public List<Contract> findByStatus(Connection connection, ContractStatus status)
            throws SQLException {
        String sql = ("""
            SELECT %s
            FROM contract
            WHERE status = ?
            ORDER BY created_at DESC
            """).formatted(COLUMNS);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            try (ResultSet resultSet = statement.executeQuery()) {
                return mapRows(resultSet);
            }
        }
    }

    // Activation is the one place commission_amount and activated_at are ever
    // written, alongside the status flip - all three describe the same event
    // (DESIGN.md section 6) so one statement writes all three together.
    public void activate(Connection connection, int contractId, BigDecimal commissionAmount,
            LocalDateTime activatedAt) throws SQLException {
        String sql = """
            UPDATE contract
            SET status = ?, commission_amount = ?, activated_at = ?
            WHERE id = ?
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ContractStatus.ACTIVE.name());
            statement.setBigDecimal(2, commissionAmount);
            statement.setObject(3, activatedAt);
            statement.setInt(4, contractId);
            statement.executeUpdate();
        }
    }

    public void close(Connection connection, int contractId, ContractStatus status,
            LocalDateTime closedAt) throws SQLException {
        String sql = "UPDATE contract SET status = ?, closed_at = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            statement.setObject(2, closedAt);
            statement.setInt(3, contractId);
            statement.executeUpdate();
        }
    }

    private List<Contract> mapRows(ResultSet resultSet) throws SQLException {
        List<Contract> results = new ArrayList<>();
        while (resultSet.next()) {
            results.add(mapRow(resultSet));
        }
        return results;
    }

    private Contract mapRow(ResultSet resultSet) throws SQLException {
        Contract contract = new Contract();
        contract.setId(resultSet.getInt("id"));
        contract.setPropertyId(resultSet.getInt("property_id"));
        contract.setOwnerId(resultSet.getInt("owner_id"));
        contract.setClientId(resultSet.getInt("client_id"));
        contract.setAgentId(resultSet.getInt("agent_id"));
        contract.setContractType(ContractType.valueOf(resultSet.getString("contract_type")));
        contract.setTotalAmount(resultSet.getBigDecimal("total_amount"));
        contract.setMonthlyRent(resultSet.getBigDecimal("monthly_rent"));
        contract.setStartDate(resultSet.getObject("start_date", LocalDate.class));
        contract.setEndDate(resultSet.getObject("end_date", LocalDate.class));
        contract.setTermMonths(resultSet.getObject("term_months", Integer.class));
        contract.setPaymentFrequency(
            PaymentFrequency.valueOf(resultSet.getString("payment_frequency")));
        contract.setInstallmentCount(resultSet.getInt("installment_count"));
        contract.setCommissionRate(resultSet.getBigDecimal("commission_rate"));
        contract.setCommissionAmount(resultSet.getBigDecimal("commission_amount"));
        contract.setStatus(ContractStatus.valueOf(resultSet.getString("status")));
        contract.setCreatedAt(resultSet.getObject("created_at", LocalDateTime.class));
        contract.setActivatedAt(resultSet.getObject("activated_at", LocalDateTime.class));
        contract.setClosedAt(resultSet.getObject("closed_at", LocalDateTime.class));
        return contract;
    }
}
