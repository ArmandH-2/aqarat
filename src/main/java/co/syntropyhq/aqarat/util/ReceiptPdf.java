package co.syntropyhq.aqarat.util;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Writes the receipt to a file.
 *
 * <p>JavaFX can print but cannot save: on Windows it is the "Microsoft Print to
 * PDF" driver that produces the file, so on a machine with no printer installed
 * there was no way to keep a receipt at all. A receipt is the one artefact a
 * client files, so it has to be a document rather than a print job.
 *
 * <p>Drawn to match the receipt on screen rather than reformatted for paper —
 * the same masthead, the same figure at display size, the same ruled lines — so
 * the file a client opens is recognisably the thing they were shown.
 */
final class ReceiptPdf {

    /* The brand, in the colour space a PDF works in. Kept beside the CSS values
       they mirror: -c-primary-deep, -c-brass-bright, -c-text-*, -c-border-subtle. */
    private static final Color INK = new Color(0x1A, 0x18, 0x15);
    private static final Color INK_SOFT = new Color(0x5A, 0x55, 0x4C);
    private static final Color INK_MUTED = new Color(0x8A, 0x83, 0x78);
    private static final Color GREEN_DEEP = new Color(0x04, 0x1F, 0x14);
    private static final Color BRASS = new Color(0xC9, 0xA2, 0x27);
    private static final Color CREAM = new Color(0xFF, 0xFE, 0xFC);
    private static final Color SUBTLE = new Color(0xF3, 0xEC, 0xDB);
    private static final Color RULE = new Color(0xDF, 0xD5, 0xBF);

    private static final float WIDTH = 460;

    private final BaseFont serif = load("/fonts/InstrumentSerif-Regular.ttf", BaseFont.TIMES_ROMAN);
    private final BaseFont sans = load("/fonts/Inter-Regular.ttf", BaseFont.HELVETICA);
    private final BaseFont sansBold = load("/fonts/Inter-SemiBold.ttf", BaseFont.HELVETICA_BOLD);

    private ReceiptPdf() {
    }

    /** Writes {@code receipt} to {@code target}, overwriting whatever is there. */
    static void write(Receipt receipt, File target) throws IOException {
        new ReceiptPdf().draw(receipt, target);
    }

    private void draw(Receipt receipt, File target) throws IOException {
        Document document = new Document(PageSize.A4, 68, 68, 64, 64);
        try (FileOutputStream out = new FileOutputStream(target)) {
            PdfWriter.getInstance(document, out);
            document.open();
            document.add(masthead(receipt));
            document.add(figure(receipt));
            if (!receipt.lines.isEmpty()) {
                document.add(detail(receipt));
            }
            if (receipt.remaining != null) {
                document.add(balance(receipt));
            }
            document.add(footNote(receipt));
            document.close();
        } catch (DocumentException e) {
            // The only realistic cause is a target the file system refused, which
            // the caller reports; the PDF library's own type does not survive the
            // boundary.
            throw new IOException("The receipt could not be written.", e);
        }
    }

    /* ---------------------------------------------------------------- */

    /*
     * One cell, holding a borderless table, rather than two cells side by side.
     * Two adjacent cells sharing a background colour render a hairline seam down
     * the middle of the band; a single fill behind a nested table does not.
     */
    private PdfPTable masthead(Receipt receipt) {
        Phrase brand = paragraph("Aqarat\n", serif, 21, CREAM);
        brand.add(text("BEIRUT", sansBold, 8, new Color(0x7E, 0x91, 0x87)));
        // A two-line phrase mixing 21pt and 8pt keeps the smaller line's leading,
        // which drops "BEIRUT" onto the edge of the band and clips it.
        brand.setLeading(28);

        Phrase kind = paragraph(receipt.title + "\n", sansBold, 8, BRASS);
        kind.add(text(receipt.number, sans, 11, CREAM));

        PdfPTable inner = frame(new float[] {55, 45});
        inner.setTotalWidth(WIDTH - 40);
        inner.addCell(cell(brand));
        PdfPCell reference = cell(kind);
        reference.setHorizontalAlignment(Element.ALIGN_RIGHT);
        inner.addCell(reference);

        PdfPCell band = new PdfPCell(inner);
        band.setBorder(Rectangle.NO_BORDER);
        band.setBackgroundColor(GREEN_DEEP);
        band.setPadding(20);

        PdfPTable table = frame(new float[] {100});
        table.addCell(band);
        return table;
    }

    /*
     * Three rows rather than one phrase of three lines. A 32pt figure and a 10pt
     * status share a line box when they are one paragraph, and the status ends
     * up printed through the descenders of the amount.
     */
    private PdfPTable figure(Receipt receipt) {
        PdfPTable table = frame(new float[] {100});

        table.addCell(figureRow(paragraph(
            (receipt.settled ? "RECEIVED WITH THANKS" : "DECLARED, AWAITING CONFIRMATION"),
            sansBold, 8, INK_MUTED), 22, 4));
        table.addCell(figureRow(
            paragraph(Format.paymentAmount(receipt.amount), serif, 32, INK), 0, 6));

        if (receipt.statusLabel != null) {
            String status = receipt.statusLabel
                + (receipt.statusDetail == null || receipt.statusDetail.isBlank()
                    ? "" : "   " + receipt.statusDetail);
            PdfPCell last = figureRow(paragraph(status, sans, 10, INK_MUTED), 0, 18);
            last.setBorderColorBottom(RULE);
            last.setBorderWidthBottom(0.7f);
            table.addCell(last);
        }
        return table;
    }

    private PdfPCell figureRow(Phrase content, float top, float bottom) {
        PdfPCell cell = cell(content);
        cell.setPaddingTop(top);
        cell.setPaddingBottom(bottom);
        cell.setPaddingLeft(20);
        cell.setPaddingRight(20);
        return cell;
    }

    private PdfPTable detail(Receipt receipt) {
        PdfPTable table = frame(new float[] {38, 62});
        for (int i = 0; i < receipt.lines.size(); i++) {
            Receipt.Line line = receipt.lines.get(i);
            boolean last = i == receipt.lines.size() - 1;

            PdfPCell label = ruled(cell(paragraph(line.label(), sans, 9.5f, INK_MUTED)), last);
            PdfPCell value = ruled(cell(paragraph(line.value(), sansBold, 11, INK)), last);
            value.setHorizontalAlignment(Element.ALIGN_RIGHT);

            table.addCell(label);
            table.addCell(value);
        }
        return table;
    }

    /* One cell again, for the same reason the masthead is one. */
    private PdfPTable balance(Receipt receipt) {
        PdfPTable inner = frame(new float[] {55, 45});
        inner.setTotalWidth(WIDTH - 40);
        inner.addCell(cell(paragraph(receipt.remainingLabel, sans, 10, INK_SOFT)));
        PdfPCell value = cell(paragraph(Format.paymentAmount(receipt.remaining), serif, 18, INK));
        value.setHorizontalAlignment(Element.ALIGN_RIGHT);
        inner.addCell(value);

        PdfPCell band = new PdfPCell(inner);
        band.setBorder(Rectangle.NO_BORDER);
        band.setBackgroundColor(SUBTLE);
        band.setPadding(15);
        band.setPaddingLeft(20);
        band.setPaddingRight(20);
        band.setBorderColorTop(RULE);
        band.setBorderWidthTop(0.7f);

        PdfPTable table = frame(new float[] {100});
        table.addCell(band);
        return table;
    }

    private PdfPTable footNote(Receipt receipt) {
        PdfPTable table = frame(new float[] {100});
        PdfPCell cell = cell(paragraph(receipt.footNote, sans, 8.5f, INK_MUTED));
        cell.setPaddingTop(14);
        cell.setPaddingLeft(20);
        table.addCell(cell);
        return table;
    }

    /* ---------------------------------------------------------------- */

    private static PdfPTable frame(float[] widths) {
        PdfPTable table = new PdfPTable(widths);
        table.setTotalWidth(WIDTH);
        table.setLockedWidth(true);
        table.setHorizontalAlignment(Element.ALIGN_LEFT);
        return table;
    }

    private static PdfPCell cell(Phrase content) {
        PdfPCell cell = new PdfPCell(content);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(0);
        return cell;
    }

    /* Every detail row carries the hairline under it that the screen draws,
       except the last, which would otherwise close the block with a line the
       balance band immediately repeats. */
    private static PdfPCell ruled(PdfPCell cell, boolean last) {
        cell.setPaddingTop(9);
        cell.setPaddingBottom(9);
        cell.setPaddingLeft(20);
        cell.setPaddingRight(20);
        if (!last) {
            cell.setBorderColorBottom(RULE);
            cell.setBorderWidthBottom(0.6f);
        }
        return cell;
    }

    private static Phrase paragraph(String value, BaseFont face, float size, Color colour) {
        return new Phrase(value, new Font(face, size, Font.NORMAL, colour));
    }

    private static Phrase text(String value, BaseFont face, float size, Color colour) {
        return paragraph(value, face, size, colour);
    }

    /*
     * The bundled face, embedded so the file looks the same on a machine that has
     * never installed it. A missing or unreadable font costs the receipt its
     * typography and nothing else, so it falls back to a base-14 face rather than
     * failing the export.
     */
    private static BaseFont load(String resource, String fallback) {
        try (InputStream in = ReceiptPdf.class.getResourceAsStream(resource)) {
            if (in != null) {
                return BaseFont.createFont(resource, BaseFont.IDENTITY_H, BaseFont.EMBEDDED,
                    BaseFont.CACHED, in.readAllBytes(), null);
            }
        } catch (IOException | DocumentException e) {
            System.err.println("Aqarat: could not embed " + resource + " in the receipt PDF — "
                + e.getMessage());
        }
        try {
            return BaseFont.createFont(fallback, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
        } catch (IOException | DocumentException e) {
            throw new IllegalStateException("No usable font for the receipt PDF.", e);
        }
    }
}
