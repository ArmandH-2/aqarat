package co.syntropyhq.aqarat.model;

import java.math.BigDecimal;

public class District {

    private int id;
    private String name;
    private String governorate;
    private BigDecimal avgPricePerSqm;

    public District() {
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getGovernorate() {
        return governorate;
    }

    public void setGovernorate(String governorate) {
        this.governorate = governorate;
    }

    public BigDecimal getAvgPricePerSqm() {
        return avgPricePerSqm;
    }

    public void setAvgPricePerSqm(BigDecimal avgPricePerSqm) {
        this.avgPricePerSqm = avgPricePerSqm;
    }
}
