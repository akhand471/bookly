package com.scheduly.mapper;

import com.scheduly.dto.UserResponse;
import com.scheduly.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "businessId", source = "business.id")
    UserResponse toResponse(User user);
}
