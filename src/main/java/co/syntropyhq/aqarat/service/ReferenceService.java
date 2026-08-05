package co.syntropyhq.aqarat.service;

import co.syntropyhq.aqarat.dao.AuditDao;
import co.syntropyhq.aqarat.dao.DistrictDao;
import co.syntropyhq.aqarat.dao.PropertyTypeDao;
import co.syntropyhq.aqarat.dao.SystemSettingDao;
import co.syntropyhq.aqarat.model.District;
import co.syntropyhq.aqarat.model.PropertyType;
import co.syntropyhq.aqarat.model.SystemSetting;
import co.syntropyhq.aqarat.util.Db;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

// Districts, property types and system settings are all read-mostly lookup
// tables edited from one screen (Reference.fxml), so one service covers the
// writes for all three rather than a fourth class every controller would
// need to construct just for settings.
public class ReferenceService {

    private final DistrictDao districtDao;
    private final PropertyTypeDao propertyTypeDao;
    private final SystemSettingDao systemSettingDao;
    private final AuditService auditService;

    public ReferenceService(DistrictDao districtDao, PropertyTypeDao propertyTypeDao) {
        this(districtDao, propertyTypeDao, new SystemSettingDao(),
            new AuditService(new AuditDao()));
    }

    public ReferenceService(DistrictDao districtDao, PropertyTypeDao propertyTypeDao,
            SystemSettingDao systemSettingDao, AuditService auditService) {
        this.districtDao = districtDao;
        this.propertyTypeDao = propertyTypeDao;
        this.systemSettingDao = systemSettingDao;
        this.auditService = auditService;
    }

    public List<District> findAllDistricts() throws SQLException {
        return districtDao.findAll();
    }

    public List<PropertyType> findAllPropertyTypes() throws SQLException {
        return propertyTypeDao.findAll();
    }

    public List<SystemSetting> findAllSettings() throws SQLException {
        return systemSettingDao.findAll();
    }

    public District findDistrict(int id) throws SQLException {
        return districtDao.findById(id);
    }

    public PropertyType findPropertyType(int id) throws SQLException {
        return propertyTypeDao.findById(id);
    }

    public District insertDistrict(District district) throws SQLException {
        try (Connection connection = Db.get()) {
            connection.setAutoCommit(false);
            try {
                int id = districtDao.insert(connection, district);
                district.setId(id);
                auditService.record(connection, "district", id, "CREATE", null,
                    district.getName());
                connection.commit();
                return district;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    // avg_price_per_sqm feeds the valuation engine, so the audit trail
    // records both the before and after price, not just that a row changed.
    public void updateDistrict(District district) throws SQLException {
        try (Connection connection = Db.get()) {
            connection.setAutoCommit(false);
            try {
                District before = districtDao.findById(connection, district.getId());
                districtDao.update(connection, district);
                auditService.record(connection, "district", district.getId(), "UPDATE",
                    before == null ? null : before.getAvgPricePerSqm().toPlainString(),
                    district.getAvgPricePerSqm().toPlainString());
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    public PropertyType insertPropertyType(PropertyType propertyType) throws SQLException {
        try (Connection connection = Db.get()) {
            connection.setAutoCommit(false);
            try {
                int id = propertyTypeDao.insert(connection, propertyType);
                propertyType.setId(id);
                auditService.record(connection, "property_type", id, "CREATE", null,
                    propertyType.getName());
                connection.commit();
                return propertyType;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    // ContractService.draft() reads commission_rate_percent fresh from this
    // table on every call (no cache anywhere in the project), so a change
    // saved here is what the next contract drafted uses (BUILD-ORDER.md
    // phase 6 done-when).
    public void updateSetting(String settingKey, String value) throws SQLException {
        try (Connection connection = Db.get()) {
            connection.setAutoCommit(false);
            try {
                SystemSetting before = systemSettingDao.findByKey(connection, settingKey);
                systemSettingDao.updateValue(connection, settingKey, value);
                auditService.record(connection, "system_setting", null, "UPDATE",
                    before == null ? null : before.getValue(), value);
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }
}
