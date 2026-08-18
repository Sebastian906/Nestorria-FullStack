package com.nestorria.server.modules.report;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.nestorria.server.modules.booking.Booking;
import com.nestorria.server.modules.booking.BookingRepository;
import com.nestorria.server.modules.contract.Contract;
import com.nestorria.server.modules.contract.ContractRepository;
import com.nestorria.server.modules.properties.Property;
import com.nestorria.server.modules.properties.PropertyRepository;
import com.nestorria.server.modules.report.dto.BookingsReportData;
import com.nestorria.server.modules.report.dto.PropertiesReportData;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final BookingRepository bookingRepository;
    private final PropertyRepository propertyRepository;
    private final ContractRepository contractRepository;

    private static final DateTimeFormatter DATE_FORMAT = 
        DateTimeFormatter.ofPattern("dd/MM/yyyy");
    
    // Zona horaria del servidor
    private static final ZoneId ZONE = ZoneId.systemDefault();

    @Transactional(readOnly = true)
    public byte[] generateBookingsReport(String agencyId, LocalDate startDate, 
                                          LocalDate endDate, String format) {
        
        Instant startInstant = startDate.atStartOfDay(ZONE).toInstant();
        Instant endInstant = endDate.plusDays(1).atStartOfDay(ZONE).toInstant();
        
        List<Booking> bookings = bookingRepository.findByAgencyIdAndDateRange(
            agencyId, startInstant, endInstant);
        
        BookingsReportData reportData = accumulateBookingsData(bookings);
        
        if ("xlsx".equals(format)) {
            return generateBookingsExcel(reportData);
        } else {
            return generateBookingsPdf(reportData);
        }
    }

    @Transactional(readOnly = true)
    public byte[] generatePropertiesReport(String agencyId, String format) {
        
        List<Property> properties = propertyRepository.findByAgencyId(agencyId);
        
        PropertiesReportData reportData = accumulatePropertiesData(properties);
        
        if ("xlsx".equals(format)) {
            return generatePropertiesExcel(reportData);
        } else {
            return generatePropertiesPdf(reportData);
        }
    }

    private BookingsReportData accumulateBookingsData(List<Booking> bookings) {
        List<BookingsReportData.BookingRow> rows = new java.util.ArrayList<>();
        long totalRevenue = 0;
        int totalNights = 0;

        // 1 query batch + Map<bookingId, Contract> → lookups O(1) (HashMap)
        java.util.Map<String, Contract> contractsByBookingId = bookings.isEmpty()
            ? java.util.Map.of()
            : contractRepository
                .findByBookingIdIn(bookings.stream().map(Booking::getId).toList())
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                    c -> c.getBooking().getId(), c -> c, (a, b) -> a));

        for (Booking booking : bookings) {
            int nights = (int) java.time.temporal.ChronoUnit.DAYS.between(
                booking.getCheckInDate(), booking.getCheckOutDate());

            totalRevenue += booking.getTotalPrice();
            totalNights += nights;

            Contract contract = contractsByBookingId.get(booking.getId());  // O(1), null si no hay

            String createdAtStr = formatInstant(booking.getCreatedAt());

            rows.add(new BookingsReportData.BookingRow(
                booking.getId(),
                createdAtStr,
                booking.getUser().getEmail(),
                booking.getProperty().getTitle(),
                contract != null ? contract.getId() : "N/A",
                booking.getCheckInDate().format(DATE_FORMAT),
                booking.getCheckOutDate().format(DATE_FORMAT),
                nights,
                booking.getTotalPrice(),
                booking.getStatus().name(),
                booking.isPaid()
            ));
        }

        return new BookingsReportData(
            rows,
            bookings.size(),
            totalRevenue,
            totalNights,
            bookings.size() > 0 ? (double) totalRevenue / bookings.size() : 0
        );
    }

    // Convierte Instant a String de forma segura.
    private String formatInstant(Instant instant) {
        if (instant == null) {
            return "N/A";
        }
        return instant.atZone(ZONE).format(DATE_FORMAT);
    }

    private PropertiesReportData accumulatePropertiesData(List<Property> properties) {
        List<PropertiesReportData.PropertyRow> rows = new java.util.ArrayList<>();
        
        java.util.Map<String, List<Contract>> contractsCache = new java.util.HashMap<>();
        
        for (Property property : properties) {
            List<Contract> contracts = contractsCache.computeIfAbsent(
                property.getId(),
                id -> contractRepository.findByPropertyId(id)
            );
            
            // Calcular revenue total de bookings asociados a contratos
            long totalRevenue = calculateRevenueFromContracts(contracts);
            
            rows.add(new PropertiesReportData.PropertyRow(
                property.getId(),
                property.getTitle(),
                property.getCity(),
                property.getCountry(),
                property.getPropertyType().name(),
                property.getPrice().getRent() != null ? property.getPrice().getRent() : 0,
                property.getPrice().getSale() != null ? property.getPrice().getSale() : 0,
                contracts.size(),
                totalRevenue,
                property.isAvailable()
            ));
        }
        
        return new PropertiesReportData(rows);
    }

    /**
     * Calcula revenue total desde los bookings de los contratos.
     * Contract no tiene getMonthlyRent(), usamos booking.getTotalPrice().
     */
    private long calculateRevenueFromContracts(List<Contract> contracts) {
        return contracts.stream()
            .map(Contract::getBooking)
            .filter(booking -> booking != null && booking.isPaid())
            .mapToLong(Booking::getTotalPrice)
            .sum();
    }

    // GENERACIÓN EXCEL
    private byte[] generateBookingsExcel(BookingsReportData data) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            
            Sheet sheet = workbook.createSheet("Bookings Report");
            
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle currencyStyle = createCurrencyStyle(workbook);
            
            // Título
            Row titleRow = sheet.createRow(0);
            org.apache.poi.ss.usermodel.Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("Bookings Report");
            titleCell.setCellStyle(headerStyle);
            
            // Resumen
            Row summaryRow = sheet.createRow(1);
            summaryRow.createCell(0).setCellValue("Total Bookings:");
            summaryRow.createCell(1).setCellValue(data.totalBookings());
            summaryRow.createCell(3).setCellValue("Total Revenue:");
            org.apache.poi.ss.usermodel.Cell revenueCell = summaryRow.createCell(4);
            revenueCell.setCellValue(data.totalRevenue());
            revenueCell.setCellStyle(currencyStyle);
            
            // Headers
            String[] headers = {"ID", "Date", "Client", "Property", "Contract", 
                              "Check-in", "Check-out", "Nights", "Amount", "Status"};
            Row headerRow = sheet.createRow(3);
            for (int i = 0; i < headers.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            // Datos
            int rowNum = 4;
            for (BookingsReportData.BookingRow row : data.rows()) {
                Row dataRow = sheet.createRow(rowNum++);
                
                dataRow.createCell(0).setCellValue(row.bookingId());
                dataRow.createCell(1).setCellValue(row.createdAt());
                dataRow.createCell(2).setCellValue(row.clientEmail());
                dataRow.createCell(3).setCellValue(row.propertyTitle());
                dataRow.createCell(4).setCellValue(row.contractId());
                dataRow.createCell(5).setCellValue(row.checkInDate());
                dataRow.createCell(6).setCellValue(row.checkOutDate());
                dataRow.createCell(7).setCellValue(row.nights());
                
                org.apache.poi.ss.usermodel.Cell amountCell = dataRow.createCell(8);
                amountCell.setCellValue(row.totalPrice());
                amountCell.setCellStyle(currencyStyle);
                
                dataRow.createCell(9).setCellValue(row.status());
            }
            
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            
            workbook.write(out);
            return out.toByteArray();
            
        } catch (IOException e) {
            log.error("Error generating Excel report", e);
            throw new RuntimeException("Error generating Excel report", e);
        }
    }

    private byte[] generateBookingsPdf(BookingsReportData data) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            
            PdfWriter writer = new PdfWriter(out);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document document = new Document(pdfDoc, PageSize.A4);
            try {
                document.add(new Paragraph("Bookings Report")
                    .setFontSize(20)
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER));
                
                document.add(new Paragraph(
                    String.format("Total Bookings: %d | Total Revenue: $%,d | Average: $%,.2f",
                        data.totalBookings(), data.totalRevenue(), data.averageBookingValue()))
                    .setFontSize(12));
                
                Table table = new Table(UnitValue.createPercentArray(10))
                    .useAllAvailableWidth();
                
                String[] headers = {"ID", "Date", "Client", "Property", "Contract", 
                                  "Check-in", "Check-out", "Nights", "Amount", "Status"};
                
                DeviceRgb headerColor = new DeviceRgb(52, 152, 219);
                
                for (String header : headers) {
                    table.addHeaderCell(new com.itextpdf.layout.element.Cell()
                        .add(new Paragraph(header).setBold().setFontSize(8))
                        .setBackgroundColor(headerColor)
                        .setFontColor(ColorConstants.WHITE)
                        .setTextAlignment(TextAlignment.CENTER));
                }
                
                for (BookingsReportData.BookingRow row : data.rows()) {
                    table.addCell(createPdfCell(row.bookingId()));
                    table.addCell(createPdfCell(row.createdAt()));
                    table.addCell(createPdfCell(row.clientEmail()));
                    table.addCell(createPdfCell(row.propertyTitle()));
                    table.addCell(createPdfCell(row.contractId()));
                    table.addCell(createPdfCell(row.checkInDate()));
                    table.addCell(createPdfCell(row.checkOutDate()));
                    table.addCell(createPdfCell(String.valueOf(row.nights())));
                    table.addCell(createPdfCell(String.format("$%,d", row.totalPrice())));
                    table.addCell(createPdfCell(row.status()));
                }
                
                document.add(table);
            } finally {
                document.close();
            }
            
            return out.toByteArray();
            
        } catch (Exception e) {
            log.error("Error generating PDF report", e);
            throw new RuntimeException("Error generating PDF report", e);
        }
    }

    // ESTILOS
    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle createCurrencyStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setDataFormat(workbook.createDataFormat().getFormat("$#,##0"));
        return style;
    }

    private com.itextpdf.layout.element.Cell createPdfCell(String content) {
        return new com.itextpdf.layout.element.Cell()
            .add(new Paragraph(content != null ? content : "").setFontSize(8))
            .setTextAlignment(TextAlignment.LEFT);
    }

    // PROPIEDADES
    private byte[] generatePropertiesExcel(PropertiesReportData data) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            
            Sheet sheet = workbook.createSheet("Properties Report");
            
            String[] headers = {"ID", "Title", "City", "Country", "Type", 
                              "Rent Price", "Sale Price", "Contracts", "Revenue", "Available"};
            
            Row headerRow = sheet.createRow(0);
            CellStyle headerStyle = createHeaderStyle(workbook);
            
            for (int i = 0; i < headers.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            int rowNum = 1;
            for (PropertiesReportData.PropertyRow row : data.rows()) {
                Row dataRow = sheet.createRow(rowNum++);
                dataRow.createCell(0).setCellValue(row.propertyId());
                dataRow.createCell(1).setCellValue(row.title());
                dataRow.createCell(2).setCellValue(row.city());
                dataRow.createCell(3).setCellValue(row.country());
                dataRow.createCell(4).setCellValue(row.type());
                dataRow.createCell(5).setCellValue(row.rentPrice());
                dataRow.createCell(6).setCellValue(row.salePrice());
                dataRow.createCell(7).setCellValue(row.totalContracts());
                dataRow.createCell(8).setCellValue(row.totalRevenue());
                dataRow.createCell(9).setCellValue(row.isAvailable() ? "Yes" : "No");
            }
            
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            
            workbook.write(out);
            return out.toByteArray();
            
        } catch (IOException e) {
            log.error("Error generating properties Excel", e);
            throw new RuntimeException("Error generating properties Excel", e);
        }
    }

    private byte[] generatePropertiesPdf(PropertiesReportData data) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            
            PdfWriter writer = new PdfWriter(out);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document document = new Document(pdfDoc, PageSize.A4);
            try {
                document.add(new Paragraph("Properties Report")
                    .setFontSize(20)
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER));
                
                Table table = new Table(UnitValue.createPercentArray(10))
                    .useAllAvailableWidth();
                
                String[] headers = {"ID", "Title", "City", "Country", "Type", 
                                  "Rent", "Sale", "Contracts", "Revenue", "Available"};
                
                DeviceRgb headerColor = new DeviceRgb(46, 204, 113);
                
                for (String header : headers) {
                    table.addHeaderCell(new com.itextpdf.layout.element.Cell()
                        .add(new Paragraph(header).setBold().setFontSize(7))
                        .setBackgroundColor(headerColor)
                        .setFontColor(ColorConstants.WHITE));
                }
                
                for (PropertiesReportData.PropertyRow row : data.rows()) {
                    table.addCell(createPdfCell(row.propertyId()));
                    table.addCell(createPdfCell(row.title()));
                    table.addCell(createPdfCell(row.city()));
                    table.addCell(createPdfCell(row.country()));
                    table.addCell(createPdfCell(row.type()));
                    table.addCell(createPdfCell(String.valueOf(row.rentPrice())));
                    table.addCell(createPdfCell(String.valueOf(row.salePrice())));
                    table.addCell(createPdfCell(String.valueOf(row.totalContracts())));
                    table.addCell(createPdfCell(String.format("$%,d", row.totalRevenue())));
                    table.addCell(createPdfCell(row.isAvailable() ? "Yes" : "No"));
                }
                
                document.add(table);
            } finally {
                document.close();
            }
            
            return out.toByteArray();
            
        } catch (Exception e) {
            log.error("Error generating properties PDF", e);
            throw new RuntimeException("Error generating properties PDF", e);
        }
    }
}