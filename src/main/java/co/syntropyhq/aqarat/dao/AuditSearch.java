package co.syntropyhq.aqarat.dao;

import java.time.LocalDateTime;

// The optional filters for AuditDao.search. Every field may be left null,
// meaning "do not filter on this" - same shape as PropertySearch.
public class AuditSearch {

    private String entityType;
    private String action;
    private Integer userId;
    private LocalDateTime createdFrom;
    private LocalDateTime createdTo;

    public AuditSearch() {
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public LocalDateTime getCreatedFrom() {
        return createdFrom;
    }

    public void setCreatedFrom(LocalDateTime createdFrom) {
        this.createdFrom = createdFrom;
    }

    public LocalDateTime getCreatedTo() {
        return createdTo;
    }

    public void setCreatedTo(LocalDateTime createdTo) {
        this.createdTo = createdTo;
    }
}
