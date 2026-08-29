package co.syntropyhq.aqarat.ai;

import co.syntropyhq.aqarat.dao.PropertySearch;
import co.syntropyhq.aqarat.model.DealType;
import co.syntropyhq.aqarat.model.District;
import co.syntropyhq.aqarat.model.PropertyType;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class FilterMapper {

    private final List<District> districts;
    private final List<PropertyType> propertyTypes;

    public FilterMapper(List<District> districts, List<PropertyType> propertyTypes) {
        this.districts = districts != null ? districts : List.of();
        this.propertyTypes = propertyTypes != null ? propertyTypes : List.of();
    }

    public PropertySearch map(String argumentsJson, PropertySearch carriedFilters) {
        JsonObject args = null;
        if (argumentsJson != null && !argumentsJson.isBlank()) {
            try {
                JsonElement parsed = JsonParser.parseString(argumentsJson);
                if (parsed.isJsonObject()) {
                    args = parsed.getAsJsonObject();
                }
            } catch (Exception e) {
                return copy(carriedFilters);
            }
        }
        return map(args, carriedFilters);
    }

    public PropertySearch map(JsonObject args, PropertySearch carriedFilters) {
        PropertySearch search = copy(carriedFilters);
        if (args == null) {
            return search;
        }

        if (args.has("district") && !args.get("district").isJsonNull()) {
            String districtName = args.get("district").getAsString().trim();
            Integer districtId = findDistrictId(districtName);
            if (districtId != null) {
                search.setDistrictId(districtId);
            }
        }

        if (args.has("governorate") && !args.get("governorate").isJsonNull()) {
            String gov = args.get("governorate").getAsString().trim();
            if (!gov.isEmpty()) {
                search.setGovernorate(gov);
            }
        }

        if (args.has("propertyType") && !args.get("propertyType").isJsonNull()) {
            String typeName = args.get("propertyType").getAsString().trim();
            Integer typeId = findPropertyTypeId(typeName);
            if (typeId != null) {
                search.setPropertyTypeId(typeId);
            }
        }

        if (args.has("dealType") && !args.get("dealType").isJsonNull()) {
            String dealTypeStr = args.get("dealType").getAsString().trim();
            try {
                search.setDealType(DealType.valueOf(dealTypeStr.toUpperCase()));
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (args.has("minPrice") && !args.get("minPrice").isJsonNull()) {
            search.setMinPrice(new BigDecimal(args.get("minPrice").getAsString()));
        }

        if (args.has("maxPrice") && !args.get("maxPrice").isJsonNull()) {
            search.setMaxPrice(new BigDecimal(args.get("maxPrice").getAsString()));
        }

        if (args.has("bedrooms") && !args.get("bedrooms").isJsonNull()) {
            search.setBedrooms(args.get("bedrooms").getAsInt());
        }

        if (args.has("bathrooms") && !args.get("bathrooms").isJsonNull()) {
            search.setBathrooms(args.get("bathrooms").getAsInt());
        }

        if (args.has("minArea") && !args.get("minArea").isJsonNull()) {
            search.setMinArea(new BigDecimal(args.get("minArea").getAsString()));
        }

        if (args.has("maxArea") && !args.get("maxArea").isJsonNull()) {
            search.setMaxArea(new BigDecimal(args.get("maxArea").getAsString()));
        }

        if (args.has("hasParking") && !args.get("hasParking").isJsonNull()) {
            boolean val = args.get("hasParking").getAsBoolean();
            if (val) {
                search.setHasParking(true);
            }
        }

        if (args.has("hasElevator") && !args.get("hasElevator").isJsonNull()) {
            boolean val = args.get("hasElevator").getAsBoolean();
            if (val) {
                search.setHasElevator(true);
            }
        }

        if (args.has("hasBalcony") && !args.get("hasBalcony").isJsonNull()) {
            boolean val = args.get("hasBalcony").getAsBoolean();
            if (val) {
                search.setHasBalcony(true);
            }
        }

        if (args.has("isFurnished") && !args.get("isFurnished").isJsonNull()) {
            boolean val = args.get("isFurnished").getAsBoolean();
            if (val) {
                search.setIsFurnished(true);
            }
        }

        return search;
    }

    public List<String> getAppliedFilters(PropertySearch search) {
        List<String> filters = new ArrayList<>();
        if (search == null) {
            return filters;
        }
        if (search.getDistrictId() != null) {
            for (District d : districts) {
                if (d.getId() == search.getDistrictId()) {
                    filters.add("District: " + d.getName());
                    break;
                }
            }
        }
        if (search.getGovernorate() != null) {
            filters.add("Governorate: " + search.getGovernorate());
        }
        if (search.getPropertyTypeId() != null) {
            for (PropertyType t : propertyTypes) {
                if (t.getId() == search.getPropertyTypeId()) {
                    filters.add("Type: " + t.getName());
                    break;
                }
            }
        }
        if (search.getDealType() != null) {
            filters.add("Deal type: " + search.getDealType());
        }
        if (search.getMinPrice() != null) {
            filters.add("Minimum price: $" + search.getMinPrice());
        }
        if (search.getMaxPrice() != null) {
            filters.add("Maximum price: $" + search.getMaxPrice());
        }
        if (search.getBedrooms() != null) {
            filters.add("Bedrooms: " + search.getBedrooms());
        }
        if (search.getBathrooms() != null) {
            filters.add("Bathrooms: " + search.getBathrooms());
        }
        if (search.getMinArea() != null) {
            filters.add("Minimum area: " + search.getMinArea() + "m²");
        }
        if (search.getMaxArea() != null) {
            filters.add("Maximum area: " + search.getMaxArea() + "m²");
        }
        if (Boolean.TRUE.equals(search.getHasParking())) {
            filters.add("Parking");
        }
        if (Boolean.TRUE.equals(search.getHasElevator())) {
            filters.add("Elevator");
        }
        if (Boolean.TRUE.equals(search.getHasBalcony())) {
            filters.add("Balcony");
        }
        if (Boolean.TRUE.equals(search.getIsFurnished())) {
            filters.add("Furnished");
        }
        return filters;
    }

    private Integer findDistrictId(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        for (District d : districts) {
            if (d.getName() != null && d.getName().trim().equalsIgnoreCase(name)) {
                return d.getId();
            }
        }
        return null;
    }

    private Integer findPropertyTypeId(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        for (PropertyType t : propertyTypes) {
            if (t.getName() != null && t.getName().trim().equalsIgnoreCase(name)) {
                return t.getId();
            }
        }
        return null;
    }

    public static PropertySearch copy(PropertySearch source) {
        if (source == null) {
            return new PropertySearch();
        }
        PropertySearch copy = new PropertySearch();
        copy.setDistrictId(source.getDistrictId());
        copy.setPropertyTypeId(source.getPropertyTypeId());
        copy.setDealType(source.getDealType());
        copy.setMinPrice(source.getMinPrice());
        copy.setMaxPrice(source.getMaxPrice());
        copy.setBedrooms(source.getBedrooms());
        copy.setBathrooms(source.getBathrooms());
        copy.setMinArea(source.getMinArea());
        copy.setMaxArea(source.getMaxArea());
        copy.setHasParking(source.getHasParking());
        copy.setHasElevator(source.getHasElevator());
        copy.setHasBalcony(source.getHasBalcony());
        copy.setIsFurnished(source.getIsFurnished());
        copy.setGovernorate(source.getGovernorate());
        copy.setTitleContains(source.getTitleContains());
        copy.setAgentId(source.getAgentId());
        copy.setUnassignedOnly(source.getUnassignedOnly());
        return copy;
    }
}
