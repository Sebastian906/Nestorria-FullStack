package com.nestorria.server.modules.properties;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.nestorria.server.common.exception.ConflictException;
import com.nestorria.server.modules.agency.Agency;
import com.nestorria.server.modules.agency.AgencyRepository;
import com.nestorria.server.modules.properties.dto.CreatePropertyRequest;
import com.nestorria.server.modules.review.ReviewService;

@ExtendWith(MockitoExtension.class)
class PropertyServiceUndoTest {

    @Mock private PropertyRepository propertyRepository;
    @Mock private AgencyRepository agencyRepository;
    @Mock private Cloudinary cloudinary;
    @Mock private PropertyPersistenceService persistenceService;
    @Mock private PropertyListingService listingService;
    @Mock private ReviewService reviewService;
    @Mock private Uploader uploader;

    private PropertyService propertyService;

    @BeforeEach
    void setUp() {
        // Runnable::run = ejecución síncrona inline para pruebas
        propertyService = new PropertyService(propertyRepository, agencyRepository, cloudinary,
            persistenceService, listingService, reviewService, Runnable::run);
        when(cloudinary.uploader()).thenReturn(uploader);
    }

    private MultipartFile image(String publicId, String url) throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn("image/jpeg");
        when(file.getBytes()).thenReturn(publicId.getBytes());
        when(uploader.upload(any(byte[].class), any(Map.class)))
            .thenReturn(Map.of("secure_url", url, "public_id", publicId));
        return file;
    }

    private CreatePropertyRequest request() {
        return new CreatePropertyRequest(
            "Casa", "Desc", "Ciudad", "País", "Dirección", 100,
            PropertyType.HOUSE, null, 1000, null, 2, 1, 1,
            List.of("wifi"), null, null, null, null);
    }

    @Test
    void persistFailure_deletesAllUploadedImagesInLifoOrder() throws Exception {
        when(agencyRepository.findByOwnerId("owner-1")).thenReturn(Optional.of(mock(Agency.class)));
        when(persistenceService.persistProperty(any(), any(), any()))
            .thenThrow(new ConflictException("DB down"));

        assertThrows(ConflictException.class, () ->
            propertyService.create("owner-1", request(), List.of(image("p1", "u1"), image("p2", "u2"))));

        verify(uploader).destroy("p2", Map.of("resource_type", "image"));
        verify(uploader).destroy("p1", Map.of("resource_type", "image"));
    }

    @Test
    void persistSuccess_keepsUploadedImages() throws Exception {
        when(agencyRepository.findByOwnerId("owner-1")).thenReturn(Optional.of(mock(Agency.class)));
        when(persistenceService.persistProperty(any(), any(), eq(List.of("u1"))))
            .thenReturn(mock(com.nestorria.server.modules.properties.dto.PropertyResponse.class));

        propertyService.create("owner-1", request(), List.of(image("p1", "u1")));

        verify(uploader, never()).destroy(any(), any());
    }
}
