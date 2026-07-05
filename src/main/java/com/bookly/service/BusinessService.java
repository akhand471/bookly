package com.bookly.service;

import com.bookly.dto.CancellationPolicyResponse;
import com.bookly.entity.Business;
import com.bookly.exception.ResourceNotFoundException;
import com.bookly.repository.BusinessRepository;
import com.bookly.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Business-level settings service.
 * Provides subdomain-based lookup (used by the public booking flow) and
 * management of business-level configuration like the cancellation policy.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BusinessService {

    private final BusinessRepository businessRepository;

    /**
     * Finds a business by its unique subdomain identifier.
     * Used by the public booking flow to route customer traffic to the correct tenant.
     *
     * @param subdomain the business subdomain (e.g. "barber" in barber.bookly.com)
     * @return the matching {@link Business}
     * @throws ResourceNotFoundException if no active business has that subdomain
     */
    @Transactional(readOnly = true)
    public Business getBusinessBySubdomain(String subdomain) {
        return businessRepository.findBySubdomain(subdomain)
                .filter(Business::isActive)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No active business found for subdomain: " + subdomain));
    }

    /**
     * Returns the cancellation policy for the current tenant.
     */
    @Transactional(readOnly = true)
    public CancellationPolicyResponse getCancellationPolicy() {
        Business business = getOrThrowCurrentBusiness();
        return CancellationPolicyResponse.builder()
                .cancellationNoticeHours(business.getCancellationNoticeHours())
                .build();
    }

    /**
     * Updates the minimum cancellation notice period for the current tenant.
     *
     * @param noticeHours new minimum notice in hours (0 = no restriction, max 720)
     * @return updated policy
     */
    @Transactional
    public CancellationPolicyResponse updateCancellationPolicy(int noticeHours) {
        Business business = getOrThrowCurrentBusiness();
        business.setCancellationNoticeHours(noticeHours);
        businessRepository.save(business);
        log.info("Cancellation policy updated: businessId={}, noticeHours={}",
                business.getId(), noticeHours);
        return CancellationPolicyResponse.builder()
                .cancellationNoticeHours(noticeHours)
                .build();
    }

    // ─── Helpers ───────────────────────────────────────────────────────────

    private Business getOrThrowCurrentBusiness() {
        UUID businessId = TenantContext.getCurrentTenant();
        if (businessId == null) {
            throw new ResourceNotFoundException("Tenant context not set");
        }
        return businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found: " + businessId));
    }
}
