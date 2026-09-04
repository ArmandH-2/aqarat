package co.syntropyhq.aqarat.model;

/**
 * What a piece of ownership evidence actually is.
 *
 * <p>Lebanon's registry issues the ownership record in two accepted forms -
 * the deed itself (سند ملكية) and a registry certificate (افادة عقارية) -
 * and both answer the same question, so both are TITLE_DEED. The rest cover
 * the cases where the person listing is not simply the sole registered owner:
 * an agency acting under a written mandate, someone signing under a power of
 * attorney, or heirs who have not yet re-registered.
 *
 * <p>Constant names match ck_document_type in schema.sql exactly.
 */
public enum DocumentType {
    TITLE_DEED,
    NATIONAL_ID,
    AGENCY_MANDATE,
    POWER_OF_ATTORNEY,
    INHERITANCE_DEED,
    OTHER
}
