package com.bookly.service;

import com.bookly.dto.CustomerResponse;
import com.bookly.dto.CustomerUpdateRequest;
import com.bookly.dto.PageResponse;
import com.bookly.entity.Business;
import com.bookly.entity.Customer;
import com.bookly.exception.ResourceNotFoundException;
import com.bookly.mapper.CustomerMapper;
import com.bookly.repository.AppointmentRepository;
import com.bookly.repository.CustomerRepository;
import com.bookly.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Manages {@link Customer} records within a tenant.
 *
 * <p>Key design: {@link #findOrCreateGuest} is idempotent — calling it with the same
 * email + businessId always returns the same {@code Customer} row.  This means repeat
 * visitors accumulate a single booking history without needing an account.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final AppointmentRepository appointmentRepository;
    private final CustomerMapper customerMapper;

    // ─── Guest findOrCreate ────────────────────────────────────────────────

    /**
     * Finds an existing {@link Customer} by email within the given business, or creates one.
     * Thread-safe: relies on the unique constraint {@code (business_id, email)} in the DB.
     *
     * @param business     the owning business (tenant)
     * @param firstName    customer's first name
     * @param lastName     customer's last name
     * @param email        customer's email (case-sensitive lookup)
     * @param phone        optional phone number
     * @return existing or newly created Customer entity
     */
    @Transactional
    public Customer findOrCreateGuest(Business business, String firstName, String lastName,
                                      String email, String phone) {
        return customerRepository.findByEmailAndBusiness_Id(email, business.getId())
                .orElseGet(() -> {
                    Customer newCustomer = Customer.builder()
                            .business(business)
                            .firstName(firstName)
                            .lastName(lastName)
                            .email(email)
                            .phone(phone)
                            .build();
                    Customer saved = customerRepository.save(newCustomer);
                    log.info("New customer created: id={}, email={}, businessId={}",
                            saved.getId(), email, business.getId());
                    return saved;
                });
    }

    // ─── Read ──────────────────────────────────────────────────────────────

    /**
     * Returns a customer by ID, scoped to the current tenant.
     */
    @Transactional(readOnly = true)
    public CustomerResponse getCustomerById(UUID customerId) {
        UUID businessId = requireTenantId();
        Customer customer = customerRepository.findByIdAndBusiness_Id(customerId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + customerId));
        return customerMapper.toResponse(customer);
    }

    /**
     * Paginated list of all active customers for the current tenant.
     */
    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> listCustomers(Pageable pageable) {
        UUID businessId = requireTenantId();
        return PageResponse.from(
                customerRepository.findAllByBusiness_IdAndIsActiveTrue(businessId, pageable)
                        .map(customerMapper::toResponse)
        );
    }

    // ─── Update ────────────────────────────────────────────────────────────

    /**
     * Updates mutable fields of a customer.  Only non-null fields in the request are applied.
     */
    @Transactional
    public CustomerResponse updateCustomer(UUID customerId, CustomerUpdateRequest request) {
        UUID businessId = requireTenantId();
        Customer customer = customerRepository.findByIdAndBusiness_Id(customerId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + customerId));

        if (request.getFirstName() != null) customer.setFirstName(request.getFirstName());
        if (request.getLastName()  != null) customer.setLastName(request.getLastName());
        if (request.getPhone()     != null) customer.setPhone(request.getPhone());
        if (request.getNotes()     != null) customer.setNotes(request.getNotes());

        Customer saved = customerRepository.save(customer);
        log.info("Customer updated: id={}", customerId);
        return customerMapper.toResponse(saved);
    }

    // ─── Helpers ───────────────────────────────────────────────────────────

    private UUID requireTenantId() {
        UUID tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new ResourceNotFoundException("Tenant context not set");
        }
        return tenantId;
    }
}
