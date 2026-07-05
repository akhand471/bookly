package com.bookly.mapper;

import com.bookly.dto.CustomerResponse;
import com.bookly.entity.Customer;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper for {@link Customer} entity ↔ DTOs.
 */
@Mapper(componentModel = "spring")
public interface CustomerMapper {

    @Mapping(target = "businessId", source = "business.id")
    CustomerResponse toResponse(Customer entity);
}
