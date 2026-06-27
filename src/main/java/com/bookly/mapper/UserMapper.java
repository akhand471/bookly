package com.bookly.mapper;

import com.bookly.dto.UserResponse;
import com.bookly.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "businessId", source = "business.id")
    UserResponse toResponse(User user);
}
