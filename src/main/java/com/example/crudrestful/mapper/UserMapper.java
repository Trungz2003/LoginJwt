package com.example.crudrestful.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import com.example.crudrestful.dto.request.UserCreationRequest;
import com.example.crudrestful.dto.request.UserUpdateRequest;
import com.example.crudrestful.dto.response.UserResponse;
import com.example.crudrestful.entity.User;

@Mapper(componentModel = "spring")
public interface UserMapper {
    User toUser(UserCreationRequest request);

    UserResponse toUserResponse(User user);

    @Mapping(target = "roles", ignore = true)
    void updateUser(@MappingTarget User user, UserUpdateRequest request);
}
