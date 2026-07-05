package com.bookly.mapper;

import com.bookly.dto.ServiceRequest;
import com.bookly.dto.ServiceResponse;
import com.bookly.entity.BookableService;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * MapStruct mapper for {@link BookableService} entity ↔ DTOs.
 * Follows the same pattern as {@link UserMapper}:
 * {@code componentModel = "spring"} makes this a Spring bean.
 */
@Mapper(componentModel = "spring")
public interface ServiceMapper {

    /** Entity → response DTO. Flattens {@code business.id} to {@code businessId}. */
    @Mapping(target = "businessId", source = "business.id")
    @Mapping(target = "isActive", source = "active")
    ServiceResponse toResponse(BookableService entity);

    /**
     * Request DTO → new entity. The {@code business} association and audit
     * timestamps are set by {@link com.bookly.service.BookableServiceService}.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "business", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "isActive", source = "active")
    BookableService toEntity(ServiceRequest request);

    /**
     * Applies non-null fields from a {@link ServiceRequest} onto an existing entity.
     * Used for PATCH-style updates where only changed fields are provided.
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "business", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(ServiceRequest request, @MappingTarget BookableService entity);
}
