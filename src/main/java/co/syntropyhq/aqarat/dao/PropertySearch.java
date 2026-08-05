package co.syntropyhq.aqarat.dao;

import co.syntropyhq.aqarat.model.DealType;
import java.math.BigDecimal;

// The optional filters for PropertyDao.search. Every field may be left null,
// meaning "do not filter on this". This is a plain holder so the search
// method does not take nine arguments - not a builder, just fields.
public class PropertySearch {

    private Integer districtId;
    private Integer propertyTypeId;
    private DealType dealType;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private Integer bedrooms;
    private BigDecimal minArea;
    private BigDecimal maxArea;
    private String titleContains;
    private Integer agentId;
    private Boolean unassignedOnly;

    public PropertySearch() {
    }

    public Integer getAgentId() {
        return agentId;
    }

    public void setAgentId(Integer agentId) {
        this.agentId = agentId;
    }

    public Boolean getUnassignedOnly() {
        return unassignedOnly;
    }

    public void setUnassignedOnly(Boolean unassignedOnly) {
        this.unassignedOnly = unassignedOnly;
    }

    public Integer getDistrictId() {
        return districtId;
    }

    public void setDistrictId(Integer districtId) {
        this.districtId = districtId;
    }

    public Integer getPropertyTypeId() {
        return propertyTypeId;
    }

    public void setPropertyTypeId(Integer propertyTypeId) {
        this.propertyTypeId = propertyTypeId;
    }

    public DealType getDealType() {
        return dealType;
    }

    public void setDealType(DealType dealType) {
        this.dealType = dealType;
    }

    public BigDecimal getMinPrice() {
        return minPrice;
    }

    public void setMinPrice(BigDecimal minPrice) {
        this.minPrice = minPrice;
    }

    public BigDecimal getMaxPrice() {
        return maxPrice;
    }

    public void setMaxPrice(BigDecimal maxPrice) {
        this.maxPrice = maxPrice;
    }

    public Integer getBedrooms() {
        return bedrooms;
    }

    public void setBedrooms(Integer bedrooms) {
        this.bedrooms = bedrooms;
    }

    public BigDecimal getMinArea() {
        return minArea;
    }

    public void setMinArea(BigDecimal minArea) {
        this.minArea = minArea;
    }

    public BigDecimal getMaxArea() {
        return maxArea;
    }

    public void setMaxArea(BigDecimal maxArea) {
        this.maxArea = maxArea;
    }

    public String getTitleContains() {
        return titleContains;
    }

    public void setTitleContains(String titleContains) {
        this.titleContains = titleContains;
    }
}
