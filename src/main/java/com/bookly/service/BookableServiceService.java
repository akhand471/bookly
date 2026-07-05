package com.bookly.service;

import com.bookly.dto.PageResponse;
import com.bookly.dto.ServiceRequest;
import com.bookly.dto.ServiceResponse;
import com.bookly.entity.BookableService;
import com.bookly.entity.Business;
import com.bookly.exception.ConflictException;
import com.bookly.exception.ResourceNotFoundException;
import com.bookly.mapper.ServiceMapper;
import com.bookly.repository.BookableServiceRepository;
import com.bookly.repository.BusinessRepository;
import com.bookly.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Business logic for bookable service management.
 * <p>
 * All operations are tenant-scoped: the {@code businessId} is always read from
 * {@link TenantContext} (populated by {@link com.bookly.config.TenantInterceptor}
 * from the authenticated JWT). This means a BUSINESS_OWNER from Tenant A can never
 * create, read, or mutate services belonging to Tenant B, even if they guess the UUID.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookableServiceService {

    private final BookableServiceRepository serviceRepository;
    private final BusinessRepository businessRepository;
    private final ServiceMapper serviceMapper;

    // ─── Create ────────────────────────────────────────────────────────────

    /**
     * Creates a new service for the current tenant.
     *
     * @throws ConflictException if a service with the same name (case-insensitive)
     *                           already exists and is active in this business.
     */
    @Transactional
    public ServiceResponse create(ServiceRequest request) {
        UUID businessId = requireTenantId();

        checkDuplicateName(businessId, request.getName(), null);

        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));

        BookableService entity = serviceMapper.toEntity(request);
        entity.setBusiness(business);

        BookableService saved = serviceRepository.save(entity);
        log.info("Service created: id={}, business={}, name={}", saved.getId(), businessId, saved.getName());
        return serviceMapper.toResponse(saved);
    }

    // ─── Read ──────────────────────────────────────────────────────────────

    /**
     * Returns a paginated list of active services for the current tenant.
     */
    @Transactional(readOnly = true)
    public PageResponse<ServiceResponse> listActive(Pageable pageable) {
        UUID businessId = requireTenantId();
        Page<ServiceResponse> page = serviceRepository
                .findAllByBusiness_IdAndIsActive(businessId, true, pageable)
                .map(serviceMapper::toResponse);
        return PageResponse.from(page);
    }

    /**
     * Returns a single service by ID, scoped to the current tenant.
     *
     * @throws ResourceNotFoundException if no matching service exists in this tenant.
     */
    @Transactional(readOnly = true)
    public ServiceResponse getById(UUID serviceId) {
        UUID businessId = requireTenantId();
        BookableService entity = findOwnedService(serviceId, businessId);
        return serviceMapper.toResponse(entity);
    }

    // ─── Update ────────────────────────────────────────────────────────────

    /**
     * Fully replaces a service's fields.
     * Ownership is verified: the service must belong to the caller's tenant.
     *
     * @throws ResourceNotFoundException if the service does not exist in this tenant.
     * @throws ConflictException         if the new name clashes with another active service.
     */
    @Transactional
    public ServiceResponse update(UUID serviceId, ServiceRequest request) {
        UUID businessId = requireTenantId();
        BookableService entity = findOwnedService(serviceId, businessId);

        checkDuplicateName(businessId, request.getName(), serviceId);

        serviceMapper.updateEntity(request, entity);
        BookableService saved = serviceRepository.save(entity);
        log.info("Service updated: id={}, business={}", serviceId, businessId);
        return serviceMapper.toResponse(saved);
    }

    // ─── Soft Delete ───────────────────────────────────────────────────────

    /**
     * Soft-deletes a service by setting {@code isActive = false}.
     * The record is retained for historical referencing in existing appointments.
     *
     * @throws ResourceNotFoundException if the service does not exist in this tenant.
     */
    @Transactional
    public void softDelete(UUID serviceId) {
        UUID businessId = requireTenantId();
        BookableService entity = findOwnedService(serviceId, businessId);
        entity.setActive(false);
        serviceRepository.save(entity);
        log.info("Service soft-deleted: id={}, business={}", serviceId, businessId);
    }

    // ─── Internal helpers ──────────────────────────────────────────────────

    private UUID requireTenantId() {
        UUID tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new ResourceNotFoundException("Tenant context not set — unauthenticated request");
        }
        return tenantId;
    }

    private BookableService findOwnedService(UUID serviceId, UUID businessId) {
        return serviceRepository.findByIdAndBusiness_Id(serviceId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Service not found: " + serviceId));
    }

    private void checkDuplicateName(UUID businessId, String name, UUID excludeId) {
        if (serviceRepository.existsActiveByNameAndBusiness(businessId, name, excludeId)) {
            throw new ConflictException(
                    "A service named '" + name + "' already exists in this business");
        }
    }
}
