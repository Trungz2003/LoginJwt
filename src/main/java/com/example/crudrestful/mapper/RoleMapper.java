package com.example.crudrestful.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.example.crudrestful.dto.request.RoleRequest;
import com.example.crudrestful.dto.response.RoleResponse;
import com.example.crudrestful.entity.Role;

@Mapper(componentModel = "spring")
public interface RoleMapper {
    @Mapping(target = "permissions", ignore = true)
    Role toRole(RoleRequest request);

    RoleResponse toRoleResponse(Role role);
}
