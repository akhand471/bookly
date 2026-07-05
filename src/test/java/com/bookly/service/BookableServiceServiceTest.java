package com.bookly.service;

import com.bookly.dto.ServiceRequest;
import com.bookly.dto.ServiceResponse;
import com.bookly.dto.PageResponse;
import com.bookly.entity.BookableService;
import com.bookly.entity.Business;
import com.bookly.exception.ConflictException;
import com.bookly.exception.ResourceNotFoundException;
import com.bookly.mapper.ServiceMapper;
import com.bookly.repository.BookableServiceRepository;
import com.bookly.repository.BusinessRepository;
import com.bookly.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookableServiceServiceTest {

    @Mock
    private BookableServiceRepository serviceRepository;

    @Mock
    private BusinessRepository businessRepository;

    @Mock
    private ServiceMapper serviceMapper;

    @InjectMocks
    private BookableServiceService bookableServiceService;

    private UUID businessId;
    private UUID serviceId;
    private Business business;
    private ServiceRequest request;

    @BeforeEach
    void setUp() {
        businessId = UUID.randomUUID();
        serviceId = UUID.randomUUID();

        // Populate TenantContext as TenantInterceptor would at runtime
        TenantContext.setCurrentTenant(businessId);

        business = Business.builder()
                .id(businessId)
                .name("Test Salon")
                .subdomain("test-salon")
                .build();

        request = ServiceRequest.builder()
                .name("Haircut")
                .description("Classic cut")
                .durationMinutes(30)
                .price(new BigDecimal("25.00"))
                .isActive(true)
                .build();
    }

    @AfterEach
    void tearDown() {
        // Always clean TenantContext to avoid thread-local leakage between tests
        TenantContext.clear();
    }

    // ─── create ───────────────────────────────────────────────────────────

    @Test
    void create_ShouldPersistAndReturnResponse_WhenValid() {
        // Arrange
        BookableService entity = BookableService.builder()
                .id(serviceId)
                .business(business)
                .name("Haircut")
                .durationMinutes(30)
                .price(new BigDecimal("25.00"))
                .isActive(true)
                .build();

        ServiceResponse expectedResponse = ServiceResponse.builder()
                .id(serviceId)
                .businessId(businessId)
                .name("Haircut")
                .durationMinutes(30)
                .price(new BigDecimal("25.00"))
                .build();

        when(serviceRepository.existsActiveByNameAndBusiness(businessId, "Haircut", null)).thenReturn(false);
        when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));
        when(serviceMapper.toEntity(request)).thenReturn(entity);
        when(serviceRepository.save(entity)).thenReturn(entity);
        when(serviceMapper.toResponse(entity)).thenReturn(expectedResponse);

        // Act
        ServiceResponse result = bookableServiceService.create(request);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(serviceId);
        assertThat(result.getBusinessId()).isEqualTo(businessId);
        assertThat(result.getName()).isEqualTo("Haircut");
        verify(serviceRepository).save(entity);
    }

    @Test
    void create_ShouldThrowConflict_WhenDuplicateNameExists() {
        // Arrange
        when(serviceRepository.existsActiveByNameAndBusiness(businessId, "Haircut", null)).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> bookableServiceService.create(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Haircut");

        verify(serviceRepository, never()).save(any());
    }

    @Test
    void create_ShouldThrowResourceNotFound_WhenBusinessMissing() {
        // This can occur in edge cases where the JWT businessId is stale after deletion
        when(serviceRepository.existsActiveByNameAndBusiness(any(), any(), any())).thenReturn(false);
        when(businessRepository.findById(businessId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookableServiceService.create(request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(serviceRepository, never()).save(any());
    }

    // ─── listActive ───────────────────────────────────────────────────────

    @Test
    void listActive_ShouldReturnPagedServices() {
        // Arrange
        BookableService entity = BookableService.builder()
                .id(serviceId).business(business).name("Haircut").build();
        ServiceResponse response = ServiceResponse.builder().id(serviceId).name("Haircut").build();

        Page<BookableService> page = new PageImpl<>(List.of(entity));
        Pageable pageable = PageRequest.of(0, 20);

        when(serviceRepository.findAllByBusiness_IdAndIsActive(businessId, true, pageable))
                .thenReturn(page);
        when(serviceMapper.toResponse(entity)).thenReturn(response);

        // Act
        PageResponse<ServiceResponse> result = bookableServiceService.listActive(pageable);

        // Assert
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("Haircut");
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    // ─── getById ──────────────────────────────────────────────────────────

    @Test
    void getById_ShouldReturnService_WhenOwned() {
        // Arrange
        BookableService entity = BookableService.builder()
                .id(serviceId).business(business).name("Haircut").build();
        ServiceResponse response = ServiceResponse.builder().id(serviceId).name("Haircut").build();

        when(serviceRepository.findByIdAndBusiness_Id(serviceId, businessId))
                .thenReturn(Optional.of(entity));
        when(serviceMapper.toResponse(entity)).thenReturn(response);

        // Act
        ServiceResponse result = bookableServiceService.getById(serviceId);

        // Assert
        assertThat(result.getId()).isEqualTo(serviceId);
    }

    @Test
    void getById_ShouldThrowResourceNotFound_WhenServiceNotInTenant() {
        // Simulates cross-tenant access attempt (UUID belongs to a different business)
        when(serviceRepository.findByIdAndBusiness_Id(serviceId, businessId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookableServiceService.getById(serviceId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── update ───────────────────────────────────────────────────────────

    @Test
    void update_ShouldModifyAndReturnUpdatedResponse_WhenValid() {
        // Arrange
        BookableService entity = BookableService.builder()
                .id(serviceId).business(business).name("Haircut").isActive(true).build();
        ServiceRequest updateReq = ServiceRequest.builder()
                .name("Haircut Deluxe").durationMinutes(45).price(new BigDecimal("35.00")).build();
        ServiceResponse updatedResponse = ServiceResponse.builder()
                .id(serviceId).name("Haircut Deluxe").build();

        when(serviceRepository.findByIdAndBusiness_Id(serviceId, businessId))
                .thenReturn(Optional.of(entity));
        when(serviceRepository.existsActiveByNameAndBusiness(businessId, "Haircut Deluxe", serviceId))
                .thenReturn(false);
        when(serviceRepository.save(entity)).thenReturn(entity);
        when(serviceMapper.toResponse(entity)).thenReturn(updatedResponse);

        // Act
        ServiceResponse result = bookableServiceService.update(serviceId, updateReq);

        // Assert
        assertThat(result.getName()).isEqualTo("Haircut Deluxe");
        verify(serviceMapper).updateEntity(eq(updateReq), eq(entity));
        verify(serviceRepository).save(entity);
    }

    @Test
    void update_ShouldThrowConflict_WhenNewNameClashesWithAnotherService() {
        // Arrange
        BookableService entity = BookableService.builder()
                .id(serviceId).business(business).name("Haircut").build();
        ServiceRequest updateReq = ServiceRequest.builder()
                .name("Beard Trim").durationMinutes(15).price(BigDecimal.TEN).build();

        when(serviceRepository.findByIdAndBusiness_Id(serviceId, businessId))
                .thenReturn(Optional.of(entity));
        when(serviceRepository.existsActiveByNameAndBusiness(businessId, "Beard Trim", serviceId))
                .thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> bookableServiceService.update(serviceId, updateReq))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Beard Trim");

        verify(serviceRepository, never()).save(any());
    }

    // ─── softDelete ───────────────────────────────────────────────────────

    @Test
    void softDelete_ShouldSetIsActiveToFalse() {
        // Arrange
        BookableService entity = BookableService.builder()
                .id(serviceId).business(business).name("Haircut").isActive(true).build();

        when(serviceRepository.findByIdAndBusiness_Id(serviceId, businessId))
                .thenReturn(Optional.of(entity));
        when(serviceRepository.save(entity)).thenReturn(entity);

        // Act
        bookableServiceService.softDelete(serviceId);

        // Assert
        assertThat(entity.isActive()).isFalse();
        verify(serviceRepository).save(entity);
    }

    @Test
    void softDelete_ShouldThrowResourceNotFound_WhenServiceNotInTenant() {
        when(serviceRepository.findByIdAndBusiness_Id(serviceId, businessId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookableServiceService.softDelete(serviceId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(serviceRepository, never()).save(any());
    }
}
