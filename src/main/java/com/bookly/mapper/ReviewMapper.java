package com.bookly.mapper;

import com.bookly.dto.PublicReviewResponse;
import com.bookly.dto.ReviewResponse;
import com.bookly.entity.Review;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface ReviewMapper {

    @Mapping(target = "appointmentId", source = "appointment.id")
    @Mapping(target = "serviceId", source = "service.id")
    @Mapping(target = "serviceName", source = "service.name")
    @Mapping(target = "staffId", source = "staff.id")
    @Mapping(target = "staffName", expression = "java(entity.getStaff().getFirstName() + \" \" + entity.getStaff().getLastName())")
    @Mapping(target = "customerName", expression = "java(entity.getCustomer().getFirstName() + \" \" + entity.getCustomer().getLastName())")
    ReviewResponse toResponse(Review entity);

    @Mapping(target = "customerName", source = "entity", qualifiedByName = "maskCustomerName")
    PublicReviewResponse toPublicResponse(Review entity);

    @Named("maskCustomerName")
    default String maskCustomerName(Review entity) {
        if (entity.getCustomer() == null) {
            return "Anonymous";
        }
        String first = entity.getCustomer().getFirstName();
        String last = entity.getCustomer().getLastName();
        if (first == null) first = "";
        if (last == null || last.isEmpty()) {
            return first;
        }
        return first + " " + last.substring(0, 1) + ".";
    }
}
