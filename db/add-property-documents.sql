/* ==========================================================================
   Aqarat - add ownership evidence to an existing database

   schema.sql drops and recreates everything, which throws away the data a
   demo has already built up. Run this instead against a database that is
   already populated. It is idempotent: running it twice changes nothing.
   ========================================================================== */

USE Aqarat;
GO

IF OBJECT_ID('dbo.property_document', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.property_document (
        id            INT IDENTITY(1,1) NOT NULL,
        property_id   INT          NOT NULL,
        doc_type      VARCHAR(20)  NOT NULL,
        file_path     VARCHAR(300) NOT NULL,
        original_name VARCHAR(200) NOT NULL,
        uploaded_by   INT          NOT NULL,
        uploaded_at   DATETIME2(0) NOT NULL CONSTRAINT df_document_uploaded DEFAULT SYSUTCDATETIME(),
        verified_by   INT          NULL,
        verified_at   DATETIME2(0) NULL,

        CONSTRAINT pk_property_document  PRIMARY KEY (id),
        CONSTRAINT fk_document_property  FOREIGN KEY (property_id)
            REFERENCES dbo.property(id) ON DELETE CASCADE,
        CONSTRAINT fk_document_uploader  FOREIGN KEY (uploaded_by) REFERENCES dbo.app_user(id),
        CONSTRAINT fk_document_verifier  FOREIGN KEY (verified_by) REFERENCES dbo.app_user(id),
        CONSTRAINT ck_document_type CHECK (doc_type IN
            ('TITLE_DEED','NATIONAL_ID','AGENCY_MANDATE','POWER_OF_ATTORNEY',
             'INHERITANCE_DEED','OTHER')),
        CONSTRAINT ck_document_verified CHECK (
            (verified_by IS NULL AND verified_at IS NULL)
         OR (verified_by IS NOT NULL AND verified_at IS NOT NULL))
    );

    CREATE INDEX ix_document_property ON dbo.property_document(property_id);

    PRINT 'Created dbo.property_document.';
END
ELSE
    PRINT 'dbo.property_document already exists - nothing to do.';
GO
