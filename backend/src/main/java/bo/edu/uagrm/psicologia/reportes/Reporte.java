package bo.edu.uagrm.psicologia.reportes;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Reporte tabular independiente del formato (CU-27). Se exporta a PDF (OpenPDF) o XLSX (Apache POI)
 * con el encabezado institucional, filtros aplicados, totales y fecha de generación.
 */
public record Reporte(String titulo, String subtitulo, List<String> columnas, List<List<Object>> filas, List<String> resumen,
                      String generadoPor) {

    private static final String INSTITUCION = "Universidad Autónoma Gabriel René Moreno";
    private static final String UNIDAD = "Facultad de Humanidades · Dirección de Carrera de Psicología";
    private static final Color AZUL = new Color(31, 58, 104);

    private String fechaGeneracion() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }

    private static String texto(Object v) {
        return v == null ? "" : v instanceof Double d ? String.format("%.1f", d) : v.toString();
    }

    public byte[] pdf() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(columnas.size() > 5 ? PageSize.LETTER.rotate() : PageSize.LETTER, 36, 36, 40, 40);
        PdfWriter w = PdfWriter.getInstance(doc, out);
        w.setPageEvent(new PdfPageEventHelper() {
            @Override
            public void onEndPage(PdfWriter writer, Document d) {
                var cb = writer.getDirectContent();
                com.lowagie.text.pdf.ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT,
                        new Phrase("Página " + writer.getPageNumber(), FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY)),
                        d.right(), d.bottom() - 20, 0);
            }
        });
        doc.open();
        doc.add(new Paragraph(INSTITUCION, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, AZUL)));
        doc.add(new Paragraph(UNIDAD, FontFactory.getFont(FontFactory.HELVETICA, 9)));
        Paragraph t = new Paragraph(titulo, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12));
        t.setSpacingBefore(12);
        doc.add(t);
        if (subtitulo != null) doc.add(new Paragraph(subtitulo, FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY)));
        for (String r : resumen) doc.add(new Paragraph(r, FontFactory.getFont(FontFactory.HELVETICA, 9)));

        PdfPTable tabla = new PdfPTable(columnas.size());
        tabla.setWidthPercentage(100);
        tabla.setSpacingBefore(10);
        tabla.setHeaderRows(1);
        Font cabecera = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
        Font celda = FontFactory.getFont(FontFactory.HELVETICA, 8);
        for (String c : columnas) {
            PdfPCell cell = new PdfPCell(new Phrase(c, cabecera));
            cell.setBackgroundColor(AZUL);
            cell.setPadding(4);
            tabla.addCell(cell);
        }
        boolean alterna = false;
        for (List<Object> fila : filas) {
            for (Object v : fila) {
                PdfPCell cell = new PdfPCell(new Phrase(texto(v), celda));
                cell.setPadding(3);
                if (v instanceof Number) cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                if (alterna) cell.setBackgroundColor(new Color(238, 243, 251));
                tabla.addCell(cell);
            }
            alterna = !alterna;
        }
        if (filas.isEmpty()) {
            PdfPCell vacia = new PdfPCell(new Phrase("Sin registros para los filtros indicados", celda));
            vacia.setColspan(columnas.size());
            tabla.addCell(vacia);
        }
        doc.add(tabla);
        Paragraph pie = new Paragraph("Generado el " + fechaGeneracion() + " por " + generadoPor + " · " + filas.size() + " registro(s)",
                FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, Color.GRAY));
        pie.setSpacingBefore(8);
        doc.add(pie);
        doc.close();
        return out.toByteArray();
    }

    public byte[] xlsx() {
        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet hoja = libro.createSheet(titulo.length() > 31 ? titulo.substring(0, 31) : titulo);
            var negrita = libro.createFont();
            negrita.setBold(true);
            CellStyle tituloEstilo = libro.createCellStyle();
            tituloEstilo.setFont(negrita);
            var blanca = libro.createFont();
            blanca.setBold(true);
            blanca.setColor(IndexedColors.WHITE.getIndex());
            CellStyle cab = libro.createCellStyle();
            cab.setFont(blanca);
            cab.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            cab.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            cab.setBorderBottom(BorderStyle.THIN);

            int r = 0;
            hoja.createRow(r++).createCell(0).setCellValue(INSTITUCION);
            hoja.getRow(0).getCell(0).setCellStyle(tituloEstilo);
            hoja.createRow(r++).createCell(0).setCellValue(UNIDAD);
            Row filaTitulo = hoja.createRow(r++);
            filaTitulo.createCell(0).setCellValue(titulo);
            filaTitulo.getCell(0).setCellStyle(tituloEstilo);
            if (subtitulo != null) hoja.createRow(r++).createCell(0).setCellValue(subtitulo);
            for (String s : resumen) hoja.createRow(r++).createCell(0).setCellValue(s);
            r++;
            int filaCabecera = r;
            Row h = hoja.createRow(r++);
            for (int i = 0; i < columnas.size(); i++) {
                h.createCell(i).setCellValue(columnas.get(i));
                h.getCell(i).setCellStyle(cab);
            }
            for (List<Object> fila : filas) {
                Row x = hoja.createRow(r++);
                for (int i = 0; i < fila.size(); i++) {
                    Object v = fila.get(i);
                    if (v instanceof Number n) x.createCell(i).setCellValue(n.doubleValue());
                    else x.createCell(i).setCellValue(texto(v));
                }
            }
            hoja.createRow(++r).createCell(0).setCellValue("Generado el " + fechaGeneracion() + " por " + generadoPor);
            hoja.createFreezePane(0, filaCabecera + 1);
            hoja.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(filaCabecera, Math.max(filaCabecera, filaCabecera + filas.size()), 0, columnas.size() - 1));
            for (int i = 0; i < columnas.size(); i++) {
                hoja.autoSizeColumn(i);
                hoja.setColumnWidth(i, Math.min(hoja.getColumnWidth(i) + 512, 60 * 256));
            }
            libro.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
