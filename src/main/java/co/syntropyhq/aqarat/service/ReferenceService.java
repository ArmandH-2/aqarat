package co.syntropyhq.aqarat.service;

import co.syntropyhq.aqarat.dao.DistrictDao;
import co.syntropyhq.aqarat.dao.PropertyTypeDao;
import co.syntropyhq.aqarat.model.District;
import co.syntropyhq.aqarat.model.PropertyType;
import java.sql.SQLException;
import java.util.List;

public class ReferenceService {

    private final DistrictDao districtDao;
    private final PropertyTypeDao propertyTypeDao;

    public ReferenceService(DistrictDao districtDao, PropertyTypeDao propertyTypeDao) {
        this.districtDao = districtDao;
        this.propertyTypeDao = propertyTypeDao;
    }

    public List<District> findAllDistricts() throws SQLException {
        return districtDao.findAll();
    }

    public List<PropertyType> findAllPropertyTypes() throws SQLException {
        return propertyTypeDao.findAll();
    }

    public District findDistrict(int id) throws SQLException {
        return districtDao.findById(id);
    }

    public PropertyType findPropertyType(int id) throws SQLException {
        return propertyTypeDao.findById(id);
    }
}
