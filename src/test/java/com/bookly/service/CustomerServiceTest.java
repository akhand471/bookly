package com.bookly.service;

import com.bookly.dto.CustomerResponse;
import com.bookly.dto.CustomerUpdateRequest;
import com.bookly.dto.PageResponse;
import com.bookly.entity.Business;
import com.bookly.entity.Customer;
import com.bookly.exception.ResourceNotFoundException;
import com.bookly.mapper.CustomerMapper;
import com.bookly.repository.CustomerRepository;
import com.bookly.repository.AppointmentRepository;
import com.bookly.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock private CustomerRepository customerRepository;
    @Mock private AppointmentRepository appointmentRepository;
    @Mock private CustomerMapper customerMapper;

    @InjectMocks
    private CustomerService customerService;

    private UUID businessId;
    private UUID customerId;
    private Business business;
    private Customer customer;
    private CustomerResponse sampleResponse;

    @BeforeEach
    void setUp() {
        businessId = UUID.randomUUID();
        customerId = UUID.randomUUID();

        business = Business.builder()
                .id(businessId)
                .name("Test Salon")
                .subdomain("test-salon")
                .build();

        customer = Customer.builder()
                .id(customerId)
                .business(business)
                .firstName("Alex")
                .lastName("Smith")
                .email("alex@example.com")
                .phone("+1-555-0100")
                .build();

        sampleResponse = CustomerResponse.builder()
                .id(customerId)
                .businessId(businessId)
                .firstName("Alex")
                .lastName("Smith")
                .email("alex@example.com")
                .build();

        TenantContext.setCurrentTenant(businessId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ─── findOrCreateGuest ─────────────────────────────────────────────────

    @Test
    void findOrCreateGuest_newCustomer_savesAndReturns() {
        when(customerRepository.findByEmailAndBusiness_Id("alex@example.com", businessId))
                .thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenReturn(customer);

        Customer result = customerService.findOrCreateGuest(
                business, "Alex", "Smith", "alex@example.com", "+1-555-0100");

        assertThat(result.getEmail()).isEqualTo("alex@example.com");
        verify(customerRepository).save(any(Customer.class));
    }

    @Test
    void findOrCreateGuest_existingCustomer_doesNotSave() {
        when(customerRepository.findByEmailAndBusiness_Id("alex@example.com", businessId))
                .thenReturn(Optional.of(customer));

        Customer result = customerService.findOrCreateGuest(
                business, "Alex", "Smith", "alex@example.com", null);

        assertThat(result.getId()).isEqualTo(customerId);
        verify(customerRepository, never()).save(any());
    }

    // ─── getCustomerById ───────────────────────────────────────────────────

    @Test
    void getCustomerById_found_returnsResponse() {
        when(customerRepository.findByIdAndBusiness_Id(customerId, businessId))
                .thenReturn(Optional.of(customer));
        when(customerMapper.toResponse(customer)).thenReturn(sampleResponse);

        CustomerResponse result = customerService.getCustomerById(customerId);

        assertThat(result.getId()).isEqualTo(customerId);
    }

    @Test
    void getCustomerById_notFound_throws404() {
        when(customerRepository.findByIdAndBusiness_Id(customerId, businessId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.getCustomerById(customerId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(customerId.toString());
    }

    // ─── listCustomers ─────────────────────────────────────────────────────

    @Test
    void listCustomers_returnsPaginatedResults() {
        var pageable = PageRequest.of(0, 20);
        var page = new PageImpl<>(List.of(customer), pageable, 1);
        when(customerRepository.findAllByBusiness_IdAndIsActiveTrue(businessId, pageable))
                .thenReturn(page);
        when(customerMapper.toResponse(customer)).thenReturn(sampleResponse);

        PageResponse<CustomerResponse> result = customerService.listCustomers(pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    // ─── updateCustomer ────────────────────────────────────────────────────

    @Test
    void updateCustomer_updatesNonNullFields() {
        CustomerUpdateRequest request = CustomerUpdateRequest.builder()
                .firstName("Alexandra")
                .phone("+1-555-9999")
                .build();
        when(customerRepository.findByIdAndBusiness_Id(customerId, businessId))
                .thenReturn(Optional.of(customer));
        when(customerRepository.save(customer)).thenReturn(customer);
        when(customerMapper.toResponse(customer)).thenReturn(sampleResponse);

        customerService.updateCustomer(customerId, request);

        assertThat(customer.getFirstName()).isEqualTo("Alexandra");
        assertThat(customer.getPhone()).isEqualTo("+1-555-9999");
        assertThat(customer.getLastName()).isEqualTo("Smith"); // unchanged
    }

    @Test
    void updateCustomer_notFound_throws404() {
        when(customerRepository.findByIdAndBusiness_Id(customerId, businessId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.updateCustomer(customerId, new CustomerUpdateRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
