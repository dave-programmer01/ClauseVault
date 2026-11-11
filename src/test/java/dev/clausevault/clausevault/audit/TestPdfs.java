package dev.clausevault.clausevault.audit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/** Builds simple text PDFs for tests (same layout as the samples/*.pdf generator). */
final class TestPdfs {

    private static final float FONT_SIZE = 10f;
    private static final float LEADING = 13f;
    private static final float MARGIN = 54f;

    private TestPdfs() {
    }

    static byte[] fromText(String text) throws IOException {
        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        float width = PDRectangle.LETTER.getWidth() - 2 * MARGIN;
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n")) {
            lines.addAll(wrap(paragraph, font, width));
        }
        int perPage = (int) ((PDRectangle.LETTER.getHeight() - 2 * MARGIN) / LEADING);
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < lines.size(); i += perPage) {
                PDPage page = new PDPage(PDRectangle.LETTER);
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(font, FONT_SIZE);
                    cs.setLeading(LEADING);
                    cs.newLineAtOffset(MARGIN, PDRectangle.LETTER.getHeight() - MARGIN);
                    for (String line : lines.subList(i, Math.min(lines.size(), i + perPage))) {
                        cs.showText(line);
                        cs.newLine();
                    }
                    cs.endText();
                }
            }
            if (lines.isEmpty()) {
                doc.addPage(new PDPage(PDRectangle.LETTER));
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static List<String> wrap(String paragraph, PDType1Font font, float width) throws IOException {
        List<String> lines = new ArrayList<>();
        if (paragraph.isBlank()) {
            lines.add("");
            return lines;
        }
        StringBuilder line = new StringBuilder();
        for (String word : paragraph.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (font.getStringWidth(candidate) / 1000 * FONT_SIZE > width && !line.isEmpty()) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        lines.add(line.toString());
        return lines;
    }
}
