package com.bookly.mapper;

import com.bookly.dto.AppointmentResponse;
import com.bookly.entity.Appointment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper for {@link Appointment} entity ↔ DTOs.
 * Flattens nested associations to flat DTO fields.
 */
@Mapper(componentModel = "spring")
public interface AppointmentMapper {

    @Mapping(target = "businessId",   source = "business.id")
    @Mapping(target = "serviceId",    source = "service.id")
    @Mapping(target = "serviceName",  source = "service.name")
    @Mapping(target = "staffId",      source = "staff.id")
    @Mapping(target = "staffName",    expression = "java(entity.getStaff().getFirstName() + \" \" + entity.getStaff().getLastName())")
    @Mapping(target = "customerId",   source = "customer.id")
    @Mapping(target = "customerName", expression = "java(entity.getCustomer().getFirstName() + \" \" + entity.getCustomer().getLastName())")
    @Mapping(target = "status",       expression = "java(entity.getStatus().name())")
    AppointmentResponse toResponse(Appointment entity);
}
