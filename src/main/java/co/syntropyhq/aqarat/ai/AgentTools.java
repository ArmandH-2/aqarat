package co.syntropyhq.aqarat.ai;

import co.syntropyhq.aqarat.dao.PropertySearch;
import co.syntropyhq.aqarat.model.District;
import co.syntropyhq.aqarat.model.Property;
import co.syntropyhq.aqarat.model.PropertyStatus;
import co.syntropyhq.aqarat.model.PropertyType;
import co.syntropyhq.aqarat.service.PropertyService;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AgentTools {

    public static final String SEARCH_PROPERTIES_SCHEMA = """
        {
          "type": "function",
          "function": {
            "name": "search_properties",
            "description": "Search published Aqarat listings. Every parameter is optional; omit any the user did not state. Returns up to five properties ranked by how well they fit.",
            "parameters": {
              "type": "object",
              "properties": {
                "district":      { "type": "string",  "description": "District name, e.g. Achrafieh. Omit if not stated." },
                "governorate":   { "type": "string",  "description": "Governorate name, when the user named a region rather than a district." },
                "propertyType":  { "type": "string",  "description": "Property type name, e.g. Apartment, Villa, Office." },
                "dealType":      { "type": "string",  "enum": ["SALE", "RENT"] },
                "minPrice":      { "type": "number",  "description": "USD. Full sale price when dealType is SALE, monthly rent when RENT." },
                "maxPrice":      { "type": "number",  "description": "USD. Same meaning as minPrice." },
                "bedrooms":      { "type": "integer" },
                "bathrooms":     { "type": "integer" },
                "minArea":       { "type": "number",  "description": "Square metres." },
                "maxArea":       { "type": "number",  "description": "Square metres." },
                "hasParking":    { "type": "boolean", "description": "Send true only when the user asked for parking." },
                "hasElevator":   { "type": "boolean" },
                "hasBalcony":    { "type": "boolean" },
                "isFurnished":   { "type": "boolean" }
              },
              "additionalProperties": false
            }
          }
        }
        """;

    public static final String GET_PROPERTY_DETAILS_SCHEMA = """
        {
          "type": "function",
          "function": {
            "name": "get_property_details",
            "description": "Fetch the full details of one published property by id, to answer a question about a property already suggested. Use this rather than recalling details from earlier in the conversation.",
            "parameters": {
              "type": "object",
              "properties": {
                "propertyId": { "type": "integer", "description": "The id from a previous search_properties result." }
              },
              "required": ["propertyId"],
              "additionalProperties": false
            }
          }
        }
        """;

    private final PropertyService propertyService;
    private final FilterMapper filterMapper;
    private final Map<Integer, District> districtsById = new HashMap<>();
    private final Map<Integer, PropertyType> typesById = new HashMap<>();
    private final Gson gson = new Gson();

    private PropertySearch lastAppliedFilters = new PropertySearch();
    private List<Property> lastRankedResults = new ArrayList<>();

    public AgentTools(PropertyService propertyService, List<District> districts, List<PropertyType> propertyTypes) {
        this.propertyService = propertyService;
        this.filterMapper = new FilterMapper(districts, propertyTypes);
        if (districts != null) {
            for (District d : districts) {
                districtsById.put(d.getId(), d);
            }
        }
        if (propertyTypes != null) {
            for (PropertyType t : propertyTypes) {
                typesById.put(t.getId(), t);
            }
        }
    }

    public static List<JsonObject> getToolSchemas() {
        return List.of(
            JsonParser.parseString(SEARCH_PROPERTIES_SCHEMA).getAsJsonObject(),
            JsonParser.parseString(GET_PROPERTY_DETAILS_SCHEMA).getAsJsonObject()
        );
    }

    public String execute(String name, String argumentsJson) throws SQLException {
        if ("search_properties".equals(name)) {
            return executeSearchProperties(argumentsJson);
        } else if ("get_property_details".equals(name)) {
            return executeGetPropertyDetails(argumentsJson);
        } else {
            JsonObject err = new JsonObject();
            err.addProperty("error", "Unknown tool: " + name);
            return gson.toJson(err);
        }
    }

    private String executeSearchProperties(String argumentsJson) throws SQLException {
        PropertySearch search = filterMapper.map(argumentsJson, lastAppliedFilters);
        lastAppliedFilters = search;

        List<Property> rawResults = propertyService.searchPublished(search, 0, 50);
        List<Property> topRanked = Ranker.rank(rawResults, search, 5);
        this.lastRankedResults = topRanked;

        JsonObject root = new JsonObject();
        root.addProperty("count", topRanked.size());

        JsonArray resultsArray = new JsonArray();
        for (Property p : topRanked) {
            resultsArray.add(formatPropertyCompact(p));
        }
        root.add("results", resultsArray);

        if (topRanked.isEmpty()) {
            List<String> applied = filterMapper.getAppliedFilters(search);
            JsonArray appliedArray = new JsonArray();
            for (String f : applied) {
                appliedArray.add(f);
            }
            root.add("filtersApplied", appliedArray);
        }

        return gson.toJson(root);
    }

    private String executeGetPropertyDetails(String argumentsJson) throws SQLException {
        JsonObject args = JsonParser.parseString(argumentsJson).getAsJsonObject();
        int propertyId = args.get("propertyId").getAsInt();

        Property property = propertyService.findById(propertyId);
        if (property == null || property.getStatus() != PropertyStatus.AVAILABLE) {
            JsonObject err = new JsonObject();
            err.addProperty("error", "Property not found or not currently available.");
            return gson.toJson(err);
        }

        return gson.toJson(formatPropertyCompact(property));
    }

    private JsonObject formatPropertyCompact(Property p) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", p.getId());
        obj.addProperty("title", p.getTitle());
        District d = districtsById.get(p.getDistrictId());
        obj.addProperty("district", d != null ? d.getName() : "Unknown");
        PropertyType t = typesById.get(p.getPropertyTypeId());
        obj.addProperty("propertyType", t != null ? t.getName() : "Property");
        obj.addProperty("dealType", p.getDealType() != null ? p.getDealType().name() : null);
        obj.addProperty("askingPrice", p.getAskingPrice());
        obj.addProperty("areaSqm", p.getAreaSqm());
        obj.addProperty("bedrooms", p.getBedrooms());
        obj.addProperty("bathrooms", p.getBathrooms());
        obj.addProperty("hasParking", p.isHasParking());
        obj.addProperty("hasElevator", p.isHasElevator());
        obj.addProperty("hasBalcony", p.isHasBalcony());
        obj.addProperty("isFurnished", p.isFurnished());
        return obj;
    }

    public PropertySearch getLastAppliedFilters() {
        return lastAppliedFilters;
    }

    public void setLastAppliedFilters(PropertySearch lastAppliedFilters) {
        this.lastAppliedFilters = lastAppliedFilters;
    }

    public List<Property> getLastRankedResults() {
        return lastRankedResults;
    }
}
