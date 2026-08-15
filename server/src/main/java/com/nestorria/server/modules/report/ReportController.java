package com.nestorria.server.modules.report;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nestorria.server.common.exception.ResourceNotFoundException;
import com.nestorria.server.modules.agency.Agency;
import com.nestorria.server.modules.agency.AgencyRepository;

import lombok.RequiredArgsConstructor;

/**
 * Controlador para generación de reportes.
 * Endpoints:
 * - GET /api/reports/bookings/{format} - Reporte de bookings
 * - GET /api/reports/properties/{format} - Reporte de propiedades
 * Formatos soportados: xlsx, pdf
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final AgencyRepository agencyRepository;

    /**
     * Genera reporte de bookings en el formato especificado.
     * @param jwt - token de autenticación
     * @param format - "xlsx" o "pdf"
     * @param startDate - fecha de inicio (opcional, default: primer día del mes)
     * @param endDate - fecha de fin (opcional, default: último día del mes)
     * @return archivo descargable
     */
    @GetMapping("/bookings/{format}")
    public ResponseEntity<byte[]> generateBookingsReport(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String format,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) 
                LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) 
                LocalDate endDate) {
        
        // Validar formato
        if (!"xlsx".equals(format) && !"pdf".equals(format)) {
            throw new IllegalArgumentException("Formato no soportado. Use: xlsx o pdf");
        }
        
        // Obtener agencia del usuario
        String userId = jwt.getSubject();
        Agency agency = agencyRepository.findByOwnerId(userId)
            .orElseThrow(() -> new ResourceNotFoundException("No se encontró una agencia para este usuario"));
        
        // Fechas por defecto: mes actual
        LocalDate now = LocalDate.now();
        if (startDate == null) {
            startDate = now.withDayOfMonth(1);
        }
        if (endDate == null) {
            endDate = now.withDayOfMonth(now.lengthOfMonth());
        }
        
        // Generar reporte
        byte[] reportBytes = reportService.generateBookingsReport(
            agency.getId(), startDate, endDate, format);
        
        // Construir respuesta
        String contentType = "xlsx".equals(format) 
            ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            : "application/pdf";
        
        String filename = String.format("bookings-report_%s_%s.%s",
            startDate.format(DateTimeFormatter.BASIC_ISO_DATE),
            endDate.format(DateTimeFormatter.BASIC_ISO_DATE),
            format);
        
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, 
                "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.parseMediaType(contentType))
            .body(reportBytes);
    }

    // Genera reporte de propiedades en el formato especificado.
    @GetMapping("/properties/{format}")
    public ResponseEntity<byte[]> generatePropertiesReport(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String format) {
        
        // Validar formato
        if (!"xlsx".equals(format) && !"pdf".equals(format)) {
            throw new IllegalArgumentException("Formato no soportado. Use: xlsx o pdf");
        }
        
        // Obtener agencia del usuario
        String userId = jwt.getSubject();
        Agency agency = agencyRepository.findByOwnerId(userId)
            .orElseThrow(() -> new ResourceNotFoundException("No se encontró una agencia para este usuario"));
        
        // Generar reporte
        byte[] reportBytes = reportService.generatePropertiesReport(
            agency.getId(), format);
        
        // Construir respuesta
        String contentType = "xlsx".equals(format) 
            ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            : "application/pdf";
        
        String filename = String.format("properties-report_%s.%s",
            LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE),
            format);
        
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, 
                "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.parseMediaType(contentType))
            .body(reportBytes);
    }
}
