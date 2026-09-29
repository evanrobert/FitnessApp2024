package Evan.Application.Fitness.Service;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

/**
 * RFC 4180 CSV with spreadsheet formula-injection protection: text cells that
 * start with = + - @ (or tab/CR) are prefixed with a quote so Excel treats them as text.
 */
public final class CsvExporter {
    private CsvExporter() {
    }

    public static void write(Writer out, List<String> header, List<List<Object>> rows) throws IOException {
        try (CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT.builder()
                .setHeader(header.toArray(String[]::new)).get())) {
            for (List<Object> row : rows) {
                printer.printRecord(row.stream().map(CsvExporter::safe).toList());
            }
        }
    }

    static Object safe(Object value) {
        if (value instanceof String text && !text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            return "'" + text;
        }
        return value == null ? "" : value;
    }
}
